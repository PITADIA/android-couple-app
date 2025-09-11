package com.love2loveapp.tests.lifecycle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.love2loveapp.core.viewmodels.MinimalIntegratedAppState
import com.love2loveapp.data.persistence.AppPreferences
import com.love2loveapp.navigation.Route
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests de restauration après process death
 * Simule la mort du processus et vérifie la restauration des états
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class ProcessDeathTest {
    
    private lateinit var context: Context
    private lateinit var testDataStoreFile: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testDataStoreFile = File(context.filesDir, "test_preferences")
        if (testDataStoreFile.exists()) {
            testDataStoreFile.delete()
        }
    }
    
    @After
    fun tearDown() {
        if (testDataStoreFile.exists()) {
            testDataStoreFile.delete()
        }
    }
    
    @Test
    fun `should restore authentication state after process death`() = runTest {
        // === Phase 1: Avant process death ===
        
        val appPreferences1 = AppPreferences(context)
        val savedStateHandle1 = SavedStateHandle()
        val appState1 = MinimalIntegratedAppState(appPreferences1, savedStateHandle1)
        
        // Set authenticated state
        appState1.setAuthenticated(true)
        appState1.setHasPartner(true, "partner123")
        appState1.completeOnboarding()
        
        // Verify state before "death"
        assertEquals(Route.Main, appState1.currentRoute.first())
        
        // === Simulate process death ===
        // (Variables go out of scope, simulating process termination)
        
        // === Phase 2: Après process death (cold start) ===
        
        val appPreferences2 = AppPreferences(context) // New instance
        val savedStateHandle2 = SavedStateHandle() // New instance (empty)
        val appState2 = MinimalIntegratedAppState(appPreferences2, savedStateHandle2)
        
        // Verify state restoration
        assertEquals(Route.Main, appState2.currentRoute.first())
    }
    
    @Test
    fun `should restore route for unauthenticated user after process death`() = runTest {
        // === Phase 1: Set unauthenticated state ===
        
        val appPreferences1 = AppPreferences(context)
        val savedStateHandle1 = SavedStateHandle()
        val appState1 = MinimalIntegratedAppState(appPreferences1, savedStateHandle1)
        
        // Keep default unauthenticated state
        assertEquals(Route.Onboarding, appState1.currentRoute.first())
        
        // === Simulate process death ===
        
        // === Phase 2: Cold start ===
        
        val appPreferences2 = AppPreferences(context)
        val savedStateHandle2 = SavedStateHandle()
        val appState2 = MinimalIntegratedAppState(appPreferences2, savedStateHandle2)
        
        // Should still be onboarding
        assertEquals(Route.Onboarding, appState2.currentRoute.first())
    }
    
    @Test
    fun `should restore partner connection state after process death`() = runTest {
        // === Phase 1: Set authenticated but no partner ===
        
        val appPreferences1 = AppPreferences(context)
        val savedStateHandle1 = SavedStateHandle()
        val appState1 = MinimalIntegratedAppState(appPreferences1, savedStateHandle1)
        
        appState1.setAuthenticated(true)
        appState1.completeOnboarding()
        // Don't set partner
        
        assertEquals(Route.PartnerConnection, appState1.currentRoute.first())
        
        // === Simulate process death ===
        
        // === Phase 2: Cold start ===
        
        val appPreferences2 = AppPreferences(context)
        val savedStateHandle2 = SavedStateHandle()
        val appState2 = MinimalIntegratedAppState(appPreferences2, savedStateHandle2)
        
        // Should restore to partner connection
        assertEquals(Route.PartnerConnection, appState2.currentRoute.first())
    }
    
    @Test
    fun `should restore SavedStateHandle parameters after process death`() = runTest {
        // === Phase 1: Set parameters ===
        
        val appPreferences1 = AppPreferences(context)
        val savedStateHandle1 = SavedStateHandle().apply {
            set("current_day", 7)
            set("selected_category", "romance")
        }
        val appState1 = MinimalIntegratedAppState(appPreferences1, savedStateHandle1)
        
        assertEquals(7, appState1.currentDay)
        assertEquals("romance", appState1.selectedCategory)
        
        // === Simulate process death ===
        
        // === Phase 2: Cold start with restored SavedStateHandle ===
        
        val appPreferences2 = AppPreferences(context)
        val savedStateHandle2 = SavedStateHandle().apply {
            // Simulate Android restoration
            set("current_day", 7)
            set("selected_category", "romance")
        }
        val appState2 = MinimalIntegratedAppState(appPreferences2, savedStateHandle2)
        
        // Parameters should be restored
        assertEquals(7, appState2.currentDay)
        assertEquals("romance", appState2.selectedCategory)
    }
    
    @Test
    fun `should handle partial state restoration gracefully`() = runTest {
        // === Phase 1: Set full state ===
        
        val appPreferences1 = AppPreferences(context)
        val savedStateHandle1 = SavedStateHandle()
        val appState1 = MinimalIntegratedAppState(appPreferences1, savedStateHandle1)
        
        appState1.setAuthenticated(true)
        appState1.setHasPartner(true, "partner123")
        appState1.completeOnboarding()
        appState1.currentDay = 5
        
        // === Simulate partial data loss (SavedStateHandle lost, DataStore intact) ===
        
        val appPreferences2 = AppPreferences(context) // DataStore intact
        val savedStateHandle2 = SavedStateHandle() // Lost parameters
        val appState2 = MinimalIntegratedAppState(appPreferences2, savedStateHandle2)
        
        // Route should be restored from DataStore
        assertEquals(Route.Main, appState2.currentRoute.first())
        
        // Parameters should use defaults
        assertEquals(1, appState2.currentDay) // Default value
        assertEquals(null, appState2.selectedCategory) // Default value
    }
}
