package app.cairn.source.corosmcp

import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoopbackReceiverTest {
    @Test fun defaultTimeoutIsAFewMinutes() {
        assertEquals(180_000, LoopbackReceiver.DEFAULT_TIMEOUT_MS)
    }

    @Test fun timeoutThrowsClearRetryableError() = runBlocking {
        LoopbackReceiver.open().use { rx ->
            try {
                rx.awaitCode("s", timeoutMs = 150)
                fail("expected timeout")
            } catch (e: SignInTimeoutException) {
                assertTrue(e.message!!.contains("try again"))
                assertEquals(SignInMessages.TIMEOUT, e.message)
                // Timeout must not trigger region fallback.
                assertFalse(RegionPolicy.isWrongRegion(e))
            }
        }
    }

    @Test fun returnsCodeWhenStateMatches() = runBlocking {
        LoopbackReceiver.open().use { rx ->
            val code = async(Dispatchers.IO) { rx.awaitCode("abc", timeoutMs = 5_000) }
            (URL(rx.redirectUri + "?code=XYZ&state=abc").openConnection() as java.net.HttpURLConnection).run {
                connectTimeout = 3000; readTimeout = 3000; inputStream.use { it.readBytes() }
            }
            assertEquals("XYZ", code.await())
        }
    }

    @Test fun hintMentionsMinutes() {
        assertTrue(SignInMessages.HINT.contains("couple of minutes"))
    }
}
