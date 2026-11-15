package app.cairn.source.corosmcp

import app.cairn.core.datastore.StoredTokens
import app.cairn.core.datastore.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Hand-rolled OAuth 2.0 public-client flows (DCR, PKCE S256, device code, refresh, revoke). Never logs secrets. */
class OAuthClient(
    private val http: OkHttpClient,
    private val store: TokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val clientName: String = "Cairn",
) : TokenProvider {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshLock = Mutex()

    companion object {
        const val SCOPE = "openid offline_access mcp.tools"
        private val JSON = "application/json".toMediaType()
        private const val SKEW_MS = 60_000L
    }

    suspend fun fetchMetadata(region: Region): ServerMetadata {
        val url = region.host + "/.well-known/oauth-authorization-server"
        val body = call(Request.Builder().url(url).get().build())
        val meta = json.decodeFromString(ServerMetadata.serializer(), body)
        if ("S256" !in meta.codeChallengeMethods) throw CorosException("Server does not advertise PKCE S256")
        return meta
    }

    suspend fun register(meta: ServerMetadata, redirectUri: String, deviceFlow: Boolean = false): String {
        val endpoint = meta.registrationEndpoint ?: throw CorosException("Server has no dynamic registration endpoint")
        val grants = buildList {
            if (deviceFlow) add(ServerMetadata.DEVICE_GRANT) else add("authorization_code")
            add("refresh_token")
        }
        val req = RegistrationRequest(
            clientName = clientName,
            redirectUris = listOf(redirectUri),
            grantTypes = grants,
            responseTypes = if (deviceFlow) emptyList() else listOf("code"),
            tokenEndpointAuthMethod = "none",
            scope = SCOPE,
        )
        val body = call(
            Request.Builder().url(endpoint)
                .post(json.encodeToString(req).toRequestBody(JSON)).header("Accept", "application/json").build(),
        )
        return json.decodeFromString(RegistrationResponse.serializer(), body).clientId
    }

    /** Registers a client for [redirectUri] and builds the authorize URL (PKCE S256). */
    suspend fun beginAuthorization(region: Region, redirectUri: String): PendingAuthorization {
        val meta = fetchMetadata(region)
        val clientId = register(meta, redirectUri)
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        val url = meta.authorizationEndpoint.toHttpUrl().newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("scope", SCOPE)
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", Pkce.challenge(verifier))
            .addQueryParameter("code_challenge_method", "S256")
            .build().toString()
        return PendingAuthorization(url, clientId, redirectUri, state, verifier, meta)
    }

    suspend fun completeAuthorization(p: PendingAuthorization, code: String) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", p.redirectUri)
            .add("client_id", p.clientId)
            .add("code_verifier", p.verifier)
            .build()
        val tr = tokenCall(p.metadata.tokenEndpoint, form)
        persist(p.clientId, p.metadata.issuer, tr, previousRefresh = null)
    }

    // ---- device code ----

    suspend fun startDeviceFlow(region: Region): Pair<DeviceFlow, DeviceAuthResponse> {
        val meta = fetchMetadata(region)
        if (!meta.supportsDeviceCode) throw CorosException("This server does not support device-code sign-in")
        val clientId = register(meta, "http://127.0.0.1:43123/callback", deviceFlow = true)
        val form = FormBody.Builder().add("client_id", clientId).add("scope", SCOPE).build()
        val body = call(Request.Builder().url(meta.deviceAuthorizationEndpoint!!).post(form).build())
        val resp = json.decodeFromString(DeviceAuthResponse.serializer(), body)
        return DeviceFlow(clientId, meta, resp) to resp
    }

    inner class DeviceFlow(val clientId: String, val meta: ServerMetadata, val resp: DeviceAuthResponse) {
        /** Polls until approved, denied or expired. */
        suspend fun awaitApproval(sleep: suspend (Long) -> Unit = { delay(it) }) {
            var interval = resp.interval.coerceAtLeast(1)
            val deadline = clock() + resp.expiresIn * 1000
            while (clock() < deadline) {
                sleep(interval * 1000)
                val form = FormBody.Builder()
                    .add("grant_type", ServerMetadata.DEVICE_GRANT)
                    .add("device_code", resp.deviceCode)
                    .add("client_id", clientId).build()
                val result = rawPost(meta.tokenEndpoint, form)
                if (result.code in 200..299) {
                    persist(clientId, meta.issuer, json.decodeFromString(TokenResponse.serializer(), result.body), null)
                    return
                }
                when (parseError(result.body)) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    "access_denied" -> throw CorosException("Sign-in was denied")
                    "expired_token" -> throw CorosException("Device code expired")
                    else -> throw CorosException("Device sign-in failed (HTTP ${result.code})")
                }
            }
            throw CorosException("Device code expired")
        }
    }

    // ---- TokenProvider ----

    override suspend fun accessToken(): String? {
        val t = store.load() ?: return null
        if (t.accessExpiresAtMs - SKEW_MS > clock()) return t.accessToken
        return if (refresh()) store.load()?.accessToken else null
    }

    override suspend fun refreshAfter401(staleToken: String?): Boolean = refresh(staleToken)

    /** Refreshes once; concurrent callers share the result. If [staleToken] no longer matches the stored token, another caller already refreshed. */
    suspend fun refresh(staleToken: String? = null): Boolean = refreshLock.withLock {
        val t = store.load() ?: return false
        if (staleToken != null && t.accessToken != staleToken) return true
        val rt = t.refreshToken ?: return false
        val meta = try {
            // Issuer is stored; metadata lives at the same host.
            json.decodeFromString(
                ServerMetadata.serializer(),
                call(Request.Builder().url(t.issuer + "/.well-known/oauth-authorization-server").get().build()),
            )
        } catch (e: CorosException) { return false }
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token").add("refresh_token", rt).add("client_id", t.clientId).build()
        val result = rawPost(meta.tokenEndpoint, form)
        if (result.code !in 200..299) return false
        persist(t.clientId, t.issuer, json.decodeFromString(TokenResponse.serializer(), result.body), rt)
        true
    }

    fun isSignedIn(): Boolean = store.load() != null

    /** Revokes the refresh (and access) token if the server has a revocation endpoint, then wipes local state. */
    suspend fun signOut() {
        val t = store.load()
        try {
            if (t != null) {
                val rev = try {
                    json.decodeFromString(
                        ServerMetadata.serializer(),
                        call(Request.Builder().url(t.issuer + "/.well-known/oauth-authorization-server").get().build()),
                    ).revocationEndpoint
                } catch (e: CorosException) { null }
                if (rev != null) {
                    t.refreshToken?.let { revoke(rev, t.clientId, it, "refresh_token") }
                    revoke(rev, t.clientId, t.accessToken, "access_token")
                }
            }
        } finally {
            store.clear()
        }
    }

    private suspend fun revoke(endpoint: String, clientId: String, token: String, hint: String) {
        val form = FormBody.Builder().add("token", token).add("token_type_hint", hint).add("client_id", clientId).build()
        try { rawPost(endpoint, form) } catch (e: Exception) { /* best effort */ }
    }

    // ---- plumbing ----

    private fun persist(clientId: String, issuer: String, tr: TokenResponse, previousRefresh: String?) {
        store.save(
            StoredTokens(
                clientId = clientId,
                issuer = issuer,
                accessToken = tr.accessToken,
                accessExpiresAtMs = clock() + (tr.expiresIn ?: 3600) * 1000,
                refreshToken = tr.refreshToken ?: previousRefresh,
            ),
        )
    }

    private suspend fun tokenCall(endpoint: String, form: FormBody): TokenResponse {
        val r = rawPost(endpoint, form)
        if (r.code !in 200..299) throw CorosException("Token request failed (HTTP ${r.code}${parseError(r.body).let { if (it.isBlank()) "" else ", $it" }})")
        return json.decodeFromString(TokenResponse.serializer(), r.body)
    }

    private class Raw(val code: Int, val body: String)

    private suspend fun rawPost(url: String, form: FormBody): Raw = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).post(form).header("Accept", "application/json").build()).execute().use {
            Raw(it.code, it.body?.string().orEmpty())
        }
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw CorosException("HTTP ${r.code} from ${req.url.host}")
                b
            }
        } catch (e: java.io.IOException) {
            throw CorosException("Network error: ${e.javaClass.simpleName}")
        }
    }

    private fun parseError(body: String): String =
        try { json.decodeFromString(OAuthError.serializer(), body).error } catch (e: Exception) { "" }
}

interface TokenProvider {
    suspend fun accessToken(): String?
    /** Called after a 401; [staleToken] is the token that was rejected. Returns true if a new token is available. */
    suspend fun refreshAfter401(staleToken: String?): Boolean
}
