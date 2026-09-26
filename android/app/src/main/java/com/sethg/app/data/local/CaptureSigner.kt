package com.sethg.app.data.local

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seals every in-app camera photo at the moment of capture.
 *
 * payload   = sha256(photo) | lotId | capturedAt | nonce | device
 * signature = ECDSA(payload) with a private key that never leaves the
 *             Android Keystore.
 *
 * If a single pixel of the photo is changed later, its SHA-256 no longer
 * matches the signed payload, so the server can reject it.
 */
@Singleton
class CaptureSigner @Inject constructor() {

    companion object {
        private const val KEY_ALIAS = "sethg_capture_key"
        private const val KEYSTORE  = "AndroidKeyStore"
    }

    data class Proof(
        val sha256: String,
        val capturedAt: Long,
        val nonce: String,
        val payload: String,
        val signature: String
    )

    fun seal(photo: File, lotId: String, capturedAt: Long = System.currentTimeMillis()): Proof {
        val hash    = sha256(photo)
        val nonce   = randomNonce()
        val payload = listOf(hash, lotId, capturedAt, nonce, "${Build.MANUFACTURER} ${Build.MODEL}")
            .joinToString("|")
        return Proof(hash, capturedAt, nonce, payload, sign(payload))
    }

    /** Sent to the backend once so it can verify signatures from this device. */
    fun publicKeyBase64(): String {
        privateKey() // make sure the key pair exists
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return Base64.encodeToString(ks.getCertificate(KEY_ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    private fun sign(payload: String): String {
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey())
            update(payload.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(signer.sign(), Base64.NO_WRAP)
    }

    private fun privateKey(): PrivateKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { return it.privateKey }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        return generator.generateKeyPair().private
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(12).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }
}
