package com.love2loveapp.core.services.security

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.love2loveapp.model.AppConstants

/**
 * Service de chiffrement/déchiffrement de géolocalisation (client Android)
 * - AES/GCM/NoPadding, clé stockée dans l'Android Keystore
 * - Format Firestore hybride (chiffré + clair) pour rétrocompatibilité
 * - Bascule globale ENCRYPTION_DISABLED_FOR_REVIEW pour phases de review
 * - Inclut chiffrement de texte (messages) avec le même mécanisme
 */
object LocationEncryptionService {

    // ===== Configuration =====
    const val ENCRYPTION_DISABLED_FOR_REVIEW: Boolean = AppConstants.Security.ENCRYPTION_DISABLED_FOR_REVIEW
    const val CURRENT_VERSION: String = AppConstants.Security.CURRENT_ENCRYPTION_VERSION

    private const val TAG = "LocationEncryption"
    private const val ANDROID_KEYSTORE = AppConstants.Security.ANDROID_KEYSTORE
    private const val KEY_ALIAS = AppConstants.Security.KEY_ALIAS
    private const val TRANSFORMATION = AppConstants.Security.TRANSFORMATION
    private const val GCM_TAG_LENGTH = AppConstants.Security.GCM_TAG_LENGTH // bits
    private const val IV_LENGTH_BYTES = AppConstants.Security.IV_LENGTH_BYTES // recommandé pour GCM

    private val secureRandom = SecureRandom()

