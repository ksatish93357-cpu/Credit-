package com.example.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.FirebaseCloudConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "credit_logger_security")

data class SecurityState(
    val hasPin: Boolean = false,
    val autoLockSeconds: Long = 60L,
    val biometricEnabled: Boolean = false,
    val lastBackgroundTimestamp: Long = 0L
)

class SecurityPreferencesStore(private val context: Context) {

    private object Keys {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val AUTO_LOCK_SECONDS = longPreferencesKey("auto_lock_seconds")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val LAST_BG_TIME = longPreferencesKey("last_bg_time")

        // Firebase Cloud Config (never exported in JSON)
        val FB_API_KEY = stringPreferencesKey("fb_api_key")
        val FB_AUTH_DOMAIN = stringPreferencesKey("fb_auth_domain")
        val FB_PROJECT_ID = stringPreferencesKey("fb_project_id")
        val FB_STORAGE_BUCKET = stringPreferencesKey("fb_storage_bucket")
        val FB_SENDER_ID = stringPreferencesKey("fb_sender_id")
        val FB_APP_ID = stringPreferencesKey("fb_app_id")
        val FB_USER_EMAIL = stringPreferencesKey("fb_user_email")
        val FB_CONNECTED = booleanPreferencesKey("fb_connected")
        val FB_LAST_SYNC_TIME = longPreferencesKey("fb_last_sync_time")
        val FB_LAST_SYNC_STATUS = stringPreferencesKey("fb_last_sync_status")
        val FB_LAST_ERROR = stringPreferencesKey("fb_last_error")

        // Normal calculator history
        val CALC_HISTORY = stringPreferencesKey("calc_history_json")
    }

    val securityState: Flow<SecurityState> = context.securityDataStore.data.map { prefs ->
        SecurityState(
            hasPin = !prefs[Keys.PIN_HASH].isNullOrBlank(),
            autoLockSeconds = prefs[Keys.AUTO_LOCK_SECONDS] ?: 60L,
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            lastBackgroundTimestamp = prefs[Keys.LAST_BG_TIME] ?: 0L
        )
    }

    val firebaseConfig: Flow<FirebaseCloudConfig> = context.securityDataStore.data.map { prefs ->
        FirebaseCloudConfig(
            apiKey = prefs[Keys.FB_API_KEY] ?: "",
            authDomain = prefs[Keys.FB_AUTH_DOMAIN] ?: "",
            projectId = prefs[Keys.FB_PROJECT_ID] ?: "",
            storageBucket = prefs[Keys.FB_STORAGE_BUCKET] ?: "",
            messagingSenderId = prefs[Keys.FB_SENDER_ID] ?: "",
            appId = prefs[Keys.FB_APP_ID] ?: "",
            userEmail = prefs[Keys.FB_USER_EMAIL] ?: "",
            isConnected = prefs[Keys.FB_CONNECTED] ?: false,
            lastSyncTime = prefs[Keys.FB_LAST_SYNC_TIME] ?: 0L,
            lastSyncStatus = prefs[Keys.FB_LAST_SYNC_STATUS] ?: "",
            lastError = prefs[Keys.FB_LAST_ERROR] ?: ""
        )
    }

    val calculatorHistory: Flow<List<String>> = context.securityDataStore.data.map { prefs ->
        val raw = prefs[Keys.CALC_HISTORY] ?: "[]"
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun setPin(pin: String) {
        require(pin.length == 4 && pin.all { it.isDigit() }) { "PIN must be 4 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        context.securityDataStore.edit { prefs ->
            prefs[Keys.PIN_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[Keys.PIN_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.securityDataStore.data.first()
        val storedSalt = prefs[Keys.PIN_SALT] ?: return false
        val storedHash = prefs[Keys.PIN_HASH] ?: return false
        val saltBytes = Base64.decode(storedSalt, Base64.NO_WRAP)
        val expectedHash = Base64.decode(storedHash, Base64.NO_WRAP)
        val actualHash = pbkdf2(pin, saltBytes)
        return actualHash.contentEquals(expectedHash)
    }

    suspend fun clearPin() {
        context.securityDataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun setAutoLockSeconds(seconds: Long) {
        context.securityDataStore.edit { it[Keys.AUTO_LOCK_SECONDS] = seconds }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun markBackgrounded(timestamp: Long) {
        context.securityDataStore.edit { it[Keys.LAST_BG_TIME] = timestamp }
    }

    suspend fun saveFirebaseConfig(config: FirebaseCloudConfig) {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.FB_API_KEY] = config.apiKey
            prefs[Keys.FB_AUTH_DOMAIN] = config.authDomain
            prefs[Keys.FB_PROJECT_ID] = config.projectId
            prefs[Keys.FB_STORAGE_BUCKET] = config.storageBucket
            prefs[Keys.FB_SENDER_ID] = config.messagingSenderId
            prefs[Keys.FB_APP_ID] = config.appId
            prefs[Keys.FB_USER_EMAIL] = config.userEmail
            prefs[Keys.FB_CONNECTED] = config.isConnected
            prefs[Keys.FB_LAST_SYNC_TIME] = config.lastSyncTime
            prefs[Keys.FB_LAST_SYNC_STATUS] = config.lastSyncStatus
            prefs[Keys.FB_LAST_ERROR] = config.lastError
        }
    }

    suspend fun clearFirebaseConnection() {
        context.securityDataStore.edit { prefs ->
            prefs[Keys.FB_CONNECTED] = false
            prefs[Keys.FB_LAST_SYNC_STATUS] = "Disconnected (local data intact)"
            prefs[Keys.FB_LAST_ERROR] = ""
        }
    }

    suspend fun appendCalculatorHistory(entry: String) {
        context.securityDataStore.edit { prefs ->
            val current = try {
                val arr = JSONArray(prefs[Keys.CALC_HISTORY] ?: "[]")
                (0 until arr.length()).map { arr.getString(it) }.toMutableList()
            } catch (_: Exception) {
                mutableListOf()
            }
            current.add(0, entry)
            val trimmed = current.take(30)
            prefs[Keys.CALC_HISTORY] = JSONArray(trimmed).toString()
        }
    }

    suspend fun clearCalculatorHistory() {
        context.securityDataStore.edit { it[Keys.CALC_HISTORY] = "[]" }
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 12000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
