package app.cairn.core.datastore

import kotlinx.serialization.Serializable

/** Everything we persist about a COROS sign-in. Never log or print instances of this class. */
@Serializable
data class StoredTokens(
    val clientId: String,
    val issuer: String,
    val accessToken: String,
    val accessExpiresAtMs: Long,
    val refreshToken: String?,
) {
    override fun toString(): String = "StoredTokens(redacted)"
}

interface TokenStore {
    fun load(): StoredTokens?
    fun save(tokens: StoredTokens)
    fun clear()
}

/** Symmetric authenticated encryption of a blob. The Keystore implementation cannot run in JVM tests. */
interface BlobCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}
