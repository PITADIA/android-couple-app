package com.love2loveapp.models

/**
 * Constantes de l'application Love2Love - Version simplifiée
 */
object AppConstants {
    
    // === Configuration générale ===
    const val APP_NAME = "Love2Love"
    const val APP_VERSION = "1.0.0"
    
    // === URLs et endpoints ===
    const val BASE_URL = "https://love2love.app"
    const val PRIVACY_POLICY_URL = "https://love2love.app/privacy"
    const val TERMS_OF_SERVICE_URL = "https://love2love.app/terms"
    
    // === Firebase ===
    const val FIREBASE_PROJECT_ID = "love2love-app"
    
    // === IDs de produits RevenueCat (iOS) ===
    object Products {
        const val WEEKLY_SUBSCRIPTION = "com.love2loveapp.subscription.weekly"
        const val MONTHLY_SUBSCRIPTION = "com.love2loveapp.subscription.monthly"
    }
    
    // === IDs de produits Google Play (Android) ===
    // ⚠️ IMPORTANT: Ces IDs doivent correspondre exactement aux IDs configurés dans Google Play Console
    object AndroidProducts {
        const val WEEKLY_SUBSCRIPTION = "love2love_weekly"
        const val MONTHLY_SUBSCRIPTION = "love2love_monthly"
        
        // Liste pour validation
        val ALL_SUBSCRIPTION_IDS = listOf(WEEKLY_SUBSCRIPTION, MONTHLY_SUBSCRIPTION)
    }
    
    // === Configuration UI ===
    object UI {
        const val ANIMATION_DURATION_MS = 300
        const val SPLASH_SCREEN_DELAY_MS = 2000
        const val TOAST_DURATION_MS = 3000
    }
    
    // === Performance ===
    object Performance {
        const val MAX_QUESTIONS_PER_BATCH = 50
        const val CACHE_EXPIRY_HOURS = 24
        const val MAX_RETRY_ATTEMPTS = 3
        const val NETWORK_TIMEOUT_SECONDS = 30
        const val MAX_IMAGE_SIZE_MB = 10
    }
    
    // === Localisation ===
    object Location {
        const val UPDATE_INTERVAL_MS = 10000L
        const val FASTEST_INTERVAL_MS = 5000L
        const val MAX_WAIT_TIME_MS = 60000L
        const val DISPLACEMENT_THRESHOLD_M = 10.0f
    }
}