package com.love2loveapp.core.constants

/**
 * Constantes pour la gestion du cache
 */
object CacheConstants {
    const val USER_CACHE_MAX_AGE_MS = 7L * 24 * 3600 * 1000 // 7 days
    const val IMAGE_CACHE_QUALITY = 80
    const val PROFILE_IMAGE_MAX_SIZE_KB = 500
    const val MAX_CACHED_QUESTIONS_PER_CATEGORY = 100
    const val MIN_QUESTIONS_EN_COUPLE = 64
    const val CACHE_CLEANUP_DAYS = 7
    const val DAILY_QUESTIONS_RETENTION_DAYS = 30
    const val DAILY_CHALLENGES_DEFAULT_LIMIT = 30
}
