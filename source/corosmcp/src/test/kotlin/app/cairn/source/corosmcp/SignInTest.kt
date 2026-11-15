package app.cairn.source.corosmcp

import app.cairn.core.datastore.BlobCipher
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

private class TestCipher : BlobCipher {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key); return c.iv + c.doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

private fun rec(created: Long = 1000L, state: String = "ST", forced: Boolean = false) = PendingRecord(
    "cid", Region.EU, Region.EU.host, BounceRedirect.REDIRECT_URI, "VERIFIER", state, created,
    "https://x/authorize?a=b", "https://x/token", "https://x", forced,
)

private fun res(code: String? = null, state: String? = null, error: String? = null) = CallbackParams.Result(code, state, error)

class AuthorizeUrlTest {
    @Test fun redirectUriHasTrailingSlash() = assertEquals("https://manojanasuri16.github.io/cairn/callback/", BounceRedirect.REDIRECT_URI)

    @Test fun builderIncludesPkceStateAndRedirect() {
        val u = OAuthClient.buildAuthorizeUrl("https://h/oauth2/authorize", "cid", BounceRedirect.REDIRECT_URI, "ST", "VER").toHttpUrl()
        assertEquals("code", u.queryParameter("response_type"))
        assertEquals("cid", u.queryParameter("client_id"))
        assertEquals(BounceRedirect.REDIRECT_URI, u.queryParameter("redirect_uri"))
        assertEquals("ST", u.queryParameter("state"))
        assertEquals("S256", u.queryParameter("code_challenge_method"))
        assertEquals(Pkce.challenge("VER"), u.queryParameter("code_challenge"))
        assertEquals(OAuthClient.SCOPE, u.queryParameter("scope"))
    }
}

class CallbackParserTest {
    @Test fun parsesCodeAndState() {
        val p = CallbackParser.parse("app.cairn.oauth://callback?code=a%2Bb&state=xyz") as CallbackParams.Result
        assertEquals("a+b", p.code); assertEquals("xyz", p.state); assertNull(p.error)
    }
    @Test fun parsesError() {
        val p = CallbackParser.parse("app.cairn.oauth://callback?error=access_denied&state=s") as CallbackParams.Result
        assertEquals("access_denied", p.error)
    }
    @Test fun rejectsOtherUris() {
        assertSame(CallbackParams.NotACallback, CallbackParser.parse("https://evil/callback?code=1"))
        assertSame(CallbackParams.NotACallback, CallbackParser.parse("app.cairn.oauth://other?code=1"))
        assertSame(CallbackParams.NotACallback, CallbackParser.parse(null))
    }
    @Test fun toStringDoesNotLeak() = assertFalse(res("SECRETCODE", "SECRETSTATE").toString().contains("SECRET"))
}

class CallbackValidatorTest {
    @Test fun match() { val c = CallbackValidator.check(rec(), res("C", "ST"), 2000L); assertEquals("C", (c as CallbackCheck.Accept).code) }
    @Test fun mismatch() = assertEquals(CallbackCheck.StateMismatch, CallbackValidator.check(rec(), res("C", "NOPE"), 2000L))
    @Test fun missingState() = assertEquals(CallbackCheck.MissingState, CallbackValidator.check(rec(), res("C", null), 2000L))
    @Test fun missingCode() = assertEquals(CallbackCheck.MissingCode, CallbackValidator.check(rec(), res(null, "ST"), 2000L))
    @Test fun noPending() = assertEquals(CallbackCheck.NoPending, CallbackValidator.check(null, res("C", "ST"), 2000L))
    @Test fun expiredAfter15Min() {
        assertEquals(CallbackCheck.Expired, CallbackValidator.check(rec(1000L), res("C", "ST"), 1000L + BounceRedirect.PENDING_TTL_MS + 1))
        assertTrue(CallbackValidator.check(rec(1000L), res("C", "ST"), 1000L + BounceRedirect.PENDING_TTL_MS) is CallbackCheck.Accept)
    }
    @Test fun errorMapsToWrongRegionOnlyForClearCodes() {
        val wrong = CallbackValidator.check(rec(), res(null, "ST", "account_not_found"), 2000L) as CallbackCheck.ProviderError
        assertTrue(wrong.wrongRegion)
        val denied = CallbackValidator.check(rec(), res(null, "ST", "access_denied"), 2000L) as CallbackCheck.ProviderError
        assertFalse(denied.wrongRegion)
        assertEquals(FailureReason.REGION_MISMATCH, FailureClassifier.fromCheck(wrong))
        assertEquals(FailureReason.REFUSED, FailureClassifier.fromCheck(denied))
    }
    @Test fun errorWithBadStateIsStillMismatch() =
        assertEquals(CallbackCheck.StateMismatch, CallbackValidator.check(rec(), res(null, "BAD", "access_denied"), 2000L))
}

