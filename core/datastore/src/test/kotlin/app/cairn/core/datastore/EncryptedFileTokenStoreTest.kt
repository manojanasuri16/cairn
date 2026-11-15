package app.cairn.core.datastore

import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake cipher: real AES-GCM with an in-memory key, same blob layout as the Keystore one. */
private class FakeCipher : BlobCipher {
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return c.iv + c.doFinal(plain)
    }
    override fun decrypt(sealed: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12))
        return c.doFinal(sealed, 12, sealed.size - 12)
    }
}

class EncryptedFileTokenStoreTest {
    private val tokens = StoredTokens("cid", "https://issuer.example", "ACCESS_SECRET", 123L, "REFRESH_SECRET")

    private fun tmp(): File = File.createTempFile("tok", ".bin").also { it.delete(); it.deleteOnExit() }

    @Test fun roundtrip() {
        val f = tmp()
        val s = EncryptedFileTokenStore(f, FakeCipher())
        assertNull(s.load())
        s.save(tokens)
        assertEquals(tokens, s.load())
    }

    @Test fun fileDoesNotContainPlaintext() {
        val f = tmp()
        EncryptedFileTokenStore(f, FakeCipher()).save(tokens)
        val raw = f.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("ACCESS_SECRET"))
        assertFalse(raw.contains("REFRESH_SECRET"))
    }

    @Test fun tamperedBlobReadsAsSignedOut() {
        val f = tmp()
        val s = EncryptedFileTokenStore(f, FakeCipher())
        s.save(tokens)
        val b = f.readBytes(); b[b.size - 1] = (b[b.size - 1].toInt() xor 1).toByte(); f.writeBytes(b)
        assertNull(s.load())
    }

    @Test fun clearRemoves() {
        val f = tmp()
        val s = EncryptedFileTokenStore(f, FakeCipher())
        s.save(tokens); s.clear()
        assertNull(s.load()); assertTrue(!f.exists())
    }

    @Test fun toStringIsRedacted() {
        assertFalse(tokens.toString().contains("SECRET"))
    }
}
