package app.cairn.source.corosmcp

import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One-shot HTTP receiver on 127.0.0.1 at an ephemeral port for the OAuth redirect (RFC 8252 section 7.3). */
class LoopbackReceiver private constructor(private val server: ServerSocket) : AutoCloseable {
    val redirectUri: String get() = "http://127.0.0.1:${server.localPort}/callback"

    /** Waits for the redirect; returns the authorization code after verifying [expectedState]. */
    suspend fun awaitCode(expectedState: String, timeoutMs: Int = 300_000): String = withContext(Dispatchers.IO) {
        server.soTimeout = timeoutMs
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) throw CorosException("Timed out waiting for sign-in")
            server.soTimeout = remaining
            try {
                server.accept().use { s ->
                    s.soTimeout = 5000
                    val line = s.getInputStream().bufferedReader().readLine().orEmpty()
                    val params = parseRequestLine(line)
                    val ok = params != null
                    val html = if (ok) "Signed in. You can return to Cairn." else "Not found."
                    val out = "HTTP/1.1 ${if (ok) "200 OK" else "404 Not Found"}\r\nContent-Type: text/plain; charset=utf-8\r\n" +
                        "Content-Length: ${html.length}\r\nConnection: close\r\n\r\n$html"
                    s.getOutputStream().apply { write(out.toByteArray()); flush() }
                    if (params != null) {
                        params["error"]?.let { throw AuthorizationException(it) }
                        if (params["state"] != expectedState) throw CorosException("State mismatch")
                        return@withContext params["code"] ?: throw CorosException("No authorization code returned")
                    }
                }
            } catch (e: SocketTimeoutException) {
                throw CorosException("Timed out waiting for sign-in")
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    override fun close() = server.close()

    companion object {
        fun open(): LoopbackReceiver = LoopbackReceiver(ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")))

        /** Returns query params for "GET /callback?..." else null. */
        internal fun parseRequestLine(line: String): Map<String, String>? {
            val parts = line.split(' ')
            if (parts.size < 2 || parts[0] != "GET" || !(parts[1] == "/callback" || parts[1].startsWith("/callback?"))) return null
            val q = parts[1].substringAfter('?', "")
            return q.split('&').filter { it.isNotEmpty() }.associate {
                val k = it.substringBefore('=')
                val v = it.substringAfter('=', "")
                URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
            }
        }
    }
}
