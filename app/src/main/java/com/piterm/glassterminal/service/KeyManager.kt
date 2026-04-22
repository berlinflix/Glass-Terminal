package com.piterm.glassterminal.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.KeyPairGenerator
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.io.File
import java.security.KeyPairGenerator as JcaKeyPairGenerator
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages Ed25519 SSH key pair generation and secure storage.
 *
 * Architecture:
 * 1. Ed25519 key pair generated via net.i2p.crypto.eddsa (BouncyCastle-backed)
 * 2. An AES-256-GCM key is generated in the Android Keystore (hardware-backed)
 * 3. The Ed25519 private key is encrypted with the AES key and stored in app-private files
 * 4. The public key is stored in OpenSSH format for easy copying to the Pi
 *
 * This gives us Ed25519 support on Android 8+ while keeping the private key
 * protected by hardware-backed encryption.
 */
class KeyManager(private val context: Context) {

    companion object {
        private const val TAG = "KeyManager"
        private const val KEYSTORE_ALIAS = "glassterminal_aes_wrapper"
        private const val PRIVATE_KEY_FILE = "id_ed25519.enc"
        private const val PUBLIC_KEY_FILE = "id_ed25519.pub"
        private const val IV_FILE = "id_ed25519.iv"
        private const val GCM_TAG_LENGTH = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    private val keyDir: File = File(context.filesDir, "ssh_keys").apply { mkdirs() }

    // ── Public API ──────────────────────────────────────────────────────────

    /** Returns true if an SSH key pair already exists on this device. */
    fun hasKeyPair(): Boolean {
        return File(keyDir, PRIVATE_KEY_FILE).exists() &&
               File(keyDir, PUBLIC_KEY_FILE).exists()
    }

    /**
     * Generates a new Ed25519 key pair, encrypts the private key with
     * an Android Keystore AES key, and persists both.
     * Returns the public key in OpenSSH format.
     */
    fun generateKeyPair(): String {
        Log.i(TAG, "Generating new Ed25519 key pair…")

        // 1. Generate Ed25519 key pair
        val kpg = KeyPairGenerator()
        val keyPair = kpg.generateKeyPair()
        val privateKey = keyPair.private as EdDSAPrivateKey
        val publicKey = keyPair.public as EdDSAPublicKey

        // 2. Get or create the AES wrapper key in Android Keystore
        val aesKey = getOrCreateAesKey()

        // 3. Encrypt the Ed25519 private key bytes
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        val encryptedPrivateKey = cipher.doFinal(privateKey.seed)

        // 4. Persist encrypted private key, IV, and public key
        File(keyDir, PRIVATE_KEY_FILE).writeBytes(encryptedPrivateKey)
        File(keyDir, IV_FILE).writeBytes(iv)

        val publicKeyOpenSsh = formatOpenSshPublicKey(publicKey)
        File(keyDir, PUBLIC_KEY_FILE).writeText(publicKeyOpenSsh)

        Log.i(TAG, "Key pair generated and stored securely.")
        return publicKeyOpenSsh
    }

    /**
     * Loads and decrypts the Ed25519 private key from storage.
     * Returns a java.security.KeyPair suitable for SSHJ authentication.
     */
    fun loadKeyPair(): java.security.KeyPair {
        val encryptedSeed = File(keyDir, PRIVATE_KEY_FILE).readBytes()
        val iv = File(keyDir, IV_FILE).readBytes()
        val aesKey = getOrCreateAesKey()

        // Decrypt the seed
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
        val seed = cipher.doFinal(encryptedSeed)

        // Reconstruct the Ed25519 key pair from the seed
        val ed25519Spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
        val privateKeySpec = EdDSAPrivateKeySpec(seed, ed25519Spec)
        val privateKey = EdDSAPrivateKey(privateKeySpec)

        val publicKeySpec = EdDSAPublicKeySpec(privateKey.a, ed25519Spec)
        val publicKey = EdDSAPublicKey(publicKeySpec)

        return java.security.KeyPair(publicKey, privateKey)
    }

    /**
     * Returns the public key in OpenSSH format (for display / copying to Pi).
     */
    fun getPublicKeyString(): String {
        val pubFile = File(keyDir, PUBLIC_KEY_FILE)
        return if (pubFile.exists()) pubFile.readText() else ""
    }

    /**
     * Deletes all keys. Used for key rotation or reset.
     */
    fun deleteKeyPair() {
        File(keyDir, PRIVATE_KEY_FILE).delete()
        File(keyDir, PUBLIC_KEY_FILE).delete()
        File(keyDir, IV_FILE).delete()
        // Also remove the AES wrapper key
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)
        ks.deleteEntry(KEYSTORE_ALIAS)
        Log.i(TAG, "All keys deleted.")
    }

    // ── Internal ────────────────────────────────────────────────────────────

    /**
     * Gets the AES-256-GCM key from the Android Keystore, or creates one
     * if it doesn't exist yet.
     */
    private fun getOrCreateAesKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
        ks.load(null)

        ks.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        // Create new AES key in Keystore
        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // Don't require biometrics
                .build()
        )
        return keyGen.generateKey()
    }

    /**
     * Formats an EdDSA public key into OpenSSH format:
     * ssh-ed25519 AAAA...base64... glassterminal@android
     */
    private fun formatOpenSshPublicKey(publicKey: EdDSAPublicKey): String {
        val keyType = "ssh-ed25519"
        val keyTypeBytes = keyType.toByteArray(Charsets.US_ASCII)
        val pubKeyBytes = publicKey.abyte

        // OpenSSH wire format: 4-byte length prefix + data
        val buffer = java.io.ByteArrayOutputStream()
        buffer.writeInt(keyTypeBytes.size)
        buffer.write(keyTypeBytes)
        buffer.writeInt(pubKeyBytes.size)
        buffer.write(pubKeyBytes)

        val encoded = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
        return "$keyType $encoded glassterminal@android"
    }

    private fun java.io.ByteArrayOutputStream.writeInt(value: Int) {
        write((value shr 24) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }
}
