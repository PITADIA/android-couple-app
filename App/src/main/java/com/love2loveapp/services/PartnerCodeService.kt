package com.love2loveapp.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.random.Random

/**
 * Service Android pour le système de code partenaire Love2Love
 * Équivalent du PartnerCodeService iOS avec toute la logique de sécurité
 */
class PartnerCodeService private constructor() {

    companion object {
        @JvmStatic
        val shared = PartnerCodeService()
    }

    // Firebase services
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val analytics = Firebase.analytics

    // États observables
    private val _state = MutableStateFlow(PartnerCodeState())
    val state: StateFlow<PartnerCodeState> = _state.asStateFlow()

    data class PartnerCodeState(
        val generatedCode: String? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val isConnected: Boolean = false,
        val partnerInfo: PartnerInfo? = null
    )

    data class PartnerInfo(
        val id: String,
        val name: String,
        val connectedAt: Date,
        val isSubscribed: Boolean
    )

    /**
     * Génère un code partenaire unique avec conformité Apple (24h max)
     */
    suspend fun generatePartnerCode(): String? = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            Log.d("PartnerCodeService", "🔄 Génération code partenaire...")

            val currentUser = auth.currentUser 
                ?: throw Exception("Utilisateur non connecté")

            // 🛡️ CONFORMITÉ APPLE : Vérifier si code récent (< 24h) existe
            val yesterday = Date(System.currentTimeMillis() - 86400000) // 24h en ms
            val recentCodesQuery = db.collection("partnerCodes")
                .whereEqualTo("userId", currentUser.uid)
                .whereGreaterThan("createdAt", Timestamp(yesterday))
                .whereEqualTo("isActive", true)
                .get()
                .await()

            // Si code récent existe, le retourner
            if (!recentCodesQuery.isEmpty) {
                val existingCode = recentCodesQuery.documents.first().id
                Log.d("PartnerCodeService", "✅ Code récent trouvé: [CODE_MASQUÉ]")
                
                _state.value = _state.value.copy(
                    generatedCode = existingCode,
                    isLoading = false
                )
                return@withContext existingCode
            }

            // Sinon, générer un nouveau code unique
            var attempts = 0
            var code: String
            var isUnique: Boolean

            do {
                code = String.format("%08d", Random.nextInt(10000000, 99999999))

                // Vérifier unicité
                val existingDoc = db.collection("partnerCodes")
                    .document(code)
                    .get()
                    .await()

                isUnique = !existingDoc.exists()
                attempts++

                if (attempts > 10) {
                    throw Exception("Impossible de générer un code unique après 10 tentatives")
                }
            } while (!isUnique)

            Log.d("PartnerCodeService", "🔥 Code unique trouvé après $attempts tentatives")

            // Créer le code avec expiration 24h
            val expirationDate = Date(System.currentTimeMillis() + 86400000)
            db.collection("partnerCodes").document(code).set(
                hashMapOf(
                    "userId" to currentUser.uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "expiresAt" to Timestamp(expirationDate),
                    "isActive" to true,
                    "connectedPartnerId" to null,
                    "rotationReason" to "apple_compliance"
                )
            ).await()

            Log.d("PartnerCodeService", "✅ Code créé avec succès: [CODE_MASQUÉ]")

            _state.value = _state.value.copy(
                generatedCode = code,
                isLoading = false
            )

            // Analytics
            analytics.logEvent("code_partenaire_genere") {
                param("method", "auto_generation")
            }

