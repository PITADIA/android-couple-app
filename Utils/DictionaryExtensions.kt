// MapExtensions.kt
@file:Suppress("unused")

package com.love2loveapp.core.utils

/**
 * Transforme les clés d'une Map tout en conservant les valeurs.
 * Si plusieurs clés produisent la même nouvelle clé, la dernière écrase les précédentes
 * (même comportement que ton Swift et que Kotlin stdlib).
 */
inline fun <K, V, NK> Map<K, V>.mapKeys(transform: (K) -> NK): Map<NK, V> =
    entries.associate { transform(it.key) to it.value }

/**
 * Spécialisation pratique pour les clés String.
 */
inline fun <V> Map<String, V>.mapKeys(transform: (String) -> String): Map<String, V> =
    entries.associate { transform(it.key) to it.value }
