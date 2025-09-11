package com.love2loveapp.model

import com.love2loveapp.core.constants.*

/**
 * ===============================
 * Point d'entrée unique pour toutes les constantes (Pattern Barrel)
 * Ré-exporte tous les fichiers de constantes par domaine
 * ===============================
 */
object AppConstants {
    
    // === Dates & Fuseaux ===
    const val DEFAULT_TIMEZONE = "Europe/Paris"
    const val UTC_TIMEZONE = "UTC"
    const val DATE_FORMAT_YMD = "yyyy-MM-dd"
    const val DATE_FORMAT_FULL = "yyyy-MM-dd HH:mm:ss"
    
    // === Base de données ===
    const val REALM_DB_NAME = "love2love.realm"
    const val REALM_SCHEMA_VERSION = 2L
    
    // === Notifications ===
    const val DEFAULT_NOTIFICATION_HOUR = 21 // 21h00
    const val NOTIFICATION_EXPIRY_HOURS = 24
    
    // === Freemium ===
    const val FREE_TRIAL_DAYS = 3
    const val PRELOAD_PRIORITY_QUESTIONS = 32
    
    // === Collections Firestore ===
    object Firestore {
        const val USERS = "users"
        const val DAILY_QUESTIONS = "daily_questions"
        const val DAILY_CHALLENGES = "daily_challenges"
        const val QUESTION_RESPONSES = "responses"
        const val NOTIFICATION_AUDIT = "notification_audit"
        const val SECURITY_EVENTS = "security_events"
    }
    
    // === Clés SharedPreferences ===
    object Prefs {
        const val FCM_PREFS = "fcm_prefs"
        const val STASHED_FCM_TOKEN = "stashed_fcm_token"
        const val WIDGET_DATA = "widget_data"
        const val LAST_WIDGET_UPDATE = "last_widget_update"
        const val HAS_SEEN_INTRO = "has_seen_intro"
        const val ONBOARDING_PROGRESS = "onboarding_progress"
    }
    
    // === IDs de produits RevenueCat ===
    object Products {
        const val WEEKLY_SUBSCRIPTION = "com.love2loveapp.subscription.weekly"
        const val MONTHLY_SUBSCRIPTION = "com.love2loveapp.subscription.monthly"
    }
    
    // === Deep Links ===
    object DeepLinks {
        const val SCHEME = "coupleapp"
        const val SUBSCRIPTION_HOST = "subscription"
    }
    
    // === Logging ===
    object Tags {
        const val APP_DELEGATE = "AppDelegate"
        const val DAILY_QUESTION = "DailyQuestionModels"
        const val FIREBASE_MSG = "MyFirebaseMsgService"
        const val BADGE_MANAGER = "BadgeManager"
        const val WIDGET_PROVIDER = "Love2LoveWidget"
        const val CACHE_MANAGER = "RealmManager"
        const val APP_CHECK = "AppCheckConfig"
        const val CONNECTION_CONFIG = "ConnectionConfig"
    }
    
    // === Widgets ===
    object Widgets {
        const val UPDATE_INTERVAL_MINUTES = 30
        const val PROFILE_CIRCLE_SIZE_DP = 40
        const val PROFILE_CIRCLE_SMALL_SIZE_DP = 24
        const val DAYS_TOGETHER_MAX_DISPLAY = 999999
    }
    
    // === Validation ===
    object Validation {
        const val MIN_USER_NAME_LENGTH = 2
        const val MAX_USER_NAME_LENGTH = 50
        const val MIN_RESPONSE_LENGTH = 1
        const val MAX_RESPONSE_LENGTH = 1000
        const val PARTNER_CODE_LENGTH = 6
    }
    
