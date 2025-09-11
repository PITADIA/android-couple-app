// PartnerSubscriptionDebugScreen.kt
// Jetpack Compose version of the SwiftUI PartnerSubscriptionDebugView
// - Uses Firebase Auth, Firestore, and Cloud Functions
// - Uses Android's standard string resources (strings.xml) via stringResource/context.getString
// - No external pull-to-refresh dependencies; includes a Refresh button
//
// Requirements:
//   implementation("androidx.compose.material3:material3:<latest>")
//   implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<latest>")
//   implementation("androidx.lifecycle:lifecycle-runtime-ktx:<latest>")
//   implementation("androidx.activity:activity-compose:<latest>")
//   implementation("com.google.firebase:firebase-auth-ktx:<latest>")
//   implementation("com.google.firebase:firebase-firestore-ktx:<latest>")
//   implementation("com.google.firebase:firebase-functions-ktx:<latest>")
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:<latest>")
//   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:<latest>")
//
// Note: See the strings.xml sample at the bottom of this file (inside a block comment).

package com.example.debug

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// --- Data model ---

data class UserInfo(
    val id: String,
    val name: String,
    val isSubscribed: Boolean,
    val subscriptionType: String?,
    val sharedFrom: String?
)

// --- UI State ---

data class PartnerSubscriptionDebugUiState(
    val userInfo: UserInfo? = null,
    val partnerInfo: UserInfo? = null,
    val isLoading: Boolean = false
)

// --- ViewModel ---

class PartnerSubscriptionDebugViewModel : ViewModel() {
    private val tag = "PartnerSubDebugVM"

    private val _uiState = MutableStateFlow(PartnerSubscriptionDebugUiState())
    val uiState: StateFlow<PartnerSubscriptionDebugUiState> = _uiState

    fun loadUserData() {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Log.e(tag, "User not logged in")
            _uiState.value = _uiState.value.copy(isLoading = false, userInfo = null, partnerInfo = null)
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                Log.d(tag, "Loading user data for uid=${currentUser.uid}")

                val userDoc = Firebase.firestore.collection("users").document(currentUser.uid).get().await()
                val userData = userDoc.data
                if (userData != null) {
                    val user = UserInfo(
                        id = currentUser.uid,
                        name = userData["name"] as? String ?: "Utilisateur",
                        isSubscribed = userData["isSubscribed"] as? Boolean ?: false,
                        subscriptionType = userData["subscriptionType"] as? String,
                        sharedFrom = userData["subscriptionSharedFrom"] as? String
                    )
                    Log.d(tag, "User loaded: name=${user.name}, subscribed=${user.isSubscribed}")

                    var partner: UserInfo? = null
                    val partnerId = (userData["partnerId"] as? String)?.trim().orEmpty()
                    if (partnerId.isNotEmpty()) {
                        try {
                            Log.d(tag, "Fetching partner via Cloud Function: $partnerId")
                            val result = Firebase.functions
                                .httpsCallable("getPartnerInfo")
                                .call(mapOf("partnerId" to partnerId))
                                .await()

                            val resultData = result.data as? Map<*, *> ?: emptyMap<String, Any>()
                            val success = resultData["success"] as? Boolean ?: false
                            if (success) {
                                @Suppress("UNCHECKED_CAST")
                                val partnerInfo = resultData["partnerInfo"] as? Map<String, Any?>
                                if (partnerInfo != null) {
                                    partner = UserInfo(
                                        id = partnerId,
                                        name = partnerInfo["name"] as? String ?: "Partenaire",
                                        isSubscribed = partnerInfo["isSubscribed"] as? Boolean ?: false,
                                        subscriptionType = partnerInfo["subscriptionType"] as? String,
                                        sharedFrom = partnerInfo["subscriptionSharedFrom"] as? String
                                    )
                                    Log.d(tag, "Partner loaded: name=${partner!!.name}, subscribed=${partner!!.isSubscribed}")
                                }
                            } else {
                                Log.e(tag, "Failed to get partner info (success=false)")
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Cloud Function error: ${e.message}", e)
                        }
                    } else {
                        Log.d(tag, "No partner connected")
                    }

                    _uiState.value = PartnerSubscriptionDebugUiState(userInfo = user, partnerInfo = partner, isLoading = false)
                } else {
                    Log.e(tag, "No user data found in Firestore")
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error loading user data: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun simulateSubscription() {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val updateData = mapOf(
                    "isSubscribed" to true,
                    "subscriptionType" to "direct",
                    "subscriptionStartedAt" to Timestamp(Date())
                )
                Firebase.firestore.collection("users").document(currentUser.uid).update(updateData).await()
                _uiState.value = _uiState.value.copy(isLoading = false)
                loadUserData()
            } catch (e: Exception) {
                Log.e(tag, "simulateSubscription error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun simulateUnsubscription() {
        val currentUser = Firebase.auth.currentUser ?: return
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val updateData = mapOf(
                    "isSubscribed" to false,
                    "subscriptionType" to FieldValue.delete(),
                    "subscriptionExpiredAt" to Timestamp(Date())
                )
                Firebase.firestore.collection("users").document(currentUser.uid).update(updateData).await()
                _uiState.value = _uiState.value.copy(isLoading = false)
                loadUserData()
            } catch (e: Exception) {
                Log.e(tag, "simulateUnsubscription error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun cleanupOrphanedCodes() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val result = Firebase.functions
                    .httpsCallable("cleanupOrphanedPartnerCodes")
                    .call()
                    .await()
                Log.d(tag, "cleanupOrphanedCodes result: ${result.data}")
                _uiState.value = _uiState.value.copy(isLoading = false)
                loadUserData()
            } catch (e: Exception) {
                Log.e(tag, "cleanupOrphanedCodes error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}

// --- Composables ---

@Composable
fun PartnerSubscriptionDebugScreen(
    viewModel: PartnerSubscriptionDebugViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.loadUserData()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.debug_sync_subscriptions)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            SectionTitle(text = stringResource(R.string.current_user), color = Color(0xFF1565C0))

            if (state.userInfo != null) {
                UserInfoCard(user = state.userInfo!!)
            } else {
                if (state.isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.loading))
                    }
                } else {
                    Text(text = stringResource(R.string.loading))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle(text = stringResource(R.string.partner_connected_status), color = Color(0xFF6A1B9A))

            if (state.partnerInfo != null) {
                PartnerInfoCard(partner = state.partnerInfo!!)
            } else {
                Text(
                    text = stringResource(R.string.no_partner_connected),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFC62828),
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle(text = stringResource(R.string.test_actions), color = Color(0xFFEF6C00))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.simulateSubscription() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.simulate_subscription))
                }

                Button(
                    onClick = { viewModel.simulateUnsubscription() },
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.simulate_revocation))
                }

                OutlinedButton(
                    onClick = { viewModel.loadUserData() },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.refresh))
                }