            return@withContext code

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur génération code", e)
            _state.value = _state.value.copy(
                errorMessage = "Erreur génération: ${e.message}",
                isLoading = false
            )
            return@withContext null
        }
    }

    /**
     * Connexion avec code partenaire via Cloud Function sécurisée
     */
    suspend fun connectWithPartnerCode(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            Log.d("PartnerCodeService", "🔗 Connexion avec code partenaire...")

            // Analytics tracking
            analytics.logEvent("partner_connect_start") {
                param("source", "onboarding")
            }

            // Appel Cloud Function sécurisé
            val result = functions.getHttpsCallable("connectPartners")
                .call(hashMapOf("partnerCode" to code))
                .await()

            val data = result.data as? Map<String, Any>
                ?: throw Exception("Réponse Cloud Function invalide")

            val success = data["success"] as? Boolean
                ?: throw Exception("Connexion échouée")

            if (!success) {
                val message = data["message"] as? String ?: "Connexion refusée"
                throw Exception(message)
            }

            val partnerName = data["partnerName"] as? String ?: "Partenaire"
            val subscriptionInherited = data["subscriptionInherited"] as? Boolean ?: false

            Log.d("PartnerCodeService", "✅ Connexion réussie avec $partnerName")
            Log.d("PartnerCodeService", "📱 Abonnement hérité: $subscriptionInherited")

            // Mise à jour état
            _state.value = _state.value.copy(
                isConnected = true,
                partnerInfo = PartnerInfo(
                    id = "", // L'ID sera récupéré plus tard si nécessaire
                    name = partnerName,
                    connectedAt = Date(),
                    isSubscribed = subscriptionInherited
                ),
                isLoading = false
            )

            // Analytics success
            analytics.logEvent("partenaire_connecte") {
                param("inherited_subscription", subscriptionInherited.toString())
                param("context", "onboarding")
            }

            return@withContext true

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur connexion partenaire", e)
            
            val errorMessage = when {
                e.message?.contains("not-found") == true -> "Code partenaire invalide"
                e.message?.contains("failed-precondition") == true -> "Code partenaire inactif"
                e.message?.contains("deadline-exceeded") == true -> "Code partenaire expiré (24h max)"
                e.message?.contains("invalid-argument") == true -> "Vous ne pouvez pas utiliser votre propre code"
                e.message?.contains("already-exists") == true -> "Ce code est déjà utilisé"
                else -> e.message ?: "Erreur de connexion"
            }
            
            _state.value = _state.value.copy(
                errorMessage = errorMessage,
                isLoading = false
            )
            return@withContext false
        }
    }

    /**
     * Vérifier s'il y a déjà une connexion partenaire existante
     */
    suspend fun checkExistingConnection() = withContext(Dispatchers.IO) {
        try {
            val currentUser = auth.currentUser ?: return@withContext
            Log.d("PartnerCodeService", "🔍 Vérification connexion existante...")

            val userDoc = db.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            val userData = userDoc.data
            val partnerId = userData?.get("partnerId") as? String

            if (!partnerId.isNullOrEmpty()) {
                Log.d("PartnerCodeService", "✅ Partenaire déjà connecté: [ID_MASQUÉ]")
                
                try {
                    // 🔥 Récupération infos partenaire via Cloud Function sécurisée (comme iOS)
                    val result = functions.getHttpsCallable("getPartnerInfo")
                        .call(hashMapOf("partnerId" to partnerId))
                        .await()

                    val resultData = result.data as? Map<String, Any>
                    val success = resultData?.get("success") as? Boolean ?: false

                    if (success) {
                        val partnerData = resultData?.get("partnerInfo") as? Map<String, Any>
                        val connectedAt = userData["partnerConnectedAt"] as? Timestamp

                        if (partnerData != null && connectedAt != null) {
                            val partnerName = partnerData["name"] as? String ?: "Partenaire"
                            val isSubscribed = partnerData["isSubscribed"] as? Boolean ?: false

                            _state.value = _state.value.copy(
                                isConnected = true,
                                partnerInfo = PartnerInfo(
                                    id = partnerId,
                                    name = partnerName,
                                    connectedAt = connectedAt.toDate(),
                                    isSubscribed = isSubscribed
                                )
                            )
                            Log.d("PartnerCodeService", "✅ Connexion existante restaurée via Cloud Function")
                        }
                    } else {
                        Log.e("PartnerCodeService", "❌ Cloud Function getPartnerInfo échec")
                    }
                } catch (e: Exception) {
                    Log.e("PartnerCodeService", "❌ Erreur Cloud Function getPartnerInfo", e)
                }
            } else {
                Log.d("PartnerCodeService", "ℹ️ Aucune connexion partenaire existante")
            }

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur vérification connexion", e)
        }
    }

    /**
     * Validation d'un code avant connexion (optionnel)
     */
    suspend fun validatePartnerCode(code: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            Log.d("PartnerCodeService", "🔍 Validation code partenaire...")

            val result = functions.getHttpsCallable("validatePartnerCode")
                .call(hashMapOf("partnerCode" to code))
                .await()

            val data = result.data as? Map<String, Any>
                ?: return@withContext ValidationResult.error("Réponse invalide")

            val isValid = data["isValid"] as? Boolean ?: false
            val reason = data["reason"] as? String
            val message = data["message"] as? String

            return@withContext if (isValid) {
                ValidationResult.valid()
            } else {
                ValidationResult.invalid(reason ?: "Code invalide", message ?: "Code non valide")
            }

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur validation code", e)
            return@withContext ValidationResult.error(e.message ?: "Erreur de validation")
        }
    }

    /**
     * Partager le code partenaire via le système de partage Android
     */
    fun sharePartnerCode(context: Context, code: String) {
        try {
            val shareText = context.getString(
                com.love2loveapp.R.string.share_partner_code_message
            ).replace("{code}", code)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }

            val chooser = Intent.createChooser(shareIntent, "Partager le code partenaire")
            context.startActivity(chooser)

            // Analytics
            analytics.logEvent("code_partenaire_partage") {
                param("method", "system_share")
            }

            Log.d("PartnerCodeService", "📤 Code partenaire partagé")

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur partage code", e)
        }
    }

    /**
     * Déconnecter du partenaire via Cloud Function sécurisée
     */
    suspend fun disconnectPartner(): Boolean = withContext(Dispatchers.IO) {
        try {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            Log.d("PartnerCodeService", "💔 Début déconnexion partenaire...")

            // Analytics tracking
            analytics.logEvent("partner_disconnect_start") {
                param("source", "partner_management")
            }

            // Appel Cloud Function sécurisé
            val result = functions.getHttpsCallable("disconnectPartners")
                .call(emptyMap<String, Any>())
                .await()

            val data = result.data as? Map<String, Any>
                ?: throw Exception("Réponse Cloud Function invalide")

            val success = data["success"] as? Boolean
                ?: throw Exception("Déconnexion échouée")

            if (!success) {
                val message = data["message"] as? String ?: "Déconnexion refusée"
                throw Exception(message)
            }

            Log.d("PartnerCodeService", "✅ Déconnexion réussie")

            // Mise à jour état local
            _state.value = _state.value.copy(
                isConnected = false,
                partnerInfo = null,
                isLoading = false,
                errorMessage = null
            )

            // Nettoyage cache local (comme iOS)
            // UserDefaults.standard.removeObject(forKey: "lastCoupleId") équivalent Android
            // TODO: Ajouter nettoyage SharedPreferences si nécessaire

            // Analytics success
            analytics.logEvent("partenaire_deconnecte") {
                param("method", "cloud_function")
            }

            return@withContext true

        } catch (e: Exception) {
            Log.e("PartnerCodeService", "❌ Erreur déconnexion partenaire", e)
            
            val errorMessage = when {
                e.message?.contains("unauthenticated") == true -> "Vous devez être connecté"
                e.message?.contains("failed-precondition") == true -> "Aucun partenaire connecté"
                e.message?.contains("permission-denied") == true -> "Permission refusée"
                else -> e.message ?: "Erreur de déconnexion"
            }
            
            _state.value = _state.value.copy(
                errorMessage = errorMessage,
                isLoading = false
            )
            return@withContext false
        }
    }

    /**
     * Réinitialiser l'état du service
     */
    fun resetState() {
        _state.value = PartnerCodeState()
        Log.d("PartnerCodeService", "🔄 État du service réinitialisé")
    }

    // Résultats de validation
    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null,
        val message: String? = null
    ) {
        companion object {
            fun valid() = ValidationResult(isValid = true)
            fun invalid(reason: String, message: String) = ValidationResult(isValid = false, reason = reason, message = message)
            fun error(message: String) = ValidationResult(isValid = false, message = message)
        }
    }
}
