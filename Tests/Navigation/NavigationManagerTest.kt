package com.love2loveapp.tests.navigation

import com.love2loveapp.navigation.NavCommand
import com.love2loveapp.navigation.NavigationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests pour NavigationManager
 * Vérifie les événements one-shot et l'absence de replay
 */
@ExperimentalCoroutinesApi
class NavigationManagerTest {
    
    private lateinit var navigationManager: NavigationManager
    
    @Before
    fun setup() {
        navigationManager = NavigationManager()
    }
    
    @Test
    fun `should emit navigation command`() = runTest {
        // Given
        val expectedCommand = NavCommand.To("test_route")
        
        // When
        navigationManager.go(expectedCommand)
        
        // Then
        val emittedCommand = navigationManager.events.first()
        assertEquals(expectedCommand, emittedCommand)
    }
    
    @Test
    fun `should emit navigateTo command with correct parameters`() = runTest {
        // Given
        val route = "main"
        val popUpToRoot = true
        
        // When
        navigationManager.navigateTo(route, popUpToRoot)
        
        // Then
        val emittedCommand = navigationManager.events.first()
        assertTrue(emittedCommand is NavCommand.To)
        assertEquals(route, (emittedCommand as NavCommand.To).route)
        assertEquals(popUpToRoot, emittedCommand.popUpToRoot)
    }
    
    @Test
    fun `should emit back command`() = runTest {
        // When
        navigationManager.goBack()
        
        // Then
        val emittedCommand = navigationManager.events.first()
        assertEquals(NavCommand.Back, emittedCommand)
    }
    
    @Test
    fun `should emit replace command`() = runTest {
        // Given
        val route = "replacement_route"
        
        // When
        navigationManager.replaceCurrent(route)
        
        // Then
        val emittedCommand = navigationManager.events.first()
        assertTrue(emittedCommand is NavCommand.Replace)
        assertEquals(route, (emittedCommand as NavCommand.Replace).route)
    }
    
    @Test
    fun `should not replay events`() = runTest {
        // Given - Émission d'un premier événement
        navigationManager.go(NavCommand.To("first_route"))
        navigationManager.events.first() // Consommé
        
        // When - Émission d'un deuxième événement
        navigationManager.go(NavCommand.To("second_route"))
        
        // Then - Seul le deuxième événement est reçu
        val secondCommand = navigationManager.events.first()
        assertEquals(NavCommand.To("second_route"), secondCommand)
    }
}
