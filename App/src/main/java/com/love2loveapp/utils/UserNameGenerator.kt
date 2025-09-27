package com.love2loveapp.utils

import java.util.*

/**
 * 🎯 UserNameGenerator - Génération automatique de noms d'utilisateur
 * 
 * Équivalent Android du mécanisme iOS pour générer des noms automatiques
 * quand l'utilisateur ne fournit pas de nom lors de l'onboarding.
 * 
 * Format généré :
 * - Français : "Utilisateur" + 4 premiers caractères UUID (ex: "UtilisateurA3F2")
 * - Anglais/Autre : "User" + 4 premiers caractères UUID (ex: "UserA3F2")
 */
object UserNameGenerator {
    
    /**
     * Génère un nom automatique selon les règles iOS
     * @param userId L'UUID de l'utilisateur (utilisé pour extraire les 4 premiers caractères)
     * @param languageCode Code langue pour la localisation (optionnel, détecte automatiquement)
     * @return Nom généré automatiquement
     */
    fun generateAutomaticName(userId: String, languageCode: String? = null): String {
        // Détecter la langue système si pas fournie
        val detectedLanguage = languageCode ?: Locale.getDefault().language
        
        // Extraire 4 premiers caractères de l'UUID (comme iOS)
        val shortId = userId.take(4).uppercase()
        
        // Générer selon la langue
        val generatedName = if (detectedLanguage.startsWith("fr")) {
            "Utilisateur$shortId"  // 🇫🇷 Français
        } else {
            "User$shortId"         // 🇺🇸 Anglais/Autre (fallback)
        }
        
        android.util.Log.d("UserNameGenerator", "✅ Génération: '$generatedName' (langue: $detectedLanguage)")
        
        return generatedName
    }
    
    /**
     * Vérifie si un nom est vide (selon les règles iOS)
     * @param name Le nom à vérifier
     * @return true si le nom est considéré comme vide
     */
    fun isNameEmpty(name: String?): Boolean {
        return name.isNullOrBlank() || name.trim().isEmpty()
    }
    
    /**
     * Traite un nom d'entrée et retourne soit le nom original soit un nom généré
     * @param inputName Le nom fourni par l'utilisateur
     * @param userId L'UUID de l'utilisateur
     * @param languageCode Code langue (optionnel)
     * @return Le nom final à utiliser
     */
    fun processUserName(inputName: String?, userId: String, languageCode: String? = null): String {
        return if (isNameEmpty(inputName)) {
            generateAutomaticName(userId, languageCode)
        } else {
            inputName!!.trim()
        }
    }
    
    /**
     * Génère un UUID simple pour les tests
     * Format : 8-4-4-4-12 caractères
     */
    fun generateUserId(): String {
        return UUID.randomUUID().toString()
    }
}
