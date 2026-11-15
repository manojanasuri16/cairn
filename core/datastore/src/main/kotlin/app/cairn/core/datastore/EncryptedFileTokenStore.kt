package app.cairn.core.datastore

import java.io.File
import kotlinx.serialization.json.Json

/**
 * Stores [StoredTokens] as one encrypted blob in [file]. Place the file under a path that
 * the backup rules exclude (files/datastore/). A corrupt or undecryptable blob reads as "signed out".
 */
class EncryptedFileTokenStore(
    private val file: File,
    private val cipher: BlobCipher,
) : TokenStore {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun load(): StoredTokens? {
        if (!file.exists()) return null
        return try {
            val plain = cipher.decrypt(file.readBytes())
            json.decodeFromString(StoredTokens.serializer(), plain.decodeToString())
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    override fun save(tokens: StoredTokens) {
        file.parentFile?.mkdirs()
        val sealed = cipher.encrypt(json.encodeToString(StoredTokens.serializer(), tokens).encodeToByteArray())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "token store write failed" }
        }
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }
}
