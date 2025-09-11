/*
 Single‑file bundle for PartnerStatusCard (Compose + Firebase)
 ------------------------------------------------------------

 1) COPY to res/values/strings.xml (FR shown; mirror to EN as needed)

<resources>
    <!-- Titles & labels -->
    <string name="partner_status_title">Statut du partenaire</string>
    <string name="you">Vous</string>
    <string name="partner">Partenaire</string>
    <string name="no_partner_connected">Aucun partenaire connecté</string>

    <!-- Access summary -->
    <string name="premium_access_label">Accès Premium</string>
    <string name="both_have_access">Tous les deux ont l&#39;accès</string>
    <string name="syncing">Synchronisation…</string>
    <string name="no_premium_access">Aucun accès premium</string>

    <!-- Quick actions -->
    <string name="quick_actions">Actions rapides</string>
    <string name="subscribe_button">S&#39;abonner</string>
    <string name="cancel_subscription">Résilier</string>
    <string name="refresh">Actualiser</string>

    <!-- Generic -->
    <string name="loading_simple">Chargement…</string>
    <string name="ellipsis">…</string>

    <!-- Status labels -->
    <string name="premium_label">Premium</string>
    <string name="free_label">Gratuit</string>
    <string name="premium_solo_label">Premium (Solo)</string>

    <!-- Subscription type badges -->
    <string name="subscription_type_direct">Payé</string>
    <string name="subscription_type_shared_from_partner">Partagé</string>
    <string name="subscription_type_inherited">Hérité</string>
</resources>


 2) Add Gradle deps

 dependencies {
   implementation platform("com.google.firebase:firebase-bom:33.2.0")
   implementation "com.google.firebase:firebase-auth-ktx"
   implementation "com.google.firebase:firebase-firestore-ktx"
   implementation "com.google.firebase:firebase-functions-ktx"
   implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1"
   implementation "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1"

   implementation("androidx.compose.ui:ui:1.7.0")
   implementation("androidx.compose.material3:material3:1.3.0")
   implementation("androidx.compose.material:material-icons-extended:1.7.0")
   implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
   debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")
 }

 Notes:
 - Uses stringResource(R.string.key) (Compose‑idiomatic equivalent of context.getString(...)).
 - ViewModel listens to users/{uid} for realtime updates + calls httpsCallable("getPartnerInfo").
*/

@file:Suppress("UnusedImport")
package com.yourapp.ui.partner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- Data model (port of your Swift struct) ---
data class UserSubscriptionInfo(
    val id: String,
    val name: String,
    val isSubscribed: Boolean,
    val subscriptionType: String? = null,
    val sharedFrom: String? = null
)

// --- UI State ---
data class PartnerStatusUiState(
    val currentUserInfo: UserSubscriptionInfo? = null,
    val partnerInfo: UserSubscriptionInfo? = null,
    val isLoading: Boolean = true,
    val hasPartner: Boolean = false
)

// --- ViewModel ---
class PartnerStatusViewModel : ViewModel() {
    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val functions = Firebase.functions

    private val _uiState = MutableStateFlow(PartnerStatusUiState())
    val uiState: StateFlow<PartnerStatusUiState> = _uiState

    private var registration: ListenerRegistration? = null

    init { attachUserListener() }

    fun loadData() { attachUserListener(force = true) }

    private fun attachUserListener(force: Boolean = false) {
        val uid = auth.currentUser?.uid ?: run {
            _uiState.value = _uiState.value.copy(isLoading = false)
            return
        }
        if (!force && registration != null) return
        registration?.remove()

        _uiState.value = _uiState.value.copy(isLoading = true)

        registration = db.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@addSnapshotListener
                }
                val data = snap?.data ?: run {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@addSnapshotListener
                }

                val user = UserSubscriptionInfo(
                    id = uid,
                    name = (data["name"] as? String) ?: "",
                    isSubscribed = (data["isSubscribed"] as? Boolean) ?: false,
                    subscriptionType = data["subscriptionType"] as? String,
                    sharedFrom = data["subscriptionSharedFrom"] as? String
                )

                val partnerId = (data["partnerId"] as? String)?.takeIf { it.isNotBlank() }

                _uiState.value = _uiState.value.copy(
                    currentUserInfo = user,
                    hasPartner = partnerId != null
                )

                if (partnerId != null) {
                    viewModelScope.launch { fetchPartnerInfo(partnerId) }
                } else {
                    _uiState.value = _uiState.value.copy(partnerInfo = null, isLoading = false)
                }
            }
    }

    private suspend fun fetchPartnerInfo(partnerId: String) {
        try {
            val result = functions
                .httpsCallable("getPartnerInfo")
                .call(mapOf("partnerId" to partnerId))
                .await()

            val map = result.data as? Map<*, *> ?: emptyMap<String, Any>()
            val success = map["success"] as? Boolean ?: false
            if (success) {
                val partnerMap = map["partnerInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                val partner = UserSubscriptionInfo(
                    id = partnerId,
                    name = (partnerMap["name"] as? String) ?: "",
                    isSubscribed = (partnerMap["isSubscribed"] as? Boolean) ?: false,
                    subscriptionType = partnerMap["subscriptionType"] as? String,
                    sharedFrom = partnerMap["subscriptionSharedFrom"] as? String
                )
                _uiState.value = _uiState.value.copy(partnerInfo = partner, isLoading = false)
            } else {
                _uiState.value = _uiState.value.copy(partnerInfo = null, isLoading = false)
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(partnerInfo = null, isLoading = false)
        }
    }

    fun simulateSubscription() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "isSubscribed" to true,
                            "subscriptionType" to "direct",
                            "subscriptionStartedAt" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (_: Exception) { }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun simulateUnsubscription() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "isSubscribed" to false,
                            "subscriptionType" to FieldValue.delete(),
                            "subscriptionExpiredAt" to Timestamp.now()
                        )
                    )
                    .await()
            } catch (_: Exception) { }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        registration?.remove()
        registration = null
    }
}