class PendingStoreTest {
    private fun tmp() = File.createTempFile("pend", ".bin").also { it.delete(); it.deleteOnExit() }

    @Test fun roundTripEncryptedAndClear() {
        val f = tmp(); val s = EncryptedFilePendingStore(f, TestCipher())
        assertNull(s.load())
        s.save(rec())
        assertEquals(rec(), s.load())
        assertFalse(f.readBytes().toString(Charsets.ISO_8859_1).contains("VERIFIER"))
        s.clear(); assertNull(s.load())
    }
    @Test fun tamperedReadsAsAbsent() {
        val f = tmp(); val s = EncryptedFilePendingStore(f, TestCipher()); s.save(rec())
        val b = f.readBytes(); b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte(); f.writeBytes(b)
        assertNull(s.load())
    }
    @Test fun expiry() {
        assertFalse(rec(0).expired(BounceRedirect.PENDING_TTL_MS))
        assertTrue(rec(0).expired(BounceRedirect.PENDING_TTL_MS + 1))
    }
    @Test fun toStringRedacted() = assertFalse(rec().toString().contains("VERIFIER"))
}

class RegionDecisionTest {
    @Test fun orderAndFallback() {
        assertEquals(Region.US, RegionPolicy.start(null)); assertEquals(Region.CN, RegionPolicy.start(Region.CN))
        assertEquals(Region.EU, RegionPolicy.next(Region.US)); assertNull(RegionPolicy.next(Region.CN))
    }
    @Test fun classify() {
        assertEquals(FailureReason.NETWORK, FailureClassifier.classify(CorosNetworkException("x")))
        assertEquals(FailureReason.REGION_MISMATCH, FailureClassifier.classify(AuthorizationException("user_not_found")))
        assertEquals(FailureReason.REFUSED, FailureClassifier.classify(AuthorizationException("access_denied")))
        assertEquals(FailureReason.TIMEOUT, FailureClassifier.classify(SignInTimeoutException()))
        assertEquals(FailureReason.REFUSED, FailureClassifier.classify(CorosException("Token request failed (HTTP 400, invalid_request)")))
    }
}

class SignInMachineTest {
    private val m = SignInMachine
    private fun waiting(at: Long = 0L) = SignInState.WaitingForBrowser(SignInMethod.BOUNCE, Region.US, at)

