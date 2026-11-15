package app.cairn.source.corosmcp

import app.cairn.core.datastore.StoredTokens
import app.cairn.core.datastore.TokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryStore : TokenStore {
    var t: StoredTokens? = null
    override fun load() = t
    override fun save(tokens: StoredTokens) { t = tokens }
    override fun clear() { t = null }
}

class PkceTest {
    @Test fun rfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun verifierShape() {
        val v = Pkce.newVerifier()
        assertTrue(v.length in 43..128); assertTrue(v.matches(Regex("[A-Za-z0-9_-]+")))
    }
}

class OAuthClientTest {
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val store = MemoryStore()
    private var now = 1_000_000L
    private lateinit var client: OAuthClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
        client = OAuthClient(OkHttpClient(), store, clock = { now })
    }
    @After fun tearDown() = server.shutdown()

    private fun metaJson(device: Boolean = true) = """{"issuer":"$base","authorization_endpoint":"$base/oauth2/authorize",
      "token_endpoint":"$base/oauth2/token","registration_endpoint":"$base/connect/register","revocation_endpoint":"$base/oauth2/revoke",
      ${if (device) "\"device_authorization_endpoint\":\"$base/oauth2/device_authorization\"," else ""}
      "grant_types_supported":["authorization_code","refresh_token","urn:ietf:params:oauth:grant-type:device_code"],
      "code_challenge_methods_supported":["S256"],"scopes_supported":["openid","mcp.tools","offline_access"]}"""

    private fun seed(expiresAt: Long = now + 10_000_000) {
        store.save(StoredTokens("cid", base, "AT1", expiresAt, "RT1"))
    }

    @Test fun dcrRequestBody() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"client_id":"abc"}"""))
        val meta = Json { ignoreUnknownKeys = true }.decodeFromString(ServerMetadata.serializer(), metaJson())
        val id = client.register(meta, "http://127.0.0.1:5555/callback")
        assertEquals("abc", id)
        val req = server.takeRequest()
        assertEquals("/connect/register", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("none", body["token_endpoint_auth_method"]!!.jsonPrimitive.content)
        assertEquals("openid offline_access mcp.tools", body["scope"]!!.jsonPrimitive.content)
        assertEquals("http://127.0.0.1:5555/callback", body["redirect_uris"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals(listOf("authorization_code", "refresh_token"), body["grant_types"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun completeAuthorizationStoresTokens() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody("""{"access_token":"A","refresh_token":"R","expires_in":100}"""))
        val meta = Json { ignoreUnknownKeys = true }.decodeFromString(ServerMetadata.serializer(), metaJson())
        val p = PendingAuthorization("u", "cid", "http://127.0.0.1:1/callback", "st", "verifier", meta)
        client.completeAuthorization(p, "code123")
        val form = server.takeRequest().body.readUtf8()
        assertTrue(form.contains("grant_type=authorization_code")); assertTrue(form.contains("code_verifier=verifier"))
        val t = store.load()!!
        assertEquals("A", t.accessToken); assertEquals("R", t.refreshToken); assertEquals(now + 100_000, t.accessExpiresAtMs)
    }

    @Test fun refreshRotatesTokens() = runBlocking<Unit> {
        seed(expiresAt = now) // expired
        server.enqueue(MockResponse().setBody(metaJson()))
        server.enqueue(MockResponse().setBody("""{"access_token":"AT2","refresh_token":"RT2","expires_in":3600}"""))
        assertEquals("AT2", client.accessToken())
        server.takeRequest()
        val form = server.takeRequest().body.readUtf8()
        assertTrue(form.contains("grant_type=refresh_token")); assertTrue(form.contains("refresh_token=RT1"))
        assertEquals("RT2", store.load()!!.refreshToken)
    }

    @Test fun refreshKeepsOldRefreshWhenNotRotated() = runBlocking<Unit> {
        seed()
        server.enqueue(MockResponse().setBody(metaJson()))
        server.enqueue(MockResponse().setBody("""{"access_token":"AT2","expires_in":3600}"""))
        assertTrue(client.refresh())
        assertEquals("RT1", store.load()!!.refreshToken)
    }

    @Test fun signOutRevokesAndClears() = runBlocking<Unit> {
        seed()
        server.enqueue(MockResponse().setBody(metaJson()))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        client.signOut()
        assertNull(store.load())
        server.takeRequest()
        assertEquals("/oauth2/revoke", server.takeRequest().path)
    }

    @Test fun deviceFlowPolling() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"A","refresh_token":"R","expires_in":100}"""))
        val meta = Json { ignoreUnknownKeys = true }.decodeFromString(ServerMetadata.serializer(), metaJson())
        val flow = client.DeviceFlow("cid", meta, DeviceAuthResponse("dc", "UC", "$base/v", null, 600, 1))
        flow.awaitApproval(sleep = { now += it })
        assertEquals("A", store.load()!!.accessToken)
    }

    @Test fun deviceMissingIsReported() {
        val meta = Json { ignoreUnknownKeys = true }.decodeFromString(ServerMetadata.serializer(), metaJson(device = false))
        assertFalse(meta.supportsDeviceCode)
    }
}