// --- UI ---
@Composable
fun PartnerStatusCard(
    modifier: Modifier = Modifier,
    viewModel: PartnerStatusViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val ui by viewModel.uiState.collectAsState()

    Card(modifier = modifier, shape = RoundedCornerShape(15.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(id = R.string.partner_status_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                if (ui.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (ui.hasPartner) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserStatusView(
                        title = stringResource(R.string.you),
                        userInfo = ui.currentUserInfo,
                        isCurrentUser = true
                    )

                    Icon(
                        imageVector = Icons.Outlined.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    UserStatusView(
                        title = stringResource(R.string.partner),
                        userInfo = ui.partnerInfo,
                        isCurrentUser = false
                    )
                }

                Spacer(Modifier.height(12.dp))
                AccessSummaryView(ui)
                Spacer(Modifier.height(8.dp))
                QuickActionsView(
                    isLoading = ui.isLoading,
                    currentIsSubscribed = ui.currentUserInfo?.isSubscribed == true,
                    onSubscribe = { viewModel.simulateSubscription() },
                    onUnsubscribe = { viewModel.simulateUnsubscription() },
                    onRefresh = { viewModel.loadData() }
                )
            } else {
                NoPartnerBox(
                    user = ui.currentUserInfo,
                    isLoading = ui.isLoading,
                    onRefresh = { viewModel.loadData() }
                )
            }
        }
    }
}

@Composable
private fun AccessSummaryView(ui: PartnerStatusUiState) {
    Divider()
    Spacer(Modifier.height(6.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.premium_access_label),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.weight(1f))

        val bothHaveAccess = (ui.currentUserInfo?.isSubscribed == true) && (ui.partnerInfo?.isSubscribed == true)
        when {
            bothHaveAccess -> StatusPill(
                text = stringResource(R.string.both_have_access),
                bg = Color(0x3322AA22), fg = Color(0xFF1B5E20)
            )
            (ui.currentUserInfo?.isSubscribed == true) || (ui.partnerInfo?.isSubscribed == true) -> StatusPill(
                text = stringResource(R.string.syncing),
                bg = Color(0x33FF9800), fg = Color(0xFFF57C00)
            )
            else -> StatusPill(
                text = stringResource(R.string.no_premium_access),
                bg = Color(0x33D32F2F), fg = Color(0xFFC62828)
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QuickActionsView(
    isLoading: Boolean,
    currentIsSubscribed: Boolean,
    onSubscribe: () -> Unit,
    onUnsubscribe: () -> Unit,
    onRefresh: () -> Unit
) {
    Divider()
    Spacer(Modifier.height(6.dp))

    Text(
        text = stringResource(R.string.quick_actions),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onSubscribe,
            enabled = !isLoading && !currentIsSubscribed,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = stringResource(R.string.subscribe_button))
        }
        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onUnsubscribe,
            enabled = !isLoading && currentIsSubscribed,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = stringResource(R.string.cancel_subscription), color = Color.Red)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
        }
    }
}

@Composable
private fun NoPartnerBox(user: UserSubscriptionInfo?, isLoading: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color.Gray)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.no_partner_connected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (user != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (user.isSubscribed) Color(0xFF2E7D32) else Color(0xFFC62828))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (user.isSubscribed) stringResource(R.string.premium_solo_label) else stringResource(R.string.free_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UserStatusView(title: String, userInfo: UserSubscriptionInfo?, isCurrentUser: Boolean) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(6.dp))

        if (userInfo != null) {
            Text(
                text = userInfo.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (userInfo.isSubscribed) Color(0xFF2E7D32) else Color(0xFFC62828))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (userInfo.isSubscribed) stringResource(R.string.premium_label) else stringResource(R.string.free_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (userInfo.isSubscribed) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }
            userInfo.subscriptionType?.let { type ->
                Spacer(Modifier.height(6.dp))
                val (chipText, chipColor) = when (type) {
                    "direct" -> stringResource(R.string.subscription_type_direct) to Color(0xFF1976D2)
                    "shared_from_partner" -> stringResource(R.string.subscription_type_shared_from_partner) to Color(0xFF7B1FA2)
                    "inherited" -> stringResource(R.string.subscription_type_inherited) to Color(0xFFF57C00)
                    else -> type to Color.Gray
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(chipColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(chipText, color = chipColor, style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.ellipsis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.loading_simple),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
