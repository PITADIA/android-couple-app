package com.love2loveapp.data.persistence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppPreferences - Gestion de la persistance des états critiques
 * 
 * Responsabilités:
 * - Persistance des flags critiques (auth, onboarding, couple)
 * - Restauration après process death
 * - États globaux nécessaires au calcul de navigation
 */

// Extension pour créer le DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    private val context: Context
) {
    
    companion object {
        // Clés pour les préférences critiques
        val AUTHENTICATED_KEY = booleanPreferencesKey("is_authenticated")
        val HAS_PARTNER_KEY = booleanPreferencesKey("has_partner")
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val PARTNER_ID_KEY = stringPreferencesKey("partner_id")
        val LAST_ROUTE_KEY = stringPreferencesKey("last_route")
    }
    
    private val dataStore = context.dataStore
    
    // === États d'authentification ===
    
    val isAuthenticated: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[AUTHENTICATED_KEY] ?: false }
    
    suspend fun setAuthenticated(isAuthenticated: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTHENTICATED_KEY] = isAuthenticated
        }
    }
    
    // === États de partenaire ===
    
    val hasPartner: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[HAS_PARTNER_KEY] ?: false }
    
    suspend fun setHasPartner(hasPartner: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_PARTNER_KEY] = hasPartner
        }
    }
    
    val partnerId: Flow<String?> = dataStore.data
        .map { preferences -> preferences[PARTNER_ID_KEY] }
    
    suspend fun setPartnerId(partnerId: String?) {
        dataStore.edit { preferences ->
            if (partnerId != null) {
                preferences[PARTNER_ID_KEY] = partnerId
            } else {
                preferences.remove(PARTNER_ID_KEY)
            }
        }
    }
    
    // === États d'onboarding ===
    
    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[ONBOARDING_COMPLETED_KEY] ?: false }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }
    
    // === États utilisateur ===
    
    val userId: Flow<String?> = dataStore.data
        .map { preferences -> preferences[USER_ID_KEY] }
    
    suspend fun setUserId(userId: String?) {
        dataStore.edit { preferences ->
            if (userId != null) {
                preferences[USER_ID_KEY] = userId
            } else {
                preferences.remove(USER_ID_KEY)
            }
        }
    }
    
    // === Navigation state ===
    
    val lastRoute: Flow<String?> = dataStore.data
        .map { preferences -> preferences[LAST_ROUTE_KEY] }
    
    suspend fun setLastRoute(route: String?) {
        dataStore.edit { preferences ->
            if (route != null) {
                preferences[LAST_ROUTE_KEY] = route
            } else {
                preferences.remove(LAST_ROUTE_KEY)
            }
        }
    }
    
    // === Utilitaires ===
    
    /**
     * Efface toutes les préférences (logout complet)
     */
    suspend fun clearAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    /**
     * Efface uniquement les données utilisateur (garde les flags système)
     */
    suspend fun clearUserData() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID_KEY)
            preferences.remove(PARTNER_ID_KEY)
            preferences[HAS_PARTNER_KEY] = false
            preferences[AUTHENTICATED_KEY] = false
        }
    }
}
