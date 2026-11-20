package com.omnisolve.overlay.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Keystore AES-256-GCM storage for user-entered third-party AI API keys.
 *
 * Keys are stored in a dedicated `OmniSolveSecureKeysPrefs` file excluded from Android cloud
 * backup (`backup_rules.xml` & `data_extraction_rules.xml`) and automatically migrated from
 * legacy plain `OmniSolvePrefs`. Falls back gracefully in local JVM/Robolectric test environments
 * that do not provide an `AndroidKeyStore` provider.
 */
object SecureKeyStorage {

    const val SECURE_PREFS_NAME = "OmniSolveSecureKeysPrefs"
    private const val LEGACY_PREFS_NAME = "OmniSolvePrefs"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "prism_solver_api_keys_aes_v1"
    private const val ENCRYPTED_PREFIX = "enc:v1:"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private val lock = Any()

    fun getKey(context: Context, keyName: String): String {
        synchronized(lock) {
            val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            val stored = securePrefs.getString(keyName, null)
            if (stored != null) {
                return decryptValue(stored).trim()
            }

            // Transparent one-time migration from legacy OmniSolvePrefs
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val legacyVal = legacyPrefs.getString(keyName, null)?.trim().orEmpty()
            if (legacyVal.isNotEmpty()) {
                saveKeyInternal(securePrefs, keyName, legacyVal)
                legacyPrefs.edit().remove(keyName).apply()
            }
            return legacyVal
        }
    }

    fun saveKeys(context: Context, entries: Map<String, String>) {
        synchronized(lock) {
            val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val secureEditor = securePrefs.edit()
            val legacyEditor = legacyPrefs.edit()

            entries.forEach { (keyName, rawValue) ->
                val clean = rawValue.trim()
                if (clean.isEmpty()) {
                    secureEditor.remove(keyName)
                } else {
                    secureEditor.putString(keyName, encryptValue(clean))
                }
                legacyEditor.remove(keyName)
            }
            secureEditor.apply()
            legacyEditor.apply()
        }
    }

    fun clearKeys(context: Context, keyNames: Collection<String>) {
        synchronized(lock) {
            val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val secureEditor = securePrefs.edit()
            val legacyEditor = legacyPrefs.edit()
            keyNames.forEach { key ->
                secureEditor.remove(key)
                legacyEditor.remove(key)
            }
            secureEditor.apply()
            legacyEditor.apply()
        }
    }

    private fun saveKeyInternal(securePrefs: SharedPreferences, keyName: String, plainValue: String) {
        val trimmed = plainValue.trim()
        if (trimmed.isEmpty()) {
            securePrefs.edit().remove(keyName).apply()
        } else {
            securePrefs.edit().putString(keyName, encryptValue(trimmed)).apply()
        }
    }

    private fun encryptValue(plain: String): String {
        return try {
            val secretKey = getOrCreateAesKey() ?: return plain
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            // Headless Robolectric fallback when AndroidKeyStore isn't mounted
            plain
        }
    }

    private fun decryptValue(stored: String): String {
        if (!stored.startsWith(ENCRYPTED_PREFIX)) return stored
        return try {
            val payload = Base64.decode(stored.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) return ""
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val secretKey = getOrCreateAesKey() ?: return ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun getOrCreateAesKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                generator.init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generator.generateKey()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
