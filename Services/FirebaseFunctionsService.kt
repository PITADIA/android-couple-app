package com.love2loveapp.services.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.love2loveapp.model.AppConstants

/**
 * Service pour les appels Firebase Functions
 * Responsable des interactions avec les Cloud Functions (partenaires, sync, etc.)
 */
object FirebaseFunctionsService {
    private const val TAG = "FirebaseFunctionsService"

    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // === Partner Management ===
    fun getPartnerInfo(partnerId: String, completion: (AppUser?) -> Unit) {
        if (auth.currentUser == null) {
            completion(null)
            return
        }

        functions.getHttpsCallable("getPartnerInfo")
            .call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res: HttpsCallableResult ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(null)
                val success = root["success"] as? Boolean ?: false
                if (!success) return@addOnSuccessListener completion(null)
                
                val info = root["partnerInfo"] as? Map<*, *> ?: return@addOnSuccessListener completion(null)
                val user = AppUser(
                    id = partnerId,
                    name = info["name"] as? String ?: "Partenaire",
                    birthDate = java.util.Date(),
                    relationshipGoals = emptyList(),
                    relationshipDuration = RelationshipDuration.NONE,
                    relationshipImprovement = null,
                    questionMode = null,
                    partnerCode = null,
                    partnerId = null,
                    partnerConnectedAt = null,
                    subscriptionInheritedFrom = info["subscriptionSharedFrom"] as? String,
                    subscriptionInheritedAt = null,
                    connectedPartnerCode = null,
                    connectedPartnerId = null,
                    connectedAt = null,
                    isSubscribed = info["isSubscribed"] as? Boolean ?: false,
                    onboardingInProgress = false,
                    relationshipStartDate = null,
                    profileImageURL = info["profileImageURL"] as? String,
                    currentLocation = null
                )
                completion(user)
            }
            .addOnFailureListener { 
                Log.e(TAG, "Erreur getPartnerInfo", it)
                completion(null) 
            }
    }

    // === Data Synchronization ===
    fun syncPartnerJournalEntries(partnerId: String, completion: (Boolean, String?) -> Unit) {
        if (auth.currentUser == null) {
            return completion(false, "Utilisateur non connecté")
        }
        
        functions.getHttpsCallable("syncPartnerJournalEntries")
            .call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(false, "Réponse invalide")
                val ok = root["success"] as? Boolean ?: false
                val msg = root["message"] as? String
                completion(ok, msg ?: if (ok) "Synchronisation terminée" else "Erreur inconnue")
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Erreur syncPartnerJournalEntries", e)
                completion(false, e.localizedMessage) 
            }
    }

    fun syncPartnerFavorites(partnerId: String, completion: (Boolean, String?) -> Unit) {
        if (auth.currentUser == null) {
            return completion(false, "Utilisateur non connecté")
        }
        
        functions.getHttpsCallable("syncPartnerFavorites")
            .call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(false, "Réponse invalide")
                val ok = root["success"] as? Boolean ?: false
                val msg = if (ok) "Synchronisation réussie" else root["message"] as? String ?: "Erreur inconnue"
                completion(ok, msg)
            }
            .addOnFailureListener { e -> 
                Log.e(TAG, "Erreur syncPartnerFavorites", e)
                completion(false, e.localizedMessage) 
            }
    }

    // === Daily Questions & Challenges ===
    fun generateDailyQuestion(
        coupleId: String,
        userId: String,
        questionDay: Int,
        timezone: String,
        completion: (Boolean, Map<String, Any>?) -> Unit
    ) {
        val payload = mapOf(
            "coupleId" to coupleId,
            "userId" to userId,
            "questionDay" to questionDay,
            "timezone" to timezone
        )
        
        functions.getHttpsCallable("generateDailyQuestion")
            .call(payload)
            .addOnSuccessListener { result ->
                val data = result.data as? Map<*, *> ?: emptyMap<String, Any>()
                val success = (data["success"] as? Boolean) == true
                @Suppress("UNCHECKED_CAST")
                completion(success, data as? Map<String, Any>)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur generateDailyQuestion", e)
                completion(false, null)
            }
    }

    fun submitDailyQuestionResponse(
        questionId: String,
        responseText: String,
        userName: String,
        completion: (Boolean, String?) -> Unit
    ) {
        functions.getHttpsCallable("submitDailyQuestionResponse")
            .call(mapOf(
                "questionId" to questionId,
                "responseText" to responseText,
                "userName" to userName
            ))
            .addOnSuccessListener { res ->
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                val success = (data["success"] as? Boolean) == true
                val message = data["message"] as? String
                completion(success, message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur submitDailyQuestionResponse", e)
                completion(false, e.localizedMessage)
            }
    }

    fun getOrCreateDailyQuestionSettings(
        coupleId: String,
        timezone: String,
        completion: (Boolean, Map<String, Any>?) -> Unit
    ) {
        functions.getHttpsCallable("getOrCreateDailyQuestionSettings")
            .call(mapOf(
                "coupleId" to coupleId,
                "timezone" to timezone
            ))
            .addOnSuccessListener { res ->
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                val success = (data["success"] as? Boolean) == true
                @Suppress("UNCHECKED_CAST")
                completion(success, data as? Map<String, Any>)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur getOrCreateDailyQuestionSettings", e)
                completion(false, null)
            }
    }

    fun migrateDailyQuestionResponses(
        coupleId: String,
        completion: (Boolean, String?) -> Unit
    ) {
        functions.getHttpsCallable("migrateDailyQuestionResponses")
            .call(mapOf("coupleId" to coupleId))
            .addOnSuccessListener { res ->
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                val success = (data["success"] as? Boolean) == true
                val message = data["message"] as? String
                completion(success, message ?: if (success) "Migration réussie" else "Erreur migration")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur migrateDailyQuestionResponses", e)
                completion(false, e.localizedMessage)
            }
    }

    // === Account Management ===
    fun deleteUserAccount(
        userId: String,
        googleIdToken: String?,
        completion: (Boolean, String?) -> Unit
    ) {
        val payload = mutableMapOf<String, Any>("userId" to userId)
        googleIdToken?.let { payload["googleIdToken"] = it }
        
        functions.getHttpsCallable("deleteUserAccount")
            .call(payload)
            .addOnSuccessListener { res ->
                val data = res.data as? Map<*, *> ?: emptyMap<String, Any>()
                val success = (data["success"] as? Boolean) == true
                val message = data["message"] as? String
                completion(success, message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur deleteUserAccount", e)
                completion(false, e.localizedMessage)
            }
    }

    // === Utility Functions ===
    fun testCloudFunction(completion: (Boolean, String?) -> Unit) {
        functions.getHttpsCallable("testFunction")
            .call()
            .addOnSuccessListener { res ->
                val data = res.data as? Map<*, *>
                val message = data?.get("message") as? String ?: "Test réussi"
                completion(true, message)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Erreur testCloudFunction", e)
                completion(false, e.localizedMessage)
            }
    }
}