                Button(
                    onClick = { viewModel.cleanupOrphanedCodes() },
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.clean_orphan_codes))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = color
    )
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun UserInfoCard(user: UserInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.id_label_format, user.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.name_label_format, user.name),
                style = MaterialTheme.typography.titleMedium
            )

            SubscriptionBadge(isSubscribed = user.isSubscribed)

            user.subscriptionType?.let { type ->
                TypeChip(text = stringResource(R.string.type_label, type), background = Color(0x33FFA726))
            }

            user.sharedFrom?.let { shared ->
                Text(
                    text = stringResource(R.string.user_shared_by, shared),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFEF6C00)
                )
            }
        }
    }
}

@Composable
private fun PartnerInfoCard(partner: UserInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.id_label_format, partner.id),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.partner_connected_status),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF2E7D32)
            )

            SubscriptionBadge(isSubscribed = partner.isSubscribed)

            partner.subscriptionType?.let { type ->
                TypeChip(text = stringResource(R.string.type_label, type), background = Color(0x33FFA726))
            }

            partner.sharedFrom?.let { shared ->
                Text(
                    text = stringResource(R.string.partner_shared_by, shared),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(isSubscribed: Boolean) {
    val color = if (isSubscribed) Color(0xFF2E7D32) else Color(0xFFC62828)
    val label = if (isSubscribed) R.string.premium else R.string.free

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun TypeChip(text: String, background: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall)
    }
}

/*
==================== strings.xml (sample) ====================

<resources>
    <string name="debug_sync_subscriptions">Debug : synchroniser les abonnements</string>
    <string name="current_user">Utilisateur actuel</string>
    <string name="partner_connected_status">Partenaire connecté</string>

    <string name="id_label_format">ID : %1$s</string>
    <string name="name_label_format">Nom : %1$s</string>
    <string name="type_label">Type : %1$s</string>

    <string name="user_shared_by">Abonnement partagé par : %1$s</string>
    <string name="partner_shared_by">Abonnement du partenaire partagé par : %1$s</string>

    <string name="premium">Premium</string>
    <string name="free">Gratuit</string>

    <string name="test_actions">Actions de test</string>
    <string name="simulate_subscription">Simuler l'abonnement</string>
    <string name="simulate_revocation">Simuler la résiliation</string>
    <string name="refresh">Rafraîchir</string>
    <string name="clean_orphan_codes">Nettoyer les codes orphelins</string>

    <string name="loading">Chargement…</string>
    <string name="no_partner_connected">Aucun partenaire connecté</string>
</resources>

=============================================================
*/
