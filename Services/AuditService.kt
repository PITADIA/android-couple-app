@file:Suppress("unused")
package com.love2love.security

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Date

/**
 * Service d'audit et de logs sécurisés pour Android/Kotlin (Firestore + Firebase Auth)
 *\n * Remarques localisation : ce service ne contient pas de chaînes UI. Pour afficher un texte localisé
 * dans votre application, utilisez le mécanisme Android standard :
 *   context.getString(R.string.votre_cle)
 * ou en Compose :
 *   stringResource(id = R.string.votre_cle)
 */
object AuditService {

    private const val TAG = "AuditService"
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    // --- Types d'événements ---
    enum class AuditEventType(val raw: String) {
        PartnerConnection("partner_connection"),
        PartnerDisconnection("partner_disconnection"),
        AccessAttempt("access_attempt"),
        AccessDenied("access_denied"),
        DataEncryption("data_encryption"),
        DataDecryption("data_decryption"),
        LoginSuccess("login_success"),
        LoginFailure("login_failure"),
        SensitiveDataAccess("sensitive_data_access"),
        SecurityViolation("security_violation")
    }

    enum class Severity(val raw: String) { LOW("low"), MEDIUM("medium"), HIGH("high"), CRITICAL("critical") }

    data class AuditEvent(
        val type: AuditEventType,
        val userId: String?,
        val details: Map<String, Any?> = emptyMap(),
        val timestamp: Date = Date(),
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val severity: Severity
    )

    /** Logger un événement d'audit */
    fun logEvent(event: AuditEvent) {
        // Log local pour debug
        Log.d(TAG, "${event.type.raw} - severity=${event.severity.raw}")

        // Préparer les données pour Firestore (sans valeurs nulles)
        val eventData = mutableMapOf<String, Any>(
            "type" to event.type.raw,
            "timestamp" to Timestamp(event.timestamp),
            "severity" to event.severity.raw,
            "details" to normalizeForFirestore(event.details)
        )

        // Ajouter userId de manière sécurisée (hash par défaut)
        event.userId?.let { uid ->
            eventData["userId"] = hashUserId(uid)
            eventData["hashedUser"] = true
        }
        event.ipAddress?.let { eventData["ipAddress"] = it }
        event.userAgent?.let { eventData["userAgent"] = it }

        db.collection("audit_events")
            .add(eventData)
            .addOnSuccessListener { Log.d(TAG, "Événement d'audit sauvegardé: ${it.id}") }
            .addOnFailureListener { e -> Log.e(TAG, "Erreur sauvegarde audit", e) }
    }

    // --- Méthodes spécialisées ---

    /** Logger une connexion partenaire */
    fun logPartnerConnection(userId: String, partnerId: String, method: String) {
        val event = AuditEvent(
            type = AuditEventType.PartnerConnection,
            userId = userId,
            details = mapOf(
                "partnerId" to hashUserId(partnerId),
                "connectionMethod" to method,
                "action" to "successful_connection"
            ),
            severity = Severity.MEDIUM
        )
        logEvent(event)
    }

    /** Logger une déconnexion partenaire */
    fun logPartnerDisconnection(userId: String, partnerId: String, reason: String) {
        val event = AuditEvent(
            type = AuditEventType.PartnerDisconnection,
            userId = userId,
            details = mapOf(
                "partnerId" to hashUserId(partnerId),
                "disconnectionReason" to reason,
                "action" to "partner_disconnected"
            ),
            severity = Severity.MEDIUM
        )
        logEvent(event)
    }

    /** Logger un accès refusé */
    fun logAccessDenied(userId: String?, resource: String, reason: String) {
        val event = AuditEvent(
            type = AuditEventType.AccessDenied,
            userId = userId,
            details = mapOf(
                "resource" to resource,
                "denialReason" to reason,
                "action" to "access_blocked"
            ),
            severity = Severity.HIGH
        )
        logEvent(event)
    }

    /** Logger un accès aux données sensibles */
    fun logSensitiveDataAccess(userId: String, dataType: String, action: String) {
        val event = AuditEvent(
            type = AuditEventType.SensitiveDataAccess,
            userId = userId,
            details = mapOf(
                "dataType" to dataType,
                "accessAction" to action,
                "timestamp" to Date().toInstant().toString()
            ),
            severity = Severity.MEDIUM
        )
        logEvent(event)
    }

