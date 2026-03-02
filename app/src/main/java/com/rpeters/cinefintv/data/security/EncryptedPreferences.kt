package com.rpeters.cinefintv.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rpeters.cinefintv.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage for sensitive data using AES-256-GCM encryption with Android Keystore.
 *
 * This class provides defense-in-depth security through:
 * 1. **Hardware-backed encryption**: Uses Android Keystore (hardware TEE on supported devices)
 * 2. **AES-256-GCM encryption**: Industry-standard authenticated encryption
 * 3. **Random IVs**: Each encryption uses a fresh 96-bit initialization vector
 * 4. **Tamper detection**: GCM mode provides authentication tag (prevents modification)
 * 5. **Secure storage**: Encrypted values stored in DataStore (never plaintext)
 *
 * Encryption process:
 * - Generate random 12-byte IV
 * - Encrypt plaintext with AES-256-GCM using Keystore-backed key
 * - Prepend IV to ciphertext
 * - Base64 encode for storage
 *
 * Security guarantees:
 * - Encryption key never leaves Android Keystore
 * - Key protected by device lock screen (if configured)
 * - Authenticated encryption prevents tampering
 * - Decryption fails if data is modified or wrong key used
 *
 * SECURITY: This should be used for:
 * - Download URLs containing authentication tokens
 * - Temporary session tokens or credentials
 * - Any data that could be used to access media content
 * - PII or sensitive user data
 */