    // === Migration ===
    object Migration {
        val CATEGORY_KEY_MAPPING = mapOf(
            "En couple" to "en-couple",
            "Désirs Inavoués" to "les-plus-hots",
            "Pour rigoler à deux" to "pour-rire-a-deux",
            "Des questions profondes" to "questions-profondes",
            "À travers la distance" to "a-distance",
            "Tu préfères quoi ?" to "tu-preferes",
            "Réparer notre amour" to "mieux-ensemble",
            "En date" to "pour-un-date"
        )
        
        val PRIORITY_CATEGORIES = listOf("en-couple")
    }
    
    // ===============================
    // Ré-export des constantes par domaine (Pattern Barrel)
    // ===============================
    
    // === Performance ===
    object Performance {
        const val DEFAULT_MONITOR_INTERVAL_MS = PerformanceConstants.DEFAULT_MONITOR_INTERVAL_MS
        const val HIGH_MEMORY_THRESHOLD_MB = PerformanceConstants.HIGH_MEMORY_THRESHOLD_MB
        const val SLOW_OPERATION_THRESHOLD_MS = PerformanceConstants.SLOW_OPERATION_THRESHOLD_MS
        const val CACHE_MEMORY_CLEANUP_THRESHOLD = PerformanceConstants.CACHE_MEMORY_CLEANUP_THRESHOLD
        const val CACHE_MEMORY_KEEP_COUNT = PerformanceConstants.CACHE_MEMORY_KEEP_COUNT
        const val QUESTION_SEARCH_LIMIT = PerformanceConstants.QUESTION_SEARCH_LIMIT
        const val BATCH_SIZE_FIRESTORE = PerformanceConstants.BATCH_SIZE_FIRESTORE
    }
    
    // === Location Services ===
    object Location {
        const val UPDATE_INTERVAL_MS = LocationConstants.UPDATE_INTERVAL_MS
        const val MIN_UPDATE_INTERVAL_MS = LocationConstants.MIN_UPDATE_INTERVAL_MS
        const val IGNORE_DISTANCE_METERS = LocationConstants.IGNORE_DISTANCE_METERS
        const val LOCATION_REQUEST_CODE = LocationConstants.LOCATION_REQUEST_CODE
    }
    
    // === Security & Encryption ===
    object Security {
        const val ENCRYPTION_DISABLED_FOR_REVIEW = SecurityConstants.ENCRYPTION_DISABLED_FOR_REVIEW
        const val CURRENT_ENCRYPTION_VERSION = SecurityConstants.CURRENT_ENCRYPTION_VERSION
        const val ANDROID_KEYSTORE = SecurityConstants.ANDROID_KEYSTORE
        const val KEY_ALIAS = SecurityConstants.KEY_ALIAS
        const val TRANSFORMATION = SecurityConstants.TRANSFORMATION
        const val GCM_TAG_LENGTH = SecurityConstants.GCM_TAG_LENGTH
        const val IV_LENGTH_BYTES = SecurityConstants.IV_LENGTH_BYTES
        const val ALGORITHM_AES_GCM = SecurityConstants.ALGORITHM_AES_GCM
        const val KEY_LENGTH_BITS = SecurityConstants.KEY_LENGTH_BITS
        const val TAG_LENGTH_BITS = SecurityConstants.TAG_LENGTH_BITS
    }
    
    // === Cache Management ===
    object Cache {
        const val USER_CACHE_MAX_AGE_MS = CacheConstants.USER_CACHE_MAX_AGE_MS
        const val IMAGE_CACHE_QUALITY = CacheConstants.IMAGE_CACHE_QUALITY
        const val PROFILE_IMAGE_MAX_SIZE_KB = CacheConstants.PROFILE_IMAGE_MAX_SIZE_KB
        const val MAX_CACHED_QUESTIONS_PER_CATEGORY = CacheConstants.MAX_CACHED_QUESTIONS_PER_CATEGORY
        const val MIN_QUESTIONS_EN_COUPLE = CacheConstants.MIN_QUESTIONS_EN_COUPLE
        const val CACHE_CLEANUP_DAYS = CacheConstants.CACHE_CLEANUP_DAYS
        const val DAILY_QUESTIONS_RETENTION_DAYS = CacheConstants.DAILY_QUESTIONS_RETENTION_DAYS
        const val DAILY_CHALLENGES_DEFAULT_LIMIT = CacheConstants.DAILY_CHALLENGES_DEFAULT_LIMIT
    }
    