    @Test fun happyPath() {
        var s: SignInState = SignInState.Idle
        s = m.reduce(s, SignInEvent.Start(SignInMethod.BOUNCE, Region.US)); assertTrue(s is SignInState.Starting)
        s = m.reduce(s, SignInEvent.BrowserLaunched(10)); assertEquals(waiting(10), s)
        s = m.reduce(s, SignInEvent.ExchangeStarted(Region.US)); assertEquals(SignInState.Exchanging(Region.US), s)
        s = m.reduce(s, SignInEvent.Succeeded); assertEquals(SignInState.SignedIn(Region.US), s)
    }
    @Test fun cancelFromWaitingGoesIdle() = assertEquals(SignInState.Idle, m.reduce(waiting(), SignInEvent.Cancel))
    @Test fun restartWhileWaitingReplacesAttempt() {
        val s = m.reduce(waiting(), SignInEvent.Start(SignInMethod.LOCAL, Region.EU))
        assertEquals(SignInState.Starting(SignInMethod.LOCAL, Region.EU), s)
    }
    @Test fun restartFromFailed() = assertTrue(m.reduce(SignInState.Failed(FailureReason.NETWORK, true), SignInEvent.Start(SignInMethod.BOUNCE, Region.US)) is SignInState.Starting)
    @Test fun startIgnoredDuringExchange() {
        val x = SignInState.Exchanging(Region.US)
        assertEquals(x, m.reduce(x, SignInEvent.Start(SignInMethod.BOUNCE, Region.US)))
    }
    @Test fun watchdogTimesOutAfterFiveMinutes() {
        assertEquals(waiting(0), m.reduce(waiting(0), SignInEvent.Tick(SignInMachine.WATCHDOG_MS - 1)))
        val f = m.reduce(waiting(0), SignInEvent.Tick(SignInMachine.WATCHDOG_MS)) as SignInState.Failed
        assertEquals(FailureReason.TIMEOUT, f.reason); assertTrue(f.canRetry)
    }
    @Test fun failureKeepsRegionAndCanRetry() {
        val f = m.reduce(waiting(), SignInEvent.Failure(FailureReason.NETWORK)) as SignInState.Failed
        assertEquals(Region.US, f.region); assertTrue(f.canRetry)
    }
    @Test fun failureDoesNotUndoSignedIn() = assertEquals(SignInState.SignedIn(Region.US), m.reduce(SignInState.SignedIn(Region.US), SignInEvent.Failure(FailureReason.OTHER)))
    @Test fun signedOutResets() = assertEquals(SignInState.Idle, m.reduce(SignInState.SignedIn(Region.US), SignInEvent.SignedOut))

    @Test fun buttonsStayEnabledWhileWaiting() {
        listOf(SignInState.Idle, SignInState.Starting(SignInMethod.BOUNCE, Region.US), waiting(), SignInState.Failed(FailureReason.OTHER, true), SignInState.SignedIn(Region.US))
            .forEach { assertTrue("$it", m.startEnabled(it)) }
        assertFalse(m.startEnabled(SignInState.Exchanging(Region.US)))
        assertTrue(m.cancelVisible(waiting()))
        assertFalse(m.cancelVisible(SignInState.Idle))
    }
    @Test fun resumeHintAfter20sOnly() {
        assertFalse(m.showResumeHint(waiting(0), null, 100_000))
        assertFalse(m.showResumeHint(waiting(0), 1000, 1000 + 19_999))
        assertTrue(m.showResumeHint(waiting(0), 1000, 1000 + 20_000))
        assertFalse(m.showResumeHint(SignInState.Idle, 1000, 99_000))
        // Does not cancel anything:
        assertEquals(waiting(0), m.reduce(waiting(0), SignInEvent.Tick(30_000)))
    }
    @Test fun restoreFromPersistedFacts() {
        assertEquals(SignInState.SignedIn(Region.EU), m.restore(true, Region.EU, null, 0))
        assertTrue(m.restore(false, Region.US, rec(1000), 2000) is SignInState.WaitingForBrowser)
        assertEquals(SignInState.Idle, m.restore(false, Region.US, rec(0), BounceRedirect.PENDING_TTL_MS + 1))
        assertEquals(SignInState.Idle, m.restore(false, Region.US, null, 0))
    }
}

class ExchangeRequestTest {
    @Test fun tokenBodyUsesPersistedVerifierAndRedirect() = runBlocking<Unit> {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setBody("""{"access_token":"AT","refresh_token":"RT","expires_in":3600}"""))
            val store = MemoryStore()
            val client = OAuthClient(OkHttpClient(), store)
            val r = rec().copy(tokenEndpoint = server.url("/oauth2/token").toString(), issuer = "https://issuer")
            client.exchangeCode(r, "THE_CODE")
            val body = server.takeRequest().body.readUtf8()
            val form = body.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            assertEquals("authorization_code", form["grant_type"])
            assertEquals("THE_CODE", form["code"])
            assertEquals("VERIFIER", form["code_verifier"])
            assertEquals(BounceRedirect.REDIRECT_URI, form["redirect_uri"])
            assertEquals("cid", form["client_id"])
            assertEquals("AT", store.load()!!.accessToken)
        } finally { server.shutdown() }
    }
}