@Singleton
class EncryptedPreferences @Inject constructor(
    private val context: Context,
) {

    companion object {
        private const val TAG = "EncryptedPreferences"
        private const val ENCRYPTED_PREFS_NAME = "jellyfin_secure_prefs"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12 // 96 bits for GCM
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "jellyfin_encryption_key"
    }

    // Lazily initialize Android Keystore key
    private val secretKey: SecretKey by lazy {
        getOrCreateSecretKey()
    }

    /**
     * Gets or creates a SecretKey from Android Keystore.
     * The key is hardware-backed on supported devices and never leaves the secure hardware.
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

        // Check if key already exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey?.let { return it }
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Don't require screen lock for every access
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts a sensitive value using AES-256-GCM with Android Keystore.
     *
     * This implementation:
     * 1. Lets Android Keystore generate a random 96-bit IV (GCM standard, Android 16+ compatible)
     * 2. Encrypts the plaintext using AES-256-GCM with the Keystore-backed key
     * 3. Prepends the Keystore-generated IV to the ciphertext (required for decryption)
     * 4. Base64 encodes the result for storage
     *
     * The encryption key never leaves the Android Keystore and is hardware-backed
     * on supported devices, providing strong protection against key extraction.
     *
     * IMPORTANT: On Android 16+, the Keystore must generate the IV to avoid
     * CALLER_NONCE_PROHIBITED errors. This is more secure as it ensures proper
     * randomness and prevents IV reuse attacks.
     *
     * @param value The plaintext value to encrypt
     * @return Base64 encoded (IV + ciphertext), or null on error
     */
    fun encryptValue(value: String): String? {
        if (value.isEmpty()) return null

        return try {
            // Initialize cipher for encryption WITHOUT providing an IV
            // Android Keystore will generate a secure random IV automatically
            // This is required for Android 16+ to avoid CALLER_NONCE_PROHIBITED errors
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            // Encrypt the plaintext
            val plaintext = value.toByteArray(StandardCharsets.UTF_8)
            val ciphertext = cipher.doFinal(plaintext)

            // Get the Keystore-generated IV (12 bytes = 96 bits for GCM)
            val iv = cipher.iv
            if (iv == null || iv.size != IV_LENGTH) {
                Log.e(TAG, "Encryption failed - invalid IV generated by Keystore")
                return null
            }

            // Combine IV + ciphertext
            val combined = ByteBuffer.allocate(IV_LENGTH + ciphertext.size)
                .put(iv)
                .put(ciphertext)
                .array()

            // Base64 encode for storage in DataStore
            android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        } catch (e: GeneralSecurityException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Encryption failed - security error", e)
            }
            // SECURITY: Never log the actual value
            null
        }
    }

    /**
     * Decrypts a previously encrypted value.
     *
     * This implementation:
     * 1. Base64 decodes the input to get (IV + ciphertext)
     * 2. Extracts the first 12 bytes as the IV
     * 3. Uses remaining bytes as the ciphertext
     * 4. Decrypts using AES-256-GCM with the Keystore-backed key
     *
     * GCM mode provides authenticated encryption, so decryption will fail if:
     * - The ciphertext has been modified (tampering detection)
     * - The wrong key is used
     * - The IV is incorrect
     *
     * @param encryptedValue Base64 encoded (IV + ciphertext)
     * @return Decrypted plaintext value, or null on error
     */
    fun decryptValue(encryptedValue: String?): String? {
        if (encryptedValue.isNullOrEmpty()) return null

        return try {
            // Base64 decode to get IV + ciphertext
            val combined = android.util.Base64.decode(encryptedValue, android.util.Base64.NO_WRAP)

            // Validate minimum length (IV + at least some ciphertext + GCM tag)
            if (combined.size < IV_LENGTH + 16) { // 16 bytes = GCM authentication tag
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Encrypted data too short - may be corrupted")
                }
                return null
            }

            // Extract IV (first 12 bytes)
            val iv = combined.copyOfRange(0, IV_LENGTH)

            // Extract ciphertext (remaining bytes)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)

            // Initialize cipher for decryption
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            // Decrypt the ciphertext
            val plaintext = cipher.doFinal(ciphertext)

            // Convert to string
            String(plaintext, StandardCharsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Decryption failed - data may be corrupted, tampered, or using wrong key", e)
            }
            // SECURITY: Don't log the encrypted value or any details that could help an attacker
            null
        }
    }

    /**
     * Encrypts and stores a sensitive string value.
     *
     * @param key The preference key
     * @param value The sensitive value to encrypt and store
     */
    suspend fun putEncryptedString(key: String, value: String?) {
        if (value == null) {
            removeKey(key)
            return
        }

        val encrypted = encryptValue(value)
        if (encrypted != null) {
            context.secureDataStore.edit { prefs ->
                prefs[stringPreferencesKey(key)] = encrypted
            }
        } else {
            Log.e(TAG, "Failed to encrypt value for key: $key")
        }
    }

    /**
     * Retrieves and decrypts a sensitive string value.
     *
     * @param key The preference key
     * @return Flow of decrypted value or null if not found/decryption failed
     */
    fun getEncryptedString(key: String): Flow<String?> {
        return context.secureDataStore.data
            .catch { exception ->
                Log.e(TAG, "Error reading secure preferences", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            }
            .map { prefs ->
                val encrypted = prefs[stringPreferencesKey(key)]
                decryptValue(encrypted)
            }
    }

    /**
     * Returns decrypted entries that share the provided prefix.
     */
    suspend fun getEntriesWithPrefix(prefix: String): Map<String, String> {
        val preferences = context.secureDataStore.data.first()
        return preferences.asMap().mapNotNull { (key, value) ->
            val name = key.name
            if (!name.startsWith(prefix)) {
                return@mapNotNull null
            }
            val decrypted = decryptValue(value as? String)
            if (decrypted != null) {
                name.removePrefix(prefix) to decrypted
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Removes an encrypted value from storage.
     */
    suspend fun removeKey(key: String) {
        context.secureDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(key))
        }
    }

    /**
     * Clears all encrypted preferences.
     * SECURITY WARNING: This should only be called during logout or app reset.
     */
    suspend fun clearAll() {
        context.secureDataStore.edit { it.clear() }
    }
}

// DataStore extension for secure preferences
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "jellyfin_secure_prefs",
)