    // === Billing ===
    object Billing {
        const val CONNECTION_TIMEOUT_MS = 10_000L
        const val RETRY_DELAY_MS = 2_000L
    }
    
    // === Authentication ===
    object Auth {
        const val CREDENTIAL_THROTTLE_MS = 2_000L
        const val SIGN_IN_TIMEOUT_MS = 30_000L
    }
    
    // === Packages (Clean Architecture) ===
    object Packages {
        const val BASE = "com.love2loveapp"
        const val CORE = "$BASE.core"
        const val TESTS = "$CORE.tests"
        const val UTILS = "$CORE.utils"
        const val VIEWMODELS = "$CORE.viewmodels"
        const val SERVICES = "$CORE.services"
        const val UI = "$CORE.ui"
        const val REPOSITORY = "$CORE.repository"
        const val COMMON = "$CORE.common"
    }
    
    // === Testing ===
    object Testing {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        const val MOCK_DELAY_MS = 100L
        const val TEST_USER_ID = "test_user_123"
        const val TEST_PARTNER_ID = "test_partner_456"
        const val TEST_COUPLE_ID = "test_couple_789"
    }
    
    // === ViewModels ===
    object ViewModels {
        const val MINIMUM_LOADING_TIME_MS = 1_000L
        const val CACHE_PROTECTION_ENABLED = true
        const val AUTO_REFRESH_INTERVAL_MS = 30_000L
        const val STATE_PERSISTENCE_ENABLED = true
    }
    
    // === Freemium ===
    object Freemium {
        const val QUESTIONS_PER_PACK = 32
        const val FREE_PACKS_LIMIT = 2
        const val FREE_JOURNAL_ENTRIES_LIMIT = 5
        const val FREE_DAILY_QUESTION_DAYS = 3
        const val FREE_DAILY_CHALLENGE_DAYS = 3
        const val MAX_FREE_QUESTIONS_EN_COUPLE = FREE_PACKS_LIMIT * QUESTIONS_PER_PACK // 64
    }
    
    // === Onboarding ===
    object Onboarding {
        const val TOTAL_STEPS = 14
        const val DATA_COLLECTION_DELAY_MS = 2_000L
        const val COMPLETION_GUARD_ENABLED = true
        const val GOOGLE_NAME_FALLBACK_ENABLED = true
    }
    
    // === Connection Orchestrator ===
    object Connection {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val RETRY_DELAY_MS = 3_000L
        const val CACHE_PURGE_ENABLED = true
        const val MUTEX_ENABLED = true
    }
    
    // === UI Extensions ===
    object UIExtensions {
        const val BADGE_CHANNEL_ID = "app_badges_channel"
        const val BADGE_NOTIFICATION_ID = 0xBADC0DE
        const val PROFILE_CIRCLE_SIZE_DP = 40
        const val PROFILE_CIRCLE_SMALL_SIZE_DP = 24
        const val KEYBOARD_HIDE_DELAY_MS = 100L
    }
    
    // === Analytics Events ===
    object Analytics {
        const val ONBOARDING_STEP = "onboarding_etape"
        const val ONBOARDING_COMPLETE = "onboarding_complete"
        const val PAYWALL_DISPLAYED = "paywall_affiche"
        const val FREEMIUM_DAILY_CHALLENGE_ACCESSED = "freemium_daily_challenge_accessed"
        const val PAYWALL_VIEWED = "paywall_viewed"
        const val INTRO_CONTINUE = "intro_continue"
    }
}