package com.love2loveapp.services.firebase

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.Timestamp
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.Date

/**
 * FirebaseService (Google Auth only)
 * - Auth: Google Sign-In -> FirebaseAuth
 * - Firestore: profil, onboarding, abonnement, localisation, etc.
 * - Storage: upload photo de profil
 * - Functions: getPartnerInfo, syncPartnerJournalEntries, syncPartnerFavorites
 *
 * ⚠️ Aucune dépendance à Apple. Ce module suppose **Google seulement**.
 */
object FirebaseServiceGoogle {
    private const val TAG = "FirebaseServiceGoogle"

    // --- Firebase ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val storage by lazy { FirebaseStorage.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance() }

    // --- Google Sign-In ---
    private var googleClient: GoogleSignInClient? = null

    fun initGoogleSignIn(context: Context) {
        if (googleClient != null) return
        // Récupère le client_id depuis strings.xml généré par google-services.json
        val webClientId = context.getString(
            context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        )
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleClient = GoogleSignIn.getClient(context, gso)
        Log.d(TAG, "🔐 GoogleSignIn initialisé")
    }

    fun getGoogleSignInIntent(context: Context): Intent {
        initGoogleSignIn(context)
        return googleClient!!.signInIntent
    }

    /**
     * À appeler depuis onActivityResult / ActivityResultLauncher callback.
     * Réalise le lien Google -> FirebaseAuth.
     */
    fun handleGoogleSignInResult(data: Intent?, onResult: (Boolean, String?) -> Unit) {
        val task: Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> =
            GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                onResult(false, "ID token Google manquant")
                return
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            _isLoading.value = true
            auth.signInWithCredential(credential)
                .addOnCompleteListener { t: Task<AuthResult> ->
                    _isLoading.value = false
                    if (t.isSuccessful) {
                        val res = t.result
                        val user = res?.user
                        if (user == null) { onResult(false, "Utilisateur Firebase introuvable"); return@addOnCompleteListener }
                        if (res.additionalUserInfo?.isNewUser == true) {
                            createEmptyUserProfile(uid = user.uid, email = user.email, name = user.displayName)
                        } else {
                            loadUserData(user.uid)
                        }
                        onResult(true, null)
                    } else {
                        onResult(false, t.exception?.localizedMessage ?: "Erreur de connexion")
                    }
                }
        } catch (e: ApiException) {
            onResult(false, e.localizedMessage)
        }
    }

    fun googleSignOut(context: Context, onComplete: (() -> Unit)? = null) {
        initGoogleSignIn(context)
        googleClient?.signOut()?.addOnCompleteListener { _ ->
            try { auth.signOut() } catch (_: Throwable) {}
            stopListeningForSubscriptionChanges()
            _isAuthenticated.value = false
            _currentUser.value = null
            _errorMessage.value = null
            onComplete?.let { it() }
        }
    }

    // --- State exposé ---
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUser = MutableStateFlow<AppUser?>(null)
    val currentUser: StateFlow<AppUser?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // --- Interne ---
    private var subscriptionListener: ListenerRegistration? = null
    private var isOnboardingInProgress: Boolean = false

    // --- Auth listener ---
    fun setupAuthListener() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                loadUserData(user.uid)
            } else {
                _isAuthenticated.value = false
                _currentUser.value = null
                _isLoading.value = false
            }
        }
    }

    // --- Onboarding flags ---
    fun startOnboardingProcess() { isOnboardingInProgress = true }
    fun completeOnboardingProcess() { isOnboardingInProgress = false }

    // --- User Data Management ---
    fun savePartialUserData(user: AppUser) {
        val firebaseUser = auth.currentUser ?: run { _errorMessage.value = "Utilisateur non connecté"; return }
        val data = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "birthDate" to Timestamp(user.birthDate),
            "relationshipGoals" to user.relationshipGoals,
            "relationshipDuration" to user.relationshipDuration.raw,
            "partnerCode" to (user.partnerCode ?: ""),
            "partnerId" to (user.partnerId ?: ""),
            "partnerConnectedAt" to (user.partnerConnectedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "subscriptionInheritedFrom" to (user.subscriptionInheritedFrom ?: ""),
            "subscriptionInheritedAt" to (user.subscriptionInheritedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "connectedPartnerCode" to (user.connectedPartnerCode ?: ""),
            "connectedPartnerId" to (user.connectedPartnerId ?: ""),
            "connectedAt" to (user.connectedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "isSubscribed" to user.isSubscribed,
            "googleUserID" to firebaseUser.uid,
            "lastLoginDate" to Timestamp(Date()),
            "createdAt" to Timestamp(Date()),
            "updatedAt" to Timestamp(Date()),
            "onboardingInProgress" to true,
            "relationshipImprovement" to (user.relationshipImprovement ?: ""),
            "questionMode" to (user.questionMode ?: ""),
            "languageCode" to (user.languageCode ?: defaultDeviceLanguage()),
            "dailyQuestionFirstAccessDate" to (user.dailyQuestionFirstAccessDate?.let { Timestamp(it) } ?: FieldValue.delete()),
            "dailyQuestionMaxDayReached" to user.dailyQuestionMaxDayReached,
            "dailyChallengeFirstAccessDate" to (user.dailyChallengeFirstAccessDate?.let { Timestamp(it) } ?: FieldValue.delete()),
            "dailyChallengeMaxDayReached" to user.dailyChallengeMaxDayReached
        )
        user.relationshipStartDate?.let { data["relationshipStartDate"] = Timestamp(it) }
        user.profileImageURL?.let { data["profileImageURL"] = it }
        user.profileImageUpdatedAt?.let { data["profileImageUpdatedAt"] = Timestamp(it) }
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
        db.collection("users").document(firebaseUser.uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "✅ Données partielles sauvegardées") }
            .addOnFailureListener { e -> _errorMessage.value = "Erreur de sauvegarde: ${e.localizedMessage}" }
    }

    fun saveUserData(user: AppUser) {
        val firebaseUser = auth.currentUser ?: run { _errorMessage.value = "Utilisateur non connecté"; return }
        _isLoading.value = true
        val data = hashMapOf(
            "id" to user.id,
            "name" to user.name,
            "birthDate" to Timestamp(user.birthDate),
            "relationshipGoals" to user.relationshipGoals,
            "relationshipDuration" to user.relationshipDuration.raw,
            "partnerCode" to (user.partnerCode ?: ""),
            "partnerId" to (user.partnerId ?: ""),
            "partnerConnectedAt" to (user.partnerConnectedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "subscriptionInheritedFrom" to (user.subscriptionInheritedFrom ?: ""),
            "subscriptionInheritedAt" to (user.subscriptionInheritedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "connectedPartnerCode" to (user.connectedPartnerCode ?: ""),
            "connectedPartnerId" to (user.connectedPartnerId ?: ""),
            "connectedAt" to (user.connectedAt?.let { Timestamp(it) } ?: FieldValue.delete()),
            "isSubscribed" to user.isSubscribed,
            "googleUserID" to firebaseUser.uid,
            "lastLoginDate" to Timestamp(Date()),
            "createdAt" to Timestamp(Date()),
            "updatedAt" to Timestamp(Date()),
            "onboardingInProgress" to false,
            "relationshipImprovement" to (user.relationshipImprovement ?: ""),
            "questionMode" to (user.questionMode ?: ""),
            "dailyQuestionFirstAccessDate" to (user.dailyQuestionFirstAccessDate?.let { Timestamp(it) } ?: FieldValue.delete()),
            "dailyQuestionMaxDayReached" to user.dailyQuestionMaxDayReached,
            "dailyChallengeFirstAccessDate" to (user.dailyChallengeFirstAccessDate?.let { Timestamp(it) } ?: FieldValue.delete()),
            "dailyChallengeMaxDayReached" to user.dailyChallengeMaxDayReached,
            "languageCode" to (user.languageCode ?: defaultDeviceLanguage())
        )
        user.relationshipStartDate?.let { data["relationshipStartDate"] = Timestamp(it) }
        user.profileImageURL?.let { data["profileImageURL"] = it }
        user.profileImageUpdatedAt?.let { data["profileImageUpdatedAt"] = Timestamp(it) }
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
        db.collection("users").document(firebaseUser.uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                _isLoading.value = false
                UserCacheManager.cacheUser(user)
                _currentUser.value = user
                _isAuthenticated.value = true
                Log.d(TAG, "✅ Données utilisateur sauvegardées (Google)")
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = "Erreur de sauvegarde: ${e.localizedMessage}"
            }
    }

    fun loadUserData(uid: String) {
        _isLoading.value = true
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                _isLoading.value = false
                if (!doc.exists()) {
                    _isAuthenticated.value = true
                    _currentUser.value = null
                    return@addOnSuccessListener
                }
                val data = doc.data ?: emptyMap<String, Any>()
                val onboardingInProgress = data["onboardingInProgress"] as? Boolean ?: false
                val name = data["name"] as? String ?: ""
                val relationshipGoals = (data["relationshipGoals"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val relationshipDuration = data["relationshipDuration"] as? String ?: ""
                val birthTs = data["birthDate"] as? Timestamp
                val isOnboardingComplete = name.isNotEmpty() && relationshipGoals.isNotEmpty() && relationshipDuration.isNotEmpty() && birthTs != null && !onboardingInProgress
                if (!isOnboardingComplete) {
                    if (onboardingInProgress && !isOnboardingInProgress) {
                        val partial = AppUser(
                            id = (data["id"] as? String) ?: uid,
                            name = name,
                            birthDate = birthTs?.toDate() ?: Date(),
                            relationshipGoals = relationshipGoals,
                            relationshipDuration = RelationshipDuration.fromRaw(relationshipDuration),
                            partnerCode = data["partnerCode"] as? String,
                            partnerId = data["partnerId"] as? String,
                            partnerConnectedAt = (data["partnerConnectedAt"] as? Timestamp)?.toDate(),
                            subscriptionInheritedFrom = data["subscriptionSharedFrom"] as? String,
                            subscriptionInheritedAt = (data["subscriptionInheritedAt"] as? Timestamp)?.toDate(),
                            isSubscribed = data["isSubscribed"] as? Boolean ?: false,
                            onboardingInProgress = true,
                            dailyQuestionFirstAccessDate = (data["dailyQuestionFirstAccessDate"] as? Timestamp)?.toDate(),
                            dailyQuestionMaxDayReached = (data["dailyQuestionMaxDayReached"] as? Number)?.toInt() ?: 0,
                            dailyChallengeFirstAccessDate = (data["dailyChallengeFirstAccessDate"] as? Timestamp)?.toDate(),
                            dailyChallengeMaxDayReached = (data["dailyChallengeMaxDayReached"] as? Number)?.toInt() ?: 0
                        )
                        _isAuthenticated.value = true
                        _currentUser.value = partial
                    } else {
                        _isAuthenticated.value = true
                        _currentUser.value = null
                    }
                    return@addOnSuccessListener
                }
                val user = parseUserFromDoc(doc)
                UserCacheManager.cacheUser(user)
                _currentUser.value = user
                _isAuthenticated.value = true
                startListeningForSubscriptionChanges()
            }
            .addOnFailureListener { e ->
                _isLoading.value = false
                _errorMessage.value = e.localizedMessage
            }
    }

    private fun parseUserFromDoc(doc: DocumentSnapshot): AppUser {
        val d = doc.data ?: emptyMap<String, Any>()
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

    private fun defaultDeviceLanguage(): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val tag = android.os.LocaleList.getDefault()[0]
            tag.language
        } else {
            java.util.Locale.getDefault().language
        }
    } catch (_: Throwable) { "fr" }

    private fun createEmptyUserProfile(uid: String, email: String?, name: String?) {
        val data = hashMapOf(
            "id" to uid,
            "email" to (email ?: ""),
            "name" to (name ?: ""),
            "googleUserID" to uid,
            "authProvider" to "google.com",
            "createdAt" to Timestamp(Date()),
            "onboardingCompleted" to false
        )
        db.collection("users").document(uid).set(data)
            .addOnSuccessListener {
                _isAuthenticated.value = true
                _currentUser.value = null
                Log.d(TAG, "✅ Profil vide créé (Google)")
            }
            .addOnFailureListener { e -> _errorMessage.value = "Erreur création profil: ${e.localizedMessage}" }
    }

    // --- Subscription Management ---
    fun updateSubscriptionStatus(isSubscribed: Boolean) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid)
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
            .addOnFailureListener { e -> _errorMessage.value = "Erreur abonnement: ${e.localizedMessage}" }
    }

    fun startListeningForSubscriptionChanges() {
        val user = auth.currentUser ?: return
        subscriptionListener?.remove()
        subscriptionListener = db.collection("users").document(user.uid)
            .addSnapshotListener { snap, e ->
                if (e != null) { Log.e(TAG, "Listener abonnement erreur", e); return@addSnapshotListener }
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

    fun stopListeningForSubscriptionChanges() { subscriptionListener?.remove(); subscriptionListener = null }

    // --- Partner / Functions helpers ---
    fun getUserData(userId: String, completion: (AppUser?) -> Unit) {
        val me = auth.currentUser
        if (me != null && me.uid == userId) {
            getUserDataDirect(userId, completion)
        } else {
            getPartnerInfoViaCloudFunction(userId, completion)
        }
    }

    private fun getUserDataDirect(userId: String, completion: (AppUser?) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { completion(null); return@addOnSuccessListener }
                completion(parseUserFromDoc(doc))
            }
            .addOnFailureListener { _ -> completion(null) }
    }

    private fun getPartnerInfoViaCloudFunction(partnerId: String, completion: (AppUser?) -> Unit) {
        functions.getHttpsCallable("getPartnerInfo").call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res: HttpsCallableResult ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(null)
                val success = root["success"] as? Boolean ?: false
                if (!success) return@addOnSuccessListener completion(null)
                val info = root["partnerInfo"] as? Map<*, *> ?: return@addOnSuccessListener completion(null)
                val user = AppUser(
                    id = partnerId,
                    name = info["name"] as? String ?: "Partenaire",
                    birthDate = Date(),
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
            .addOnFailureListener { _ -> completion(null) }
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
        db.collection("users").document(user.uid).update(data)
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

    fun updateUserName(newName: String, completion: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return completion(false)
        db.collection("users").document(user.uid)
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
        db.collection("users").document(user.uid)
            .update(mapOf("relationshipStartDate" to Timestamp(date), "updatedAt" to Timestamp(Date())))
            .addOnSuccessListener {
                _currentUser.value = _currentUser.value?.copy(relationshipStartDate = date)
                _currentUser.value?.let { UserCacheManager.cacheUser(it) }
                completion(true)
            }
            .addOnFailureListener { completion(false) }
    }

    // --- Profile Image Upload ---
    fun updateProfileImage(bitmap: Bitmap, completion: (Boolean, String?) -> Unit) {
        val me = auth.currentUser ?: run { completion(false, null); return }
        uploadProfileImageInternal(me.uid, bitmap) { success, url ->
            if (!success || url == null) return@uploadProfileImageInternal completion(false, null)
            val cur = _currentUser.value
            if (cur != null) {
                val updated = cur.copy(profileImageURL = url, profileImageUpdatedAt = Date())
                _currentUser.value = updated
                UserCacheManager.cacheUser(updated)
                ImageCacheService.clearCachedImage(cur.profileImageURL)
                ImageCacheService.cacheImage(bitmap, url)
                saveUserData(updated)
            }
            completion(true, url)
        }
    }

    private fun uploadProfileImageInternal(uid: String, bitmap: Bitmap, completion: (Boolean, String?) -> Unit) {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val data = baos.toByteArray()
        val path = "profile_images/$uid/profile.jpg"
        val ref = storage.reference.child(path)
        val meta = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .setCustomMetadata("uploadedBy", uid)
            .build()

        ref.putBytes(data, meta)
            .addOnFailureListener { e -> Log.e(TAG, "Upload échec", e); completion(false, null) }
            .addOnSuccessListener {
                ref.downloadUrl
                    .addOnSuccessListener { uri -> completion(true, uri.toString()) }
                    .addOnFailureListener { completion(false, null) }
            }
    }

    // --- Shared Partner Data ---
    fun updateSharedPartnerData() {
        val me = auth.currentUser ?: return
        val cur = _currentUser.value ?: return
        val payload = hashMapOf(
            "name" to cur.name,
            "relationshipStartDate" to (cur.relationshipStartDate?.let { Timestamp(it) } ?: FieldValue.delete()),
            "currentLocation" to (cur.currentLocation?.let {
                mapOf(
                    "latitude" to it.latitude,
                    "longitude" to it.longitude,
                    "city" to it.city,
                    "country" to it.country,
                    "lastUpdated" to Timestamp(it.lastUpdated)
                )
            } ?: FieldValue.delete()),
            "lastActive" to Timestamp(Date()),
            "profileImageURL" to (cur.profileImageURL ?: FieldValue.delete())
        )
        db.collection("sharedPartnerData").document(me.uid).set(payload, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "✅ Données partagées mises à jour") }
            .addOnFailureListener { e -> Log.e(TAG, "Erreur partage données", e) }
    }

    fun getSharedPartnerData(partnerId: String, completion: (AppUser?) -> Unit) {
        db.collection("sharedPartnerData").document(partnerId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { completion(null); return@addOnSuccessListener }
                val d = doc.data ?: return@addOnSuccessListener completion(null)
                val user = AppUser(
                    id = partnerId,
                    name = d["name"] as? String ?: "",
                    birthDate = Date(),
                    relationshipGoals = emptyList(),
                    relationshipDuration = RelationshipDuration.NONE,
                    relationshipImprovement = null,
                    questionMode = null,
                    partnerCode = null,
                    partnerId = null,
                    partnerConnectedAt = null,
                    subscriptionInheritedFrom = null,
                    subscriptionInheritedAt = null,
                    connectedPartnerCode = null,
                    connectedPartnerId = null,
                    connectedAt = null,
                    isSubscribed = false,
                    onboardingInProgress = false,
                    relationshipStartDate = (d["relationshipStartDate"] as? Timestamp)?.toDate(),
                    profileImageURL = d["profileImageURL"] as? String,
                    currentLocation = parseUserLocation(d["currentLocation"] as? Map<String, Any?>)
                )
                completion(user)
            }
            .addOnFailureListener { completion(null) }
    }

    // --- Sync via Cloud Functions ---
    fun syncPartnerJournalEntries(partnerId: String, completion: (Boolean, String?) -> Unit) {
        if (auth.currentUser == null) return completion(false, "Utilisateur non connecté")
        functions.getHttpsCallable("syncPartnerJournalEntries").call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(false, "Réponse invalide")
                val ok = root["success"] as? Boolean ?: false
                val msg = root["message"] as? String
                completion(ok, msg ?: if (ok) "Synchronisation terminée" else "Erreur inconnue")
            }
            .addOnFailureListener { e -> completion(false, e.localizedMessage) }
    }

    fun syncPartnerFavorites(partnerId: String, completion: (Boolean, String?) -> Unit) {
        if (auth.currentUser == null) return completion(false, "Utilisateur non connecté")
        functions.getHttpsCallable("syncPartnerFavorites").call(mapOf("partnerId" to partnerId))
            .addOnSuccessListener { res ->
                val root = res.data as? Map<*, *> ?: return@addOnSuccessListener completion(false, "Réponse invalide")
                val ok = root["success"] as? Boolean ?: false
                val msg = if (ok) "Synchronisation réussie" else root["message"] as? String ?: "Erreur inconnue"
                completion(ok, msg)
            }
            .addOnFailureListener { e -> completion(false, e.localizedMessage) }
    }

    fun forceRefreshUserData() { auth.currentUser?.uid?.let { loadUserData(it) } }
}

// =============================
//  Modèles & Stubs utilitaires
// =============================

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
    companion object { fun fromRaw(raw: String): RelationshipDuration = values().firstOrNull { it.raw == raw } ?: NOT_IN_RELATIONSHIP }
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

/** Stubs de cache, remplace par tes implémentations réelles. */
object UserCacheManager { fun cacheUser(@Suppress("UNUSED_PARAMETER") user: AppUser) {} }
object ImageCacheService {
    fun clearCachedImage(@Suppress("UNUSED_PARAMETER") url: String?) {}
    fun cacheImage(@Suppress("UNUSED_PARAMETER") bitmap: Bitmap, @Suppress("UNUSED_PARAMETER") url: String) {}
}