    /** Logger une violation de sécurité */
    fun logSecurityViolation(userId: String?, violationType: String, details: Map<String, Any?>) {
        val merged = details.toMutableMap().apply {
            put("violationType", violationType)
            put("severity", Severity.CRITICAL.raw)
        }
        val event = AuditEvent(
            type = AuditEventType.SecurityViolation,
            userId = userId,
            details = merged,
            severity = Severity.CRITICAL
        )
        logEvent(event)
    }

    // --- Utilitaires de sécurité ---

    /** Hasher un userId pour la confidentialité (SHA-256, hex) */
    fun hashUserId(userId: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(userId.toByteArray())
        return buildString(digest.size * 2) {
            for (b in digest) append(String.format("%02x", b))
        }
    }

    /** Obtenir le userId courant via FirebaseAuth */
    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // --- Monitoring & Alertes ---

    /**
     * Version coroutine (recommandée) : true si > 5 événements "high" en 24h
     */
    suspend fun checkSuspiciousActivity(userId: String): Boolean = withContext(Dispatchers.IO) {
        val oneDayAgo = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val hashed = hashUserId(userId)
        try {
            val snap = db.collection("audit_events")
                .whereEqualTo("userId", hashed)
                .whereEqualTo("severity", Severity.HIGH.raw)
                .whereGreaterThan("timestamp", Timestamp(oneDayAgo))
                .get()
                .await()
            val count = snap.size()
            val suspicious = count > 5
            if (suspicious) Log.w(TAG, "Activité suspecte détectée pour l'utilisateur (hashed)")
            suspicious
        } catch (e: Exception) {
            Log.e(TAG, "Erreur vérification activité", e)
            false
        }
    }

    /**
     * Version callback (si vous n'utilisez pas les coroutines)
     */
    fun checkSuspiciousActivity(userId: String, onResult: (Boolean) -> Unit) {
        val oneDayAgo = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        val hashed = hashUserId(userId)
        db.collection("audit_events")
            .whereEqualTo("userId", hashed)
            .whereEqualTo("severity", Severity.HIGH.raw)
            .whereGreaterThan("timestamp", Timestamp(oneDayAgo))
            .get()
            .addOnSuccessListener { snap ->
                val suspicious = snap.size() > 5
                if (suspicious) Log.w(TAG, "Activité suspecte détectée pour l'utilisateur (hashed)")
                onResult(suspicious)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur vérification activité", e)
                onResult(false)
            }
    }

    // --- Extensions pratiques (mêmes intentions que l'extension Swift) ---

    /** Logger rapidement un accès aux données de géolocalisation */
    fun logLocationAccess(action: String) {
        val uid = getCurrentUserId() ?: return
        logSensitiveDataAccess(uid, dataType = "geolocation", action = action)
    }

    /** Logger rapidement un accès aux photos */
    fun logPhotoAccess(action: String) {
        val uid = getCurrentUserId() ?: return
        logSensitiveDataAccess(uid, dataType = "profile_photo", action = action)
    }

    /** Logger rapidement un chiffrement/déchiffrement */
    fun logEncryptionActivity(type: String, success: Boolean) {
        val uid = getCurrentUserId() ?: return
        val eventType = if (type.contains("encrypt", ignoreCase = true))
            AuditEventType.DataEncryption else AuditEventType.DataDecryption
        val severity = if (success) Severity.LOW else Severity.MEDIUM

        val event = AuditEvent(
            type = eventType,
            userId = uid,
            details = mapOf(
                "encryptionType" to type,
                "success" to success,
                "timestamp" to Date().toInstant().toString()
            ),
            severity = severity
        )
        logEvent(event)
    }

    // --- Normalisation Firestore ---

    /**
     * Firestore accepte String/Boolean/Number/Map/List/Timestamp/GeoPoint/DocumentReference.
     * Cette fonction normalise récursivement les Map/List et convertit Date en Timestamp.
     */
    private fun normalizeForFirestore(value: Any?): Any? = when (value) {
        null -> null
        is Date -> Timestamp(value)
        is Map<*, *> -> value.entries.associate { (k, v) ->
            k.toString() to normalizeForFirestore(v)
        }
        is List<*> -> value.map { normalizeForFirestore(it) }
        else -> value
    }
}