    // ===== Modèle de données =====
    data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val isEncrypted: Boolean,
        val version: String?
    ) {
        fun toLocation(): Location = Location("fused").apply {
            latitude = this@LocationData.latitude
            longitude = this@LocationData.longitude
        }
    }

    // ===== API publique : Lecture =====
    /**
     * Lecture hybride d'une localisation depuis une Map Firestore
     * - Priorité au champ chiffré (encryptedLocation + locationVersion)
     * - Fallback sur l'ancien format clair { location: { latitude, longitude } }
     */
    fun readLocation(firestoreData: Map<String, Any?>): LocationData? {
        val enc = firestoreData["encryptedLocation"] as? String
        val version = firestoreData["locationVersion"] as? String
        if (!enc.isNullOrBlank() && !version.isNullOrBlank()) {
            Log.d(TAG, "🔐 Lecture format chiffré v$version")
            decryptLocation(enc)?.let {
                return LocationData(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isEncrypted = true,
                    version = version
                )
            }
        }

        // Ancien format clair
        val legacyLoc = firestoreData["location"] as? Map<*, *>
        val lat = (legacyLoc?.get("latitude") as? Number)?.toDouble()
        val lon = (legacyLoc?.get("longitude") as? Number)?.toDouble()
        if (lat != null && lon != null) {
            Log.d(TAG, "📍 Lecture format legacy (non chiffré)")
            return LocationData(lat, lon, isEncrypted = false, version = "1.0")
        }

        Log.w(TAG, "❌ Format de localisation non reconnu")
        return null
    }

    // ===== API publique : Écriture =====
    /**
     * Écrit une localisation au format Firestore.
     * - En mode review (désactivé), on renvoie l'ancien format en clair + métadonnées.
     * - Sinon, on renvoie le format chiffré + un miroir legacy pour compatibilité.
     */
    fun writeLocation(location: Location, context: Context): Map<String, Any> {
        if (ENCRYPTION_DISABLED_FOR_REVIEW) {
            Log.w(TAG, "⚠️ Mode non-chiffré activé (review)")
            return mapOf(
                "location" to mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude
                ),
                "hasLocation" to true,
                "locationVersion" to "1.0-temp",
                "migrationStatus" to "unencrypted_temp",
                "clientVersion" to clientVersion(context)
            )
        }

        val encrypted = encryptLocation(location) ?: run {
            Log.e(TAG, "❌ Échec du chiffrement de la localisation")
            return emptyMap()
        }

        return mapOf(
            // Nouveau format chiffré
            "encryptedLocation" to encrypted,
            "locationVersion" to CURRENT_VERSION,
            "hasLocation" to true,
            "encryptedAt" to Date(),

            // Rétrocompatibilité
            "location" to mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
            ),

            // Métadonnées
            "migrationStatus" to "hybrid",
            "clientVersion" to clientVersion(context)
        )
    }

    /** Indique si une entrée (au format clair uniquement) doit être migrée vers le format chiffré. */
    fun needsMigration(firestoreData: Map<String, Any?>): Boolean =
        firestoreData["location"] != null && firestoreData["encryptedLocation"] == null

    /** Migrer une entrée existante vers le format chiffré, en place. */
    fun migrateEntry(firestoreData: MutableMap<String, Any?>, context: Context) {
        val loc = readLocation(firestoreData) ?: return
        if (!loc.isEncrypted) {
            val newFormat = writeLocation(loc.toLocation(), context)
            firestoreData.putAll(newFormat)
            firestoreData["migrationDate"] = Date()
            Log.i(TAG, "✅ Entrée migrée vers format chiffré")
        }
    }

    // ===== Chiffrement/Déchiffrement Localisation =====
    private fun encryptLocation(location: Location): String? {
        val payload = "${location.latitude},${location.longitude}".toByteArray()
        return encrypt(payload)
    }

    private fun decryptLocation(encrypted: String): Location? {
        val bytes = decryptToBytes(encrypted) ?: return null
        val text = bytes.toString(Charsets.UTF_8)
        val parts = text.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return Location("encrypted").apply {
            latitude = lat
            longitude = lon
        }
    }

    // ===== Chiffrement/Déchiffrement Texte (Phase 2) =====
    fun encryptText(text: String): String? {
        if (ENCRYPTION_DISABLED_FOR_REVIEW) {
            Log.w(TAG, "⚠️ Mode texte non-chiffré activé (review)")
            return text
        }
        return encrypt(text.toByteArray())
    }

    fun decryptText(encrypted: String): String? {
        if (ENCRYPTION_DISABLED_FOR_REVIEW) {
            Log.w(TAG, "⚠️ Mode déchiffrement non-chiffré activé (review)")
            return encrypted
        }
        val bytes = decryptToBytes(encrypted) ?: return null
        return bytes.toString(Charsets.UTF_8)
    }

    /**
     * Prépare un message pour Firestore (nouveau format chiffré + fallback legacy).
     */
    fun processMessageForStorage(text: String, context: Context): Map<String, Any> {
        if (ENCRYPTION_DISABLED_FOR_REVIEW) {
            return mapOf(
                "text" to text,
                "textVersion" to "1.0-temp",
                "migrationStatus" to "unencrypted_temp"
            )
        }
        val map = mutableMapOf<String, Any>(
            "text_legacy" to text,
            "migrationStatus" to "hybrid_text"
        )
        encryptText(text)?.let { enc ->
            map["encryptedText"] = enc
            map["textVersion"] = CURRENT_VERSION
        }
        return map
    }

    /** Lecture hybride d'un message depuis Firestore. */
    fun readMessageFromFirestore(data: Map<String, Any?>): String? {
        (data["encryptedText"] as? String)?.let { enc ->
            decryptText(enc)?.let { return it }
        }
        (data["text_legacy"] as? String)?.let { return it }
        (data["text"] as? String)?.let { return it }
        return null
    }

    // ===== Helpers de haut niveau =====
    fun processLocationForStorage(location: Location, context: Context): Map<String, Any> =
        writeLocation(location, context)

    fun extractLocationFromJournalData(journalData: Map<String, Any?>): Location? =
        readLocation(journalData)?.toLocation()

    // ===== Gestion de la clé (Android Keystore) =====
    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)

        // Optionnel : ne pas forcer StrongBox, pour compatibilité élargie
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { specBuilder.setIsStrongBoxBacked(false) } catch (_: Throwable) {}
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    private fun encrypt(plain: ByteArray): String? = try {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key) // IV aléatoire auto
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)

        val buffer = ByteBuffer.allocate(IV_LENGTH_BYTES + cipherText.size)
        buffer.put(iv)
        buffer.put(cipherText)
        Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Erreur chiffrement: ${e.message}", e)
        null
    }

    private fun decryptToBytes(encoded: String): ByteArray? = try {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        if (all.size <= IV_LENGTH_BYTES) return null
        val iv = all.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherBytes = all.copyOfRange(IV_LENGTH_BYTES, all.size)

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.doFinal(cipherBytes)
    } catch (e: Exception) {
        Log.e(TAG, "Erreur déchiffrement: ${e.message}", e)
        null
    }

    private fun clientVersion(context: Context): String = try {
        val pm = context.packageManager
        val pInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        pInfo.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}
