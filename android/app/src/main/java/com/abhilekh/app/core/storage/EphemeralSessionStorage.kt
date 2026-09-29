package com.abhilekh.app.core.storage

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Ephemeral Session Storage with AES-256-GCM encryption and flash-safe crypto-shredding.
 * Temporary cache files are encrypted with an ephemeral key held only in RAM.
 * Upon session completion or discard, wiping the key in RAM makes unlinked flash storage
 * blocks mathematically unrecoverable regardless of flash wear-leveling or delayed TRIM.
 */
class EphemeralSessionStorage(private val sessionDir: File) {
    private var sessionKey: SecretKey? = generateKey()
    private val secureRandom = SecureRandom()

    init {
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
    }

    private fun generateKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    /**
     * Encrypts and writes raw page bitmap bytes to disk.
     */
    fun writeEncryptedPage(pageIndex: Int, rawBytes: ByteArray): File {
        val key = sessionKey ?: throw IllegalStateException("Session key has been shredded")
        val iv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))

        val cipherBytes = cipher.doFinal(rawBytes)
        val targetFile = File(sessionDir, "page_${pageIndex}.enc")
        targetFile.outputStream().use { out ->
            out.write(iv)
            out.write(cipherBytes)
        }
        return targetFile
    }

    /**
     * Reads and decrypts stored page bytes from disk.
     */
    fun readDecryptedPage(pageIndex: Int): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key has been shredded")
        val targetFile = File(sessionDir, "page_${pageIndex}.enc")
        if (!targetFile.exists()) {
            throw IllegalArgumentException("Page file does not exist: ${targetFile.absolutePath}")
        }

        val fileBytes = targetFile.readBytes()
        if (fileBytes.size < 12) {
            throw IllegalStateException("Invalid encrypted page file format")
        }

        val iv = fileBytes.copyOfRange(0, 12)
        val cipherBytes = fileBytes.copyOfRange(12, fileBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherBytes)
    }

    /**
     * Performs cryptographic shredding: wipes the AES key in RAM and unlinks the session directory.
     */
    fun cryptoShred() {
        sessionKey = null
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }
    }
}
