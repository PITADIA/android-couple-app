package com.love2loveapp.tests.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.navigation.AppNavigator
import com.love2loveapp.navigation.NavigationManager
import com.love2loveapp.navigation.Route
import com.love2loveapp.ui.AppRoot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests d'intégration pour la navigation complète
 * Vérifie le flux complet : État → AppNavigator → NavigationManager → NavHost
 */
@ExperimentalCoroutinesApi
class NavigationIntegrationTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    private lateinit var navigationManager: NavigationManager
    private lateinit var appNavigator: AppNavigator
    private lateinit var appState: IntegratedAppState
    private lateinit var navController: TestNavHostController
    
    @Before
    fun setup() {
        navigationManager = NavigationManager()
        appNavigator = AppNavigator(navigationManager)
        appState = mockk(relaxed = true)
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
    }
    
    @Test
    fun `should navigate from splash to onboarding when not authenticated`() = runTest {
        // Given
        val routeFlow = MutableStateFlow<Route>(Route.Splash)
        every { appState.currentRoute } returns routeFlow
        
        composeTestRule.setContent {
            AppRoot(
                appState = appState,
                navigationManager = navigationManager,
                appNavigator = appNavigator,
                navController = navController
            )
        }
        
        // When - Change state to trigger navigation
        routeFlow.value = Route.Onboarding
        
        composeTestRule.waitForIdle()
        
        // Then
        assertEquals("onboarding", navController.currentDestination?.route)
    }
    
    @Test
    fun `should navigate to main when authenticated with partner`() = runTest {
        // Given
        val routeFlow = MutableStateFlow<Route>(Route.Splash)
        every { appState.currentRoute } returns routeFlow
        
        composeTestRule.setContent {
            AppRoot(
                appState = appState,
                navigationManager = navigationManager,
                appNavigator = appNavigator,
                navController = navController
            )
        }
        
        // When
        routeFlow.value = Route.Main
        
        composeTestRule.waitForIdle()
        
        // Then
        assertEquals("main", navController.currentDestination?.route)
    }
    
    @Test
    fun `should navigate to daily content with correct parameter`() = runTest {
        // Given
        val routeFlow = MutableStateFlow<Route>(Route.Splash)
        every { appState.currentRoute } returns routeFlow
        
        composeTestRule.setContent {
            AppRoot(
                appState = appState,
                navigationManager = navigationManager,
                appNavigator = appNavigator,
                navController = navController
            )
        }
        
        // When
        routeFlow.value = Route.Daily(7)
        
        composeTestRule.waitForIdle()
        
        // Then
        assertEquals("daily/{day}", navController.currentDestination?.route)
        assertEquals("7", navController.currentBackStackEntry?.arguments?.getString("day"))
    }
    
    @Test
    fun `should handle back navigation correctly`() = runTest {
        // Given
        val routeFlow = MutableStateFlow<Route>(Route.Main)
        every { appState.currentRoute } returns routeFlow
        
        composeTestRule.setContent {
            AppRoot(
                appState = appState,
                navigationManager = navigationManager,
                appNavigator = appNavigator,
                navController = navController
            )
        }
        
        composeTestRule.waitForIdle()
        
        // Navigate to settings
        routeFlow.value = Route.Settings
        composeTestRule.waitForIdle()
        
        // When - Navigate back
        navigationManager.goBack()
        composeTestRule.waitForIdle()
        
        // Then
        assertEquals("main", navController.currentDestination?.route)
    }
    
    @Test
    fun `should clear backstack when popUpToRoot is true`() = runTest {
        // Given - Start with multiple screens in backstack
        val routeFlow = MutableStateFlow<Route>(Route.Onboarding)
        every { appState.currentRoute } returns routeFlow
        
        composeTestRule.setContent {
            AppRoot(
                appState = appState,
                navigationManager = navigationManager,
                appNavigator = appNavigator,
                navController = navController
            )
        }
        
        composeTestRule.waitForIdle()
        
        // Navigate to settings (adds to backstack)
        routeFlow.value = Route.Settings
        composeTestRule.waitForIdle()
        
        // When - Navigate to main (should clear backstack)
        routeFlow.value = Route.Main
        composeTestRule.waitForIdle()
        
        // Then
        assertEquals("main", navController.currentDestination?.route)
        assertEquals(1, navController.backStack.value.size) // Only current destination
    }
}
