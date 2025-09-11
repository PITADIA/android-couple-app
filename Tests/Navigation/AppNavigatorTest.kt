package com.love2loveapp.tests.navigation

import com.love2loveapp.core.viewmodels.IntegratedAppState
import com.love2loveapp.navigation.AppNavigator
import com.love2loveapp.navigation.NavCommand
import com.love2loveapp.navigation.NavigationManager
import com.love2loveapp.navigation.Route
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Tests pour AppNavigator
 * Vérifie la traduction état → navigation
 */
@ExperimentalCoroutinesApi
class AppNavigatorTest {
    
    private lateinit var appNavigator: AppNavigator
    private lateinit var navigationManager: NavigationManager
    private lateinit var appState: IntegratedAppState
    private lateinit var testScope: TestScope
    
    @Before
    fun setup() {
        navigationManager = mockk(relaxed = true)
        appState = mockk(relaxed = true)
        appNavigator = AppNavigator(navigationManager)
        testScope = TestScope()
    }
    
    @Test
    fun `should navigate to onboarding when not authenticated`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.Onboarding)
        coEvery { appState.currentRoute } returns routeFlow
        
        // When
        appNavigator.bind(appState, testScope)
        
        // Then
        coVerify { 
            navigationManager.go(NavCommand.To("onboarding", popUpToRoot = true))
        }
    }
    
    @Test
    fun `should navigate to main when authenticated and has partner`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.Main)
        coEvery { appState.currentRoute } returns routeFlow
        
        // When
        appNavigator.bind(appState, testScope)
        
        // Then
        coVerify { 
            navigationManager.go(NavCommand.To("main", popUpToRoot = true))
        }
    }
    
    @Test
    fun `should navigate to partner connection when authenticated but no partner`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.PartnerConnection)
        coEvery { appState.currentRoute } returns routeFlow
        
        // When
        appNavigator.bind(appState, testScope)
        
        // Then
        coVerify { 
            navigationManager.go(NavCommand.To("partner_connection", popUpToRoot = true))
        }
    }
    
    @Test
    fun `should navigate to daily content with day parameter`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.Daily(5))
        coEvery { appState.currentRoute } returns routeFlow
        
        // When
        appNavigator.bind(appState, testScope)
        
        // Then
        coVerify { 
            navigationManager.go(NavCommand.To("daily/5"))
        }
    }
    
    @Test
    fun `should not navigate on splash route`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.Splash)
        coEvery { appState.currentRoute } returns routeFlow
        
        // When
        appNavigator.bind(appState, testScope)
        
        // Then
        coVerify(exactly = 0) { navigationManager.go(any()) }
    }
    
    @Test
    fun `should only bind once`() = runTest {
        // Given
        val routeFlow = MutableStateFlow(Route.Main)
        coEvery { appState.currentRoute } returns routeFlow
        
        // When - Bind multiple times
        appNavigator.bind(appState, testScope)
        appNavigator.bind(appState, testScope)
        appNavigator.bind(appState, testScope)
        
        // Then - Should only bind once
        coVerify(exactly = 1) { 
            navigationManager.go(NavCommand.To("main", popUpToRoot = true))
        }
    }
}
