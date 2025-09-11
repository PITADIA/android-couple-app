package com.love2loveapp.tests.backend

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.love2loveapp.core.common.Result
import com.love2loveapp.core.services.billing.GooglePlayBillingService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.Task
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests pour GooglePlayBillingService
 * Vérifie la validation des achats Google Play
 */
@ExperimentalCoroutinesApi
class GooglePlayValidationTest {
    
    private lateinit var functions: FirebaseFunctions
    private lateinit var billingService: GooglePlayBillingService
    
    @Before
    fun setup() {
        functions = mockk(relaxed = true)
        billingService = GooglePlayBillingService(mockk(), functions)
    }
    
    @Test
    fun `should validate successful Google Play purchase`() = runTest {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "test_purchase_token"
        
        val mockResponse = mapOf(
            "success" to true,
            "isSubscribed" to true,
            "expiresDate" to "2024-12-31T23:59:59.000Z"
        )
        
        val mockResult = mockk<HttpsCallableResult> {
            every { data } returns mockResponse
        }
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns true
            every { result } returns mockResult
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call(any()) } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("validateGooglePurchase") } returns mockCallable
        
        // When
        val result = billingService.validateGooglePurchase(productId, purchaseToken)
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertTrue(response.success)
        assertTrue(response.isSubscribed)
        assertEquals("2024-12-31T23:59:59.000Z", response.expiresDate)
    }
    
    @Test
    fun `should handle failed Google Play purchase validation`() = runTest {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "invalid_token"
        
        val mockResponse = mapOf(
            "success" to false,
            "isSubscribed" to false,
            "expiresDate" to null
        )
        
        val mockResult = mockk<HttpsCallableResult> {
            every { data } returns mockResponse
        }
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns true
            every { result } returns mockResult
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call(any()) } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("validateGooglePurchase") } returns mockCallable
        
        // When
        val result = billingService.validateGooglePurchase(productId, purchaseToken)
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertFalse(response.success)
        assertFalse(response.isSubscribed)
        assertEquals(null, response.expiresDate)
    }
    
    @Test
    fun `should handle network error during validation`() = runTest {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "test_token"
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns false
            every { exception } returns RuntimeException("Network error")
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call(any()) } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("validateGooglePurchase") } returns mockCallable
        
        // When
        val result = billingService.validateGooglePurchase(productId, purchaseToken)
        
        // Then
        assertTrue(result is Result.Error)
    }
    
    @Test
    fun `should check subscription status successfully`() = runTest {
        // Given
        val mockResponse = mapOf(
            "isSubscribed" to true,
            "subscriptionType" to "direct",
            "platform" to "android",
            "expiresDate" to "2024-12-31T23:59:59.000Z"
        )
        
        val mockResult = mockk<HttpsCallableResult> {
            every { data } returns mockResponse
        }
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns true
            every { result } returns mockResult
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call() } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("checkSubscriptionStatus") } returns mockCallable
        
        // When
        val result = billingService.checkSubscriptionStatus()
        
        // Then
        assertTrue(result is Result.Success)
        val status = (result as Result.Success).data
        assertTrue(status.isSubscribed)
        assertEquals("direct", status.subscriptionType)
        assertEquals("android", status.platform)
        assertEquals("2024-12-31T23:59:59.000Z", status.expiresDate)
    }
    
    @Test
    fun `should handle rate limiting error`() = runTest {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "test_token"
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns false
            every { exception } returns com.google.firebase.functions.FirebaseFunctionsException(
                "resource-exhausted", 
                "Rate limit exceeded",
                null,
                null
            )
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call(any()) } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("validateGooglePurchase") } returns mockCallable
        
        // When
        val result = billingService.validateGooglePurchase(productId, purchaseToken)
        
        // Then
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).exception
        assertTrue(error.message?.contains("validation failed") == true)
    }
    
    @Test
    fun `should handle invalid response format`() = runTest {
        // Given
        val productId = "premium_monthly"
        val purchaseToken = "test_token"
        
        // Response with wrong format
        val mockResult = mockk<HttpsCallableResult> {
            every { data } returns "invalid_string_response"
        }
        
        val mockTask = mockk<Task<HttpsCallableResult>> {
            every { isComplete } returns true
            every { isSuccessful } returns true
            every { result } returns mockResult
        }
        
        val mockCallable = mockk<com.google.firebase.functions.HttpsCallableReference> {
            every { call(any()) } returns mockTask
        }
        
        coEvery { functions.getHttpsCallable("validateGooglePurchase") } returns mockCallable
        
        // When
        val result = billingService.validateGooglePurchase(productId, purchaseToken)
        
        // Then
        assertTrue(result is Result.Error)
        val error = (result as Result.Error).exception
        assertTrue(error.message?.contains("Invalid response format") == true)
    }
}
