package com.love2loveapp.core.constants

/**
 * Constantes de performance et monitoring
 */
object PerformanceConstants {
    const val DEFAULT_MONITOR_INTERVAL_MS = 5_000L
    const val HIGH_MEMORY_THRESHOLD_MB = 200.0
    const val SLOW_OPERATION_THRESHOLD_MS = 100.0
    const val CACHE_MEMORY_CLEANUP_THRESHOLD = 500
    const val CACHE_MEMORY_KEEP_COUNT = 300
    const val QUESTION_SEARCH_LIMIT = 1000
    const val BATCH_SIZE_FIRESTORE = 50
}
