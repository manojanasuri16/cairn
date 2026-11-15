package app.cairn.source.corosmcp

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val rng = SecureRandom()

    fun newVerifier(): String = b64.encodeToString(ByteArray(48).also(rng::nextBytes))

    /** RFC 7636 S256: BASE64URL(SHA256(ASCII(verifier))). */
    fun challenge(verifier: String): String =
        b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun newState(): String = b64.encodeToString(ByteArray(24).also(rng::nextBytes))
}
