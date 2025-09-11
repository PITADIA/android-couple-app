package com.love2loveapp.services.firebase

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.love2loveapp.model.AppConstants
import com.love2loveapp.services.cache.UserCacheManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * Service de gestion des données utilisateur Firebase
 * Responsable du CRUD des profils utilisateur, abonnements, et données partagées
 */
object FirebaseUserService {
    private const val TAG = "FirebaseUserService"

    // Firebase instances
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }

    // Observable state
    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Listeners
    private var subscriptionListener: ListenerRegistration? = null

    // === User Data Management ===
    fun saveUserData(user: AppUser) {
        val firebaseUser = auth.currentUser ?: run {
            _errorMessage.value = "Utilisateur non connecté"
            return
        }
        
        _isLoading.value = true
        val data = createUserDataMap(user, firebaseUser.uid, false)
        
        db.collection(AppConstants.Firestore.USERS).document(firebaseUser.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                _isLoading.value = false
                UserCacheManager.cacheUser(user)
                _currentUser.value = user
                Log.d(TAG, "✅ Données utilisateur sauvegardées")
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Erreur de sauvegarde: ${e.localizedMessage}"
            }
    }

    fun savePartialUserData(user: AppUser) {
        val firebaseUser = auth.currentUser ?: run {
            _errorMessage.value = "Utilisateur non connecté"
            return
        }
        
        val data = createUserDataMap(user, firebaseUser.uid, true)
        
        db.collection(AppConstants.Firestore.USERS).document(firebaseUser.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { 
                Log.d(TAG, "✅ Données partielles sauvegardées") 
            }
            .addOnFailureListener { e -> 
                _errorMessage.value = "Erreur de sauvegarde: ${e.localizedMessage}" 
            }
    }

    fun loadUserData(uid: String) {
        _isLoading.value = true
        db.collection(AppConstants.Firestore.USERS).document(uid).get()
            .addOnSuccessListener { doc ->
                _isLoading.value = false
                if (!doc.exists()) {
                    _currentUser.value = null
                    return@addOnSuccessListener
                }
                
                val user = parseUserFromDoc(doc)
                if (user != null) {
                    UserCacheManager.cacheUser(user)
                    _currentUser.value = user
                    startListeningForSubscriptionChanges()
                }
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.localizedMessage
            }
    }

    // === Profile Updates ===
    fun updateUserName(newName: String, completion: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return completion(false)
        
        db.collection(AppConstants.Firestore.USERS).document(user.uid)
            .update(mapOf("name" to newName, "updatedAt" to Timestamp(Date())))
            .addOnSuccessListener {
                _currentUser.value = _currentUser.value?.copy(name = newName)
                _currentUser.value?.let { UserCacheManager.cacheUser(it) }
                completion(true)
            }
            .addOnFailureListener { completion(false) }
    }

    fun updateRelationshipStartDate(date: Date, completion: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return completion(false)
        
        db.collection(AppConstants.Firestore.USERS).document(user.uid)
            .update(mapOf("relationshipStartDate" to Timestamp(date), "updatedAt" to Timestamp(Date())))
            .addOnSuccessListener {
                _currentUser.value = _currentUser.value?.copy(relationshipStartDate = date)
                _currentUser.value?.let { UserCacheManager.cacheUser(it) }
                completion(true)
            }
            .addOnFailureListener { completion(false) }
    }

    fun updateUserLocation(location: UserLocation, completion: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return completion(false)
        
        val data = mapOf(
            "currentLocation" to mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "address" to location.address,
                "city" to location.city,
                "country" to location.country,
                "lastUpdated" to Timestamp(location.lastUpdated)
            ),
            "updatedAt" to Timestamp(Date())
        )
        
        db.collection(AppConstants.Firestore.USERS).document(user.uid).update(data)
            .addOnSuccessListener {
                val cur = _currentUser.value
                if (cur != null) {
                    _currentUser.value = cur.copy(currentLocation = location)
                    UserCacheManager.cacheUser(_currentUser.value!!)
                }
                completion(true)
            }
            .addOnFailureListener { completion(false) }
    }

    // === Profile Image Management ===
    fun updateProfileImage(bitmap: Bitmap, completion: (Boolean, String?) -> Unit) {
        val me = auth.currentUser ?: run { completion(false, null); return }
        
        uploadProfileImageInternal(me.uid, bitmap) { success, url ->
            if (!success || url == null) return@uploadProfileImageInternal completion(false, null)
            
            val cur = _currentUser.value
            if (cur != null) {
                val updated = cur.copy(profileImageURL = url, profileImageUpdatedAt = Date())
                _currentUser.value = updated
                UserCacheManager.cacheUser(updated)
                saveUserData(updated)
            }
            completion(true, url)
        }
    }

    private fun uploadProfileImageInternal(uid: String, bitmap: Bitmap, completion: (Boolean, String?) -> Unit) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, AppConstants.Cache.IMAGE_CACHE_QUALITY, baos)
        val data = baos.toByteArray()
        val path = "profile_images/$uid/profile.jpg"
        val ref = storage.reference.child(path)
        val meta = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploadedBy", uid)
            .build()

        ref.putBytes(data, meta)
            .addOnFailureListener { e -> 
                Log.e(TAG, "Upload échec", e)
                completion(false, null) 
            }
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { uri -> completion(true, uri.toString()) }
                    .addOnFailureListener { completion(false, null) }
            }
    }

    // === Subscription Management ===
    fun updateSubscriptionStatus(isSubscribed: Boolean) {
        val user = auth.currentUser ?: return
        
        db.collection(AppConstants.Firestore.USERS).document(user.uid)
            .update(
                mapOf(
                    "isSubscribed" to isSubscribed,
                    "subscriptionDate" to if (isSubscribed) Timestamp(Date()) else FieldValue.delete(),
                    "updatedAt" to Timestamp(Date())
                )
            )
            .addOnSuccessListener {
                _currentUser.value = _currentUser.value?.copy(isSubscribed = isSubscribed)
                Log.d(TAG, "✅ Statut abonnement mis à jour")
            }
            .addOnFailureListener { e -> 
                _errorMessage.value = "Erreur abonnement: ${e.localizedMessage}" 
            }
    }

    fun startListeningForSubscriptionChanges() {
        val user = auth.currentUser ?: return
        subscriptionListener?.remove()
        
        subscriptionListener = db.collection(AppConstants.Firestore.USERS).document(user.uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e(TAG, "Listener abonnement erreur", e)
                    return@addSnapshotListener
                }
                
                val data = snap?.data ?: return@addSnapshotListener
                val isSubscribed = data["isSubscribed"] as? Boolean ?: false
                val type = data["subscriptionType"] as? String
                
                var updated = _currentUser.value ?: return@addSnapshotListener
                if (updated.isSubscribed != isSubscribed) {
                    updated = updated.copy(isSubscribed = isSubscribed)
                    if (type == "shared_from_partner") {
                        updated = updated.copy(
                            subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String,
                            subscriptionInheritedAt = (data["subscriptionSharedAt"] as? Timestamp)?.toDate()
                        )
                    }
                    _currentUser.value = updated
                    Log.d(TAG, "🔥 Abonnement mis à jour localement: $isSubscribed")
                }
            }
    }

    fun stopListeningForSubscriptionChanges() {
        subscriptionListener?.remove()
        subscriptionListener = null
    }

    // === Helper Methods ===
    private fun createUserDataMap(user: AppUser, uid: String, isPartial: Boolean): HashMap<String, Any> {
        val data = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "birthDate" to Timestamp(user.birthDate),
            "relationshipGoals" to user.relationshipGoals,
            "relationshipDuration" to user.relationshipDuration.raw,
            "partnerCode" to (user.partnerCode ?: ""),
            "partnerId" to (user.partnerId ?: ""),
            "isSubscribed" to user.isSubscribed,
            "googleUserID" to uid,
            "lastLoginDate" to Timestamp(Date()),
            "updatedAt" to Timestamp(Date()),
            "onboardingInProgress" to isPartial,
            "languageCode" to (user.languageCode ?: "fr"),
            "dailyQuestionMaxDayReached" to user.dailyQuestionMaxDayReached,
            "dailyChallengeMaxDayReached" to user.dailyChallengeMaxDayReached
        )
        
        // Optional fields
        user.relationshipImprovement?.let { data["relationshipImprovement"] = it }
        user.questionMode?.let { data["questionMode"] = it }
        user.relationshipStartDate?.let { data["relationshipStartDate"] = Timestamp(it) }
        user.profileImageURL?.let { data["profileImageURL"] = it }
        user.profileImageUpdatedAt?.let { data["profileImageUpdatedAt"] = Timestamp(it) }
        user.partnerConnectedAt?.let { data["partnerConnectedAt"] = Timestamp(it) }
        user.subscriptionInheritedFrom?.let { data["subscriptionInheritedFrom"] = it }
        user.subscriptionInheritedAt?.let { data["subscriptionInheritedAt"] = Timestamp(it) }
        user.connectedPartnerCode?.let { data["connectedPartnerCode"] = it }
        user.connectedPartnerId?.let { data["connectedPartnerId"] = it }
        user.connectedAt?.let { data["connectedAt"] = Timestamp(it) }
        user.dailyQuestionFirstAccessDate?.let { data["dailyQuestionFirstAccessDate"] = Timestamp(it) }
        user.dailyChallengeFirstAccessDate?.let { data["dailyChallengeFirstAccessDate"] = Timestamp(it) }
        
        user.currentLocation?.let { loc ->
            data["currentLocation"] = mapOf(
                "latitude" to loc.latitude,
                "longitude" to loc.longitude,
                "address" to loc.address,
                "city" to loc.city,
                "country" to loc.country,
                "lastUpdated" to Timestamp(loc.lastUpdated)
            )
        }
        
        return data
    }

    private fun parseUserFromDoc(doc: DocumentSnapshot): AppUser? {
        val d = doc.data ?: return null
        return AppUser(
            id = (d["id"] as? String) ?: doc.id,
            name = (d["name"] as? String) ?: "",
            birthDate = (d["birthDate"] as? Timestamp)?.toDate() ?: Date(),
            relationshipGoals = (d["relationshipGoals"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            relationshipDuration = RelationshipDuration.fromRaw((d["relationshipDuration"] as? String) ?: ""),
            relationshipImprovement = d["relationshipImprovement"] as? String,
            questionMode = d["questionMode"] as? String,
            partnerCode = d["partnerCode"] as? String,
            partnerId = d["partnerId"] as? String,
            partnerConnectedAt = (d["partnerConnectedAt"] as? Timestamp)?.toDate(),
            subscriptionInheritedFrom = d["subscriptionSharedFrom"] as? String,
            subscriptionInheritedAt = (d["subscriptionInheritedAt"] as? Timestamp)?.toDate(),
            connectedPartnerCode = d["connectedPartnerCode"] as? String,
            connectedPartnerId = d["connectedPartnerId"] as? String,
            connectedAt = (d["connectedAt"] as? Timestamp)?.toDate(),
            isSubscribed = d["isSubscribed"] as? Boolean ?: false,
            onboardingInProgress = d["onboardingInProgress"] as? Boolean ?: false,
            relationshipStartDate = (d["relationshipStartDate"] as? Timestamp)?.toDate(),
            profileImageURL = d["profileImageURL"] as? String,
            profileImageUpdatedAt = (d["profileImageUpdatedAt"] as? Timestamp)?.toDate(),
            currentLocation = parseUserLocation(d["currentLocation"] as? Map<String, Any?>),
            languageCode = d["languageCode"] as? String,
            dailyQuestionFirstAccessDate = (d["dailyQuestionFirstAccessDate"] as? Timestamp)?.toDate(),
            dailyQuestionMaxDayReached = (d["dailyQuestionMaxDayReached"] as? Number)?.toInt() ?: 0,
            dailyChallengeFirstAccessDate = (d["dailyChallengeFirstAccessDate"] as? Timestamp)?.toDate(),
            dailyChallengeMaxDayReached = (d["dailyChallengeMaxDayReached"] as? Number)?.toInt() ?: 0
        )
    }

    private fun parseUserLocation(map: Map<String, Any?>?): UserLocation? {
        if (map == null) return null
        val lat = (map["latitude"] as? Number)?.toDouble() ?: return null
        val lng = (map["longitude"] as? Number)?.toDouble() ?: return null
        return UserLocation(
            latitude = lat,
            longitude = lng,
            address = map["address"] as? String,
            city = map["city"] as? String,
            country = map["country"] as? String,
            lastUpdated = (map["lastUpdated"] as? Timestamp)?.toDate() ?: Date()
        )
    }

    fun forceRefreshUserData() {
        auth.currentUser?.uid?.let { loadUserData(it) }
    }
}

// === Data Models ===
data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdated: Date = Date()
)

enum class RelationshipDuration(val raw: String) {
    NOT_IN_RELATIONSHIP("notInRelationship"),
    NONE("none");
    companion object { 
        fun fromRaw(raw: String): RelationshipDuration = 
            values().firstOrNull { it.raw == raw } ?: NOT_IN_RELATIONSHIP 
    }
}

data class AppUser(
    val id: String,
    var name: String,
    val birthDate: Date,
    val relationshipGoals: List<String>,
    val relationshipDuration: RelationshipDuration,
    val relationshipImprovement: String? = null,
    val questionMode: String? = null,
    val partnerCode: String? = null,
    val partnerId: String? = null,
    val partnerConnectedAt: Date? = null,
    val subscriptionInheritedFrom: String? = null,
    val subscriptionInheritedAt: Date? = null,
    val connectedPartnerCode: String? = null,
    val connectedPartnerId: String? = null,
    val connectedAt: Date? = null,
    var isSubscribed: Boolean = false,
    val onboardingInProgress: Boolean = false,
    val relationshipStartDate: Date? = null,
    val profileImageURL: String? = null,
    val profileImageUpdatedAt: Date? = null,
    val currentLocation: UserLocation? = null,
    val languageCode: String? = null,
    val dailyQuestionFirstAccessDate: Date? = null,
    val dailyQuestionMaxDayReached: Int = 0,
    val dailyChallengeFirstAccessDate: Date? = null,
    val dailyChallengeMaxDayReached: Int = 0
)
