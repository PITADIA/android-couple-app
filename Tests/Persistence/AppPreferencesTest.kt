package com.love2loveapp.tests.persistence

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.love2loveapp.data.persistence.AppPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Tests pour AppPreferences
 * Vérifie la persistance des états critiques
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AppPreferencesTest {
    
    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences
    private lateinit var testDataStoreFile: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Crée un DataStore temporaire pour les tests
        testDataStoreFile = File(context.filesDir, "test_preferences")
        if (testDataStoreFile.exists()) {
            testDataStoreFile.delete()
        }
        
        appPreferences = AppPreferences(context)
    }
    
    @After
    fun tearDown() {
        if (testDataStoreFile.exists()) {
            testDataStoreFile.delete()
        }
    }
    
    @Test
    fun `should persist authentication state`() = runTest {
        // Given
        assertFalse(appPreferences.isAuthenticated.first())
        
        // When
        appPreferences.setAuthenticated(true)
        
        // Then
        assertTrue(appPreferences.isAuthenticated.first())
    }
    
    @Test
    fun `should persist partner state`() = runTest {
        // Given
        assertFalse(appPreferences.hasPartner.first())
        assertNull(appPreferences.partnerId.first())
        
        // When
        appPreferences.setHasPartner(true)
        appPreferences.setPartnerId("partner123")
        
        // Then
        assertTrue(appPreferences.hasPartner.first())
        assertEquals("partner123", appPreferences.partnerId.first())
    }
    
    @Test
    fun `should persist onboarding completion`() = runTest {
        // Given
        assertFalse(appPreferences.onboardingCompleted.first())
        
        // When
        appPreferences.setOnboardingCompleted(true)
        
        // Then
        assertTrue(appPreferences.onboardingCompleted.first())
    }
    
    @Test
    fun `should persist user ID`() = runTest {
        // Given
        assertNull(appPreferences.userId.first())
        
        // When
        appPreferences.setUserId("user456")
        
        // Then
        assertEquals("user456", appPreferences.userId.first())
    }
    
    @Test
    fun `should persist last route`() = runTest {
        // Given
        assertNull(appPreferences.lastRoute.first())
        
        // When
        appPreferences.setLastRoute("main")
        
        // Then
        assertEquals("main", appPreferences.lastRoute.first())
    }
    
    @Test
    fun `should clear all preferences`() = runTest {
        // Given - Set some preferences
        appPreferences.setAuthenticated(true)
        appPreferences.setHasPartner(true)
        appPreferences.setPartnerId("partner123")
        appPreferences.setOnboardingCompleted(true)
        appPreferences.setUserId("user456")
        
        // When
        appPreferences.clearAll()
        
        // Then
        assertFalse(appPreferences.isAuthenticated.first())
        assertFalse(appPreferences.hasPartner.first())
        assertNull(appPreferences.partnerId.first())
        assertFalse(appPreferences.onboardingCompleted.first())
        assertNull(appPreferences.userId.first())
    }
    
    @Test
    fun `should clear only user data`() = runTest {
        // Given
        appPreferences.setAuthenticated(true)
        appPreferences.setHasPartner(true)
        appPreferences.setPartnerId("partner123")
        appPreferences.setOnboardingCompleted(true)
        appPreferences.setUserId("user456")
        
        // When
        appPreferences.clearUserData()
        
        // Then
        assertFalse(appPreferences.isAuthenticated.first())
        assertFalse(appPreferences.hasPartner.first())
        assertNull(appPreferences.partnerId.first())
        assertNull(appPreferences.userId.first())
        // Onboarding completion should remain
        assertTrue(appPreferences.onboardingCompleted.first())
    }
    
    @Test
    fun `should handle null values correctly`() = runTest {
        // Given
        appPreferences.setUserId("user123")
        appPreferences.setPartnerId("partner456")
        
        // When - Set to null
        appPreferences.setUserId(null)
        appPreferences.setPartnerId(null)
        
        // Then
        assertNull(appPreferences.userId.first())
        assertNull(appPreferences.partnerId.first())
    }
}
