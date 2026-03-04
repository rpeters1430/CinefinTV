package com.rpeters.cinefintv.data

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.security.keystore.UserNotAuthenticatedException
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.rpeters.cinefintv.BuildConfig
import com.rpeters.cinefintv.core.constants.Constants
import com.rpeters.cinefintv.data.preferences.CredentialSecurityPreferencesRepository
import com.rpeters.cinefintv.utils.SecureLogger
import com.rpeters.cinefintv.utils.normalizeServerUrl
import com.rpeters.cinefintv.utils.normalizeServerUrlLegacy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureCredentialManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentialSecurityPreferencesRepository: CredentialSecurityPreferencesRepository,
) {
    private val jsonSerializer = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
    }

    companion object {
        private const val TAG = "SecureCredentialManager"
        private const val KEY_VERSION = "v1"
        private const val KEY_ROTATION_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val DATASTORE_NAME = "secure_credentials"
        private const val USER_AUTH_VALIDITY_WINDOW_SECONDS = 300
        private const val SERVER_STATE_KEY = "last_server_state"
    }

    // CRITICAL FIX: DataStore needs a CoroutineScope to properly persist data
    // Without a scope, edit operations may not commit changes to disk
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ✅ FIX: Create DataStore directly as a property instead of using Context extension
    // This avoids potential issues with Context extension properties in Singleton classes
    // CRITICAL: Must provide a scope parameter to ensure proper persistence
    private val secureCredentialsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = {
                context.preferencesDataStoreFile(DATASTORE_NAME)
            },
        )

    // ✅ ENHANCEMENT: Use modern Android Keystore with DataStore for secure storage
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    @Volatile
    private var cachedUserAuthRequired: Boolean? = null

    // Note: biometric authentication is excluded from the TV MVP (no BiometricAuthManager)

    @VisibleForTesting
    internal var debugLoggingEnabled: Boolean = BuildConfig.DEBUG

    @VisibleForTesting
    internal fun logDebug(message: () -> String) {
        if (debugLoggingEnabled) {
            SecureLogger.d(TAG, message())
        }
    }

    /**
     * Biometric authentication is not available in the TV MVP.
     * Returns false always; can be wired in Task 24 if biometrics are added.
     */
    fun isBiometricAuthAvailable(requireStrongBiometric: Boolean = false): Boolean = false

    /**
     * Generates a stable key alias.
     * We no longer use timestamps for rotation to prevent data loss.
     */
    private fun getKeyAlias(): String {
        return "${Constants.Security.KEY_ALIAS}_${KEY_VERSION}"
    }

    private fun getRotatedKeyAlias(): String {
        return "${getKeyAlias()}_${System.currentTimeMillis()}"
    }

    /**
     * Gets or creates a secret key.
     * Prioritizes stable alias, but falls back to legacy timestamped aliases to preserve existing logins.
     * Performs keystore operations on background thread.
     */
    private suspend fun getOrCreateSecretKey(forceNew: Boolean = false): SecretKey = withContext(Dispatchers.IO) {
        val stableAlias = getKeyAlias()

        // Find any existing legacy keys (timestamped or buggy)
        // This is crucial for migrating users who have keys generated with the old rotation logic
        var existingKeyAlias: String? = null
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            // Check for our key prefix
            if (alias.startsWith(Constants.Security.KEY_ALIAS)) {
                // If it's the stable alias, prefer it
                if (alias == stableAlias) {
                    existingKeyAlias = alias
                    break // Found best match
                }
                // If we haven't found a stable alias yet, track this as a candidate
                // This catches ..._v1_0, ..._v1_1, and the buggy ..._v1}_0
                if (existingKeyAlias == null) {
                    existingKeyAlias = alias
                }
            }
        }

        if (forceNew) {
            // Delete all our keys to start fresh
            val cleanupAliases = keyStore.aliases()
            while (cleanupAliases.hasMoreElements()) {
                val alias = cleanupAliases.nextElement()
                if (alias.startsWith(Constants.Security.KEY_ALIAS)) {
                    try { keyStore.deleteEntry(alias) } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete old key: $alias", e)
                    }
                }
            }
            return@withContext generateKey(stableAlias, userAuthenticationRequired())
        }

        // Return existing key if found
        if (existingKeyAlias != null) {
            logDebug { "Using existing key alias: $existingKeyAlias" }
            return@withContext keyStore.getKey(existingKeyAlias, null) as SecretKey
        }

        // No key found, generate stable one
        logDebug { "No existing key found, generating new stable key: $stableAlias" }
        return@withContext generateKey(stableAlias, userAuthenticationRequired())
    }

    private fun deleteAliasIfExists(alias: String) {
        if (keyStore.containsAlias(alias)) {
            try {
                keyStore.deleteEntry(alias)
            } catch (e: KeyStoreException) {
                Log.w(TAG, "Failed to delete key alias: $alias", e)
            }
        }
    }

    private fun removeOldKeys(excludeAlias: String) {
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (alias.startsWith(Constants.Security.KEY_ALIAS) && alias != excludeAlias) {
                try {
                    keyStore.deleteEntry(alias)
                } catch (e: KeyStoreException) {
                    Log.w(TAG, "Failed to delete old key: $alias", e)
                }
            }
        }
    }

    private fun getLatestKeyAlias(): String? {
        val aliases = keyStore.aliases()
        var latestAlias: String? = null
        var latestTimestamp = Long.MIN_VALUE

        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()
            if (!alias.startsWith(Constants.Security.KEY_ALIAS)) {
                continue
            }
            val timestamp = parseKeyAliasTimestamp(alias) ?: continue
            if (timestamp > latestTimestamp) {
                latestTimestamp = timestamp
                latestAlias = alias
            }
        }

        return latestAlias
    }

    private fun parseKeyAliasTimestamp(alias: String): Long? {
        val match = Regex("(\\d+)(?:_(\\d+))?$").find(alias) ?: return null
        val primaryValue = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?: match.groupValues.getOrNull(1)
        return primaryValue?.toLongOrNull()
    }

    private fun generateKey(alias: String, requireUserAuthentication: Boolean): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(requireUserAuthentication)
            .apply {
                if (requireUserAuthentication) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(
                            USER_AUTH_VALIDITY_WINDOW_SECONDS,
                            KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(USER_AUTH_VALIDITY_WINDOW_SECONDS)
                    }
                }
            }
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private suspend fun userAuthenticationRequired(): Boolean {
        cachedUserAuthRequired?.let { return it }
        val preference = credentialSecurityPreferencesRepository.currentPreferences().requireStrongAuthForCredentials
        cachedUserAuthRequired = preference
        return preference
    }

    /**
     * Encrypts data using AES/GCM/NoPadding with a new IV for each encryption.
     * Performs keystore operations on background thread.
     */
    private suspend fun encrypt(data: String): String = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(Constants.Security.ENCRYPTION_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data.toByteArray())

            // Combine IV + encrypted data and encode to Base64
            val combined = iv + encryptedData
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Decrypts data using AES/GCM/NoPadding.
     * Performs keystore operations on background thread.
     */
    private suspend fun decrypt(encryptedData: String): String? = withContext(Dispatchers.IO) {
        try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = combined.sliceArray(0 until Constants.Security.IV_LENGTH)
            val cipherData = combined.sliceArray(Constants.Security.IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(Constants.Security.ENCRYPTION_TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

            String(cipher.doFinal(cipherData))
        } catch (e: CancellationException) {
            throw e
        } catch (e: UserNotAuthenticatedException) {
            logDebug { "decrypt: user authentication required before key use" }
            null
        } catch (e: Exception) {
            SecureLogger.w(TAG, "decrypt: failed to decrypt saved credential", e)
            null
        }
    }

    /**
     * Forces key rotation by generating a new key
     */
    suspend fun rotateKey() {
        try {
            getOrCreateSecretKey(forceNew = true)
            logDebug { "Key rotation completed successfully" }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun applyCredentialAuthenticationRequirement(requireAuthentication: Boolean) {
        val currentRequirement = userAuthenticationRequired()
        if (currentRequirement == requireAuthentication) {
            return
        }

        val existingCredentials = exportPlaintextCredentials()
        val newAlias = getRotatedKeyAlias()

        try {
            generateKey(newAlias, requireUserAuthentication = requireAuthentication)
            restoreCredentials(existingCredentials)
            credentialSecurityPreferencesRepository.setRequireStrongAuthForCredentials(requireAuthentication)
            cachedUserAuthRequired = requireAuthentication
            removeOldKeys(newAlias)
        } catch (e: CancellationException) {
            throw e
        }
    }

    // ✅ ENHANCEMENT: Modern secure storage with Android Keystore + DataStore + Key Rotation
    suspend fun savePassword(serverUrl: String, username: String, password: String) {
        val keys = generateKeys(serverUrl, username)
        val encryptedPassword = encrypt(password)

        logDebug { "savePassword: Saving password for user='$username', serverUrl='$serverUrl'" }
        logDebug { "savePassword: Generated key='${keys.newKey}'" }

        try {
            logDebug { "savePassword: 🔵 ENTERING NonCancellable block - password save cannot be cancelled from here" }
            // CRITICAL FIX: Use NonCancellable to ensure password save completes
            // even if parent scope is cancelled (e.g., due to navigation after login)
            // This prevents JobCancellationException when DataStore operations are interrupted
            withContext(NonCancellable + Dispatchers.IO) {
                // Check DataStore state before edit
                val beforeEdit = secureCredentialsDataStore.data.first()
                logDebug { "savePassword: DataStore before edit contains ${beforeEdit.asMap().size} entries" }

                secureCredentialsDataStore.edit { prefs ->
                    logDebug { "savePassword: Inside edit block, setting key='${keys.newKey}'" }
                    logDebug { "savePassword: Before setting - prefs.asMap().size = ${prefs.asMap().size}" }

                    val passwordKey = stringPreferencesKey(keys.newKey)
                    val timestampKey = longPreferencesKey("${keys.newKey}_timestamp")

                    logDebug { "savePassword: Setting password with key: $passwordKey" }
                    prefs[passwordKey] = encryptedPassword
                    logDebug { "savePassword: Password set, prefs.asMap().size = ${prefs.asMap().size}" }
                    logDebug { "savePassword: Verifying password was set: ${prefs[passwordKey] != null}" }

                    logDebug { "savePassword: Setting timestamp with key: $timestampKey" }
                    prefs[timestampKey] = System.currentTimeMillis()
                    logDebug { "savePassword: Timestamp set, prefs.asMap().size = ${prefs.asMap().size}" }

                    // CRITICAL FIX: Only remove legacy keys if they're different from the new key
                    // If they're the same, we'd be deleting the password we just saved!
                    if (keys.legacyRawKey != keys.newKey) {
                        logDebug { "savePassword: Removing legacy raw key: ${keys.legacyRawKey}" }
                        prefs.remove(stringPreferencesKey(keys.legacyRawKey))
                        prefs.remove(longPreferencesKey("${keys.legacyRawKey}_timestamp"))
                    } else {
                        logDebug { "savePassword: Skipping removal of legacyRawKey (same as newKey)" }
                    }

                    if (keys.legacyNormalizedKey != keys.newKey) {
                        logDebug { "savePassword: Removing legacy normalized key: ${keys.legacyNormalizedKey}" }
                        prefs.remove(stringPreferencesKey(keys.legacyNormalizedKey))
                        prefs.remove(longPreferencesKey("${keys.legacyNormalizedKey}_timestamp"))
                    } else {
                        logDebug { "savePassword: Skipping removal of legacyNormalizedKey (same as newKey)" }
                    }

                    logDebug { "savePassword: Edit block completed, prefs now contains ${prefs.asMap().size} entries" }
                    logDebug { "savePassword: Keys in prefs: ${prefs.asMap().keys.joinToString { it.name }} " }
                }

                logDebug { "savePassword: Edit operation returned successfully" }

                // Verify the password was saved by immediately reading it back
                val verification = secureCredentialsDataStore.data.first()
                logDebug { "savePassword: Verification read returned ${verification.asMap().size} entries" }

                val savedPassword = verification[stringPreferencesKey(keys.newKey)]
                if (savedPassword != null) {
                    logDebug { "savePassword: ✅ Password saved successfully and verified in DataStore" }
                    logDebug { "savePassword: DataStore now contains ${verification.asMap().size} entries" }
                } else {
                    SecureLogger.e(TAG, "savePassword: ❌ ERROR - Password was not found in DataStore after saving!")
                    SecureLogger.e(TAG, "savePassword: DataStore keys present: ${verification.asMap().keys.joinToString { it.name }}")
                }
            }
            logDebug { "savePassword: 🟢 EXITED NonCancellable block - password save operation completed" }
        } catch (e: CancellationException) {
            throw e
        }
    }

    /**
     * Gets a password with optional biometric authentication
     *
     * @param serverUrl The server URL
     * @param username The username
     * @param activity The activity to use for biometric prompt (if null, no biometric auth)
     * @return The decrypted password or null if not found or auth failed
     */
    suspend fun getPassword(
        serverUrl: String,
        username: String,
        // activity parameter kept for API compatibility but biometrics are no-op in TV MVP
        activity: Any? = null,
        requireStrongBiometric: Boolean = false,
    ): String? {
        logDebug { "getPassword: CALLED - Retrieving password for user='$username', serverUrl='$serverUrl'" }

        return try {
            // Biometric auth is not used in the TV MVP - proceed directly to key lookup
            val keys = generateKeys(serverUrl, username)
            logDebug { "getPassword: Generated key='${keys.newKey}'" }

            val preferences = secureCredentialsDataStore.data.first()
            logDebug { "getPassword: DataStore contains ${preferences.asMap().size} entries" }
            var encryptedPassword = preferences[stringPreferencesKey(keys.newKey)]

            if (encryptedPassword == null) {
                logDebug { "getPassword: Password not found with new key='${keys.newKey}', checking legacy keys" }
                val legacySearchOrder = listOf(keys.legacyNormalizedKey, keys.legacyRawKey)
                var matchedKey: String? = null
                for (legacyKey in legacySearchOrder) {
                    logDebug { "getPassword: Checking legacy key='$legacyKey'" }
                    encryptedPassword = preferences[stringPreferencesKey(legacyKey)]
                    if (encryptedPassword != null) {
                        matchedKey = legacyKey
                        logDebug { "getPassword: Found password with legacy key='$legacyKey'" }
                        break
                    }
                }

                // FIX: If still not found, try with the raw (non-normalized) server URL
                // This handles the case where credentials were saved before URL normalization was fixed
                if (encryptedPassword == null) {
                    logDebug { "getPassword: Checking with raw server URL (pre-normalization fix)" }
                    val rawKey = generateKey(serverUrl, username)
                    if (rawKey != keys.newKey) { // Don't check the same key twice
                        logDebug { "getPassword: Trying raw URL key='$rawKey'" }
                        encryptedPassword = preferences[stringPreferencesKey(rawKey)]
                        if (encryptedPassword != null) {
                            matchedKey = rawKey
                            logDebug { "getPassword: Found password with raw URL key='$rawKey'" }
                        }
                    }
                }

                if (encryptedPassword != null && matchedKey != null) {
                    logDebug { "getPassword: Migrating legacy credential from key='$matchedKey' to key='${keys.newKey}'" }
                    migrateLegacyCredential(matchedKey, keys.newKey, encryptedPassword)
                }
            } else {
                logDebug { "getPassword: Found password with new key" }
            }

            val result = encryptedPassword?.let { decrypt(it) }
            if (result != null) {
                logDebug { "🟢 getPassword: SUCCESS - Password retrieved and decrypted" }
            } else {
                SecureLogger.e(
                    TAG,
                    "🔴 getPassword: FAILED - Password is NULL (encryptedPassword was ${if (encryptedPassword != null) "found but decrypt failed" else "not found in DataStore"})",
                )
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SecureLogger.e(TAG, "getPassword: Unexpected error while retrieving saved password", e)
            null
        }
    }

    /**
     * Gets a password without biometric authentication (for backward compatibility)
     */
    suspend fun getPassword(serverUrl: String, username: String): String? {
        return getPassword(serverUrl, username, null)
    }

    suspend fun clearPassword(serverUrl: String, username: String) {
        val keys = generateKeys(serverUrl, username)
        secureCredentialsDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(keys.newKey))
            prefs.remove(longPreferencesKey("${keys.newKey}_timestamp"))
            prefs.remove(stringPreferencesKey(keys.legacyRawKey))
            prefs.remove(longPreferencesKey("${keys.legacyRawKey}_timestamp"))
            prefs.remove(stringPreferencesKey(keys.legacyNormalizedKey))
            prefs.remove(longPreferencesKey("${keys.legacyNormalizedKey}_timestamp"))
        }
    }

    suspend fun hasSavedPassword(serverUrl: String, username: String): Boolean {
        val keys = generateKeys(serverUrl, username)
        val preferences = secureCredentialsDataStore.data.first()
        val candidates = listOf(keys.newKey, keys.legacyNormalizedKey, keys.legacyRawKey)
        val hasPassword = candidates.any { key ->
            preferences[stringPreferencesKey(key)] != null
        }
        logDebug { "hasSavedPassword: serverUrl='$serverUrl', username='$username', result=$hasPassword" }
        return hasPassword
    }

    suspend fun clearAllPasswords() {
        secureCredentialsDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun clearCredentials() {
        clearAllPasswords()
    }

    suspend fun saveServerState(server: JellyfinServer) {
        try {
            val json = kotlinx.serialization.json.Json.encodeToString(server)
            withContext(NonCancellable + Dispatchers.IO) {
                secureCredentialsDataStore.edit { prefs ->
                    prefs[stringPreferencesKey(SERVER_STATE_KEY)] = json
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "saveServerState: failed to save server state", e)
        }
    }

    suspend fun loadServerState(): JellyfinServer? {
        return try {
            val prefs = secureCredentialsDataStore.data.first()
            val json = prefs[stringPreferencesKey(SERVER_STATE_KEY)] ?: return null
            jsonSerializer.decodeFromString<JellyfinServer>(json)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "loadServerState: failed to load server state", e)
            null
        }
    }

    suspend fun clearServerState() {
        withContext(NonCancellable + Dispatchers.IO) {
            secureCredentialsDataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey(SERVER_STATE_KEY))
            }
        }
    }

    private suspend fun exportPlaintextCredentials(): List<StoredCredential> = withContext(Dispatchers.IO) {
        val preferences = secureCredentialsDataStore.data.first()
        val credentials = mutableListOf<StoredCredential>()

        preferences.asMap().forEach { (key, value) ->
            if (key.name.startsWith("pwd_") && !key.name.endsWith("_timestamp")) {
                val encryptedPassword = value as? String ?: return@forEach
                val decryptedPassword = decrypt(encryptedPassword)
                if (decryptedPassword == null) {
                    Log.w(TAG, "Failed to decrypt credential for key: ${key.name}")
                    throw SecurityException("Failed to decrypt credential for key: ${key.name}")
                }
                val timestamp = preferences[longPreferencesKey("${key.name}_timestamp")]
                credentials.add(
                    StoredCredential(
                        preferenceKey = key.name,
                        plaintext = decryptedPassword,
                        timestamp = timestamp,
                    ),
                )
            }
        }

        credentials
    }

    private suspend fun restoreCredentials(credentials: List<StoredCredential>) {
        if (credentials.isEmpty()) return

        withContext(NonCancellable + Dispatchers.IO) {
            secureCredentialsDataStore.edit { prefs ->
                credentials.forEach { credential ->
                    val encrypted = encrypt(credential.plaintext)
                    prefs[stringPreferencesKey(credential.preferenceKey)] = encrypted
                    credential.timestamp?.let { timestamp ->
                        prefs[longPreferencesKey("${credential.preferenceKey}_timestamp")] = timestamp
                    }
                }
            }
        }
    }

    private data class CredentialKeys(
        val newKey: String,
        val legacyRawKey: String,
        val legacyNormalizedKey: String,
    )

    private data class StoredCredential(
        val preferenceKey: String,
        val plaintext: String,
        val timestamp: Long?,
    )

    private fun generateKeys(serverUrl: String, username: String): CredentialKeys {
        val normalizedUrl = normalizeServerUrl(serverUrl)
        val legacyNormalizedUrl = normalizeServerUrlLegacy(serverUrl)
        val rawTrimmedUrl = serverUrl.trim()

        val newKey = generateKey(normalizedUrl, username)
        val legacyNormalizedKey = generateKey(legacyNormalizedUrl, username)
        val legacyRawKey = generateKey(rawTrimmedUrl, username)

        logDebug { "generateKeys: newKey='$newKey'" }
        logDebug { "generateKeys: legacyRawKey='$legacyRawKey'" }
        logDebug { "generateKeys: legacyNormalizedKey='$legacyNormalizedKey'" }
        logDebug { "generateKeys: Are any keys identical? newKey==legacyRawKey:${newKey == legacyRawKey}, newKey==legacyNormalizedKey:${newKey == legacyNormalizedKey}" }

        return CredentialKeys(newKey, legacyRawKey, legacyNormalizedKey)
    }

    private suspend fun migrateLegacyCredential(
        oldKey: String,
        newKey: String,
        encryptedPassword: String,
    ) {
        secureCredentialsDataStore.edit { prefs ->
            prefs[stringPreferencesKey(newKey)] = encryptedPassword
            prefs[longPreferencesKey("${newKey}_timestamp")] =
                prefs[longPreferencesKey("${oldKey}_timestamp")] ?: System.currentTimeMillis()
            prefs.remove(stringPreferencesKey(oldKey))
            prefs.remove(longPreferencesKey("${oldKey}_timestamp"))
        }
    }

    // ✅ ENHANCEMENT: Improved key generation with cryptographically secure hashing
    @VisibleForTesting
    internal fun generateKey(serverUrl: String, username: String): String {
        require(serverUrl.isNotBlank()) { "Server URL cannot be blank" }
        require(username.isNotBlank()) { "Username cannot be blank" }

        // Use SHA-256 for more secure key generation
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$serverUrl::$username::${Constants.Security.KEY_ALIAS}".toByteArray()
        val hash = digest.digest(input)

        // Take first 16 bytes and encode as hex for the key
        val keyBytes = hash.sliceArray(0 until 16)
        return "pwd_${keyBytes.joinToString("") { "%02x".format(it) }}"
    }
}
