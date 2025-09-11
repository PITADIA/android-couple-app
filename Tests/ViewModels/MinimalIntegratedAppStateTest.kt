package com.love2loveapp.tests.viewmodels

import androidx.lifecycle.SavedStateHandle
import com.love2loveapp.core.viewmodels.MinimalIntegratedAppState
import com.love2loveapp.data.persistence.AppPreferences
import com.love2loveapp.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests pour MinimalIntegratedAppState
 * Vérifie la restauration après process death et le calcul de routes
 */
@ExperimentalCoroutinesApi
class MinimalIntegratedAppStateTest {
    
    private lateinit var appPreferences: AppPreferences
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var appState: MinimalIntegratedAppState
    
    @Before
    fun setup() {
        appPreferences = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()
        
        // Mock des flows par défaut
        every { appPreferences.isAuthenticated } returns MutableStateFlow(false)
        every { appPreferences.hasPartner } returns MutableStateFlow(false)
        every { appPreferences.onboardingCompleted } returns MutableStateFlow(false)
        
        appState = MinimalIntegratedAppState(appPreferences, savedStateHandle)
    }
    
    @Test
    fun `should calculate route for unauthenticated user`() = runTest {
        // Given
        every { appPreferences.isAuthenticated } returns MutableStateFlow(false)
        every { appPreferences.hasPartner } returns MutableStateFlow(false)
        every { appPreferences.onboardingCompleted } returns MutableStateFlow(false)
        
        // When
        val route = appState.currentRoute.first()
        
        // Then
        assertEquals(Route.Onboarding, route)
    }
    
    @Test
    fun `should calculate route for authenticated user without partner`() = runTest {
        // Given
        every { appPreferences.isAuthenticated } returns MutableStateFlow(true)
        every { appPreferences.hasPartner } returns MutableStateFlow(false)
        every { appPreferences.onboardingCompleted } returns MutableStateFlow(true)
        
        appState = MinimalIntegratedAppState(appPreferences, savedStateHandle)
        
        // When
        val route = appState.currentRoute.first()
        
        // Then
        assertEquals(Route.PartnerConnection, route)
    }
    
    @Test
    fun `should calculate route for authenticated user with partner`() = runTest {
        // Given
        every { appPreferences.isAuthenticated } returns MutableStateFlow(true)
        every { appPreferences.hasPartner } returns MutableStateFlow(true)
        every { appPreferences.onboardingCompleted } returns MutableStateFlow(true)
        
        appState = MinimalIntegratedAppState(appPreferences, savedStateHandle)
        
        // When
        val route = appState.currentRoute.first()
        
        // Then
        assertEquals(Route.Main, route)
    }
    
    @Test
    fun `should calculate route for incomplete onboarding`() = runTest {
        // Given
        every { appPreferences.isAuthenticated } returns MutableStateFlow(true)
        every { appPreferences.hasPartner } returns MutableStateFlow(true)
        every { appPreferences.onboardingCompleted } returns MutableStateFlow(false)
        
        appState = MinimalIntegratedAppState(appPreferences, savedStateHandle)
        
        // When
        val route = appState.currentRoute.first()
        
        // Then
        assertEquals(Route.Onboarding, route)
    }
    
    @Test
    fun `should persist authentication state`() = runTest {
        // When
        appState.setAuthenticated(true)
        
        // Then
        coVerify { appPreferences.setAuthenticated(true) }
    }
    
    @Test
    fun `should persist partner state`() = runTest {
        // When
        appState.setHasPartner(true, "partner123")
        
        // Then
        coVerify { 
            appPreferences.setHasPartner(true)
            appPreferences.setPartnerId("partner123")
        }
    }
    
    @Test
    fun `should complete onboarding`() = runTest {
        // When
        appState.completeOnboarding()
        
        // Then
        coVerify { appPreferences.setOnboardingCompleted(true) }
    }
    
    @Test
    fun `should logout and clear data`() = runTest {
        // When
        appState.logout()
        
        // Then
        coVerify { appPreferences.clearUserData() }
    }
    
    @Test
    fun `should persist currentDay in SavedStateHandle`() {
        // When
        appState.currentDay = 5
        
        // Then
        assertEquals(5, appState.currentDay)
        assertEquals(5, savedStateHandle.get<Int>("current_day"))
    }
    
    @Test
    fun `should persist selectedCategory in SavedStateHandle`() {
        // When
        appState.selectedCategory = "love"
        
        // Then
        assertEquals("love", appState.selectedCategory)
        assertEquals("love", savedStateHandle.get<String>("selected_category"))
    }
    
    @Test
    fun `should restore currentDay from SavedStateHandle`() {
        // Given
        savedStateHandle.set("current_day", 10)
        
        // When
        val restoredDay = appState.currentDay
        
        // Then
        assertEquals(10, restoredDay)
    }
    
    @Test
    fun `should use default values when SavedStateHandle is empty`() {
        // When
        val defaultDay = appState.currentDay
        val defaultCategory = appState.selectedCategory
        
        // Then
        assertEquals(1, defaultDay) // Default day
        assertEquals(null, defaultCategory) // Default category
    }
}
