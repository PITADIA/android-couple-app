package com.love2loveapp.core.constants

/**
 * Constantes de sécurité et chiffrement
 */
object SecurityConstants {
    const val ENCRYPTION_DISABLED_FOR_REVIEW = true
    const val CURRENT_ENCRYPTION_VERSION = "2.0"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val KEY_ALIAS = "love2love_location_encryption_key"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val GCM_TAG_LENGTH = 128
    const val IV_LENGTH_BYTES = 12
    const val ALGORITHM_AES_GCM = "AES/GCM/NoPadding"
    const val KEY_LENGTH_BITS = 256
    const val TAG_LENGTH_BITS = 128
}
