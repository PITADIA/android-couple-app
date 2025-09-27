package com.love2loveapp.models

import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

/**
 * 🗺️ JournalCluster - Système de clustering intelligent selon rapport iOS
 * Modèle identique au système iOS avec IDs stables et position centrale calculée
 */
data class JournalCluster(
    val id: String,                         // ID stable basé sur les entrées
    val coordinate: LatLng,                 // Position centrale calculée du cluster
    val entries: List<JournalEntry>         // Liste des événements regroupés
) {
    val count: Int get() = entries.size
    val isCluster: Boolean get() = count > 1
    val firstEntry: JournalEntry get() = entries.first()
    
    companion object {
        /**
         * ✅ Créer un ID stable basé sur les IDs des entrées (identique iOS)
         * Évite les re-renderings inutiles lors du zoom
         */
        fun createId(entries: List<JournalEntry>): String {
            val sortedIds = entries.map { it.id }.sorted()
            return sortedIds.joinToString("-")
        }
        
        /**
         * 🎯 Calculer le centre géographique du cluster (identique iOS)
         * Retourne la position moyenne des coordonnées
         */
        fun calculateCenterCoordinate(entries: List<JournalEntry>): LatLng {
            val coordinates = entries.mapNotNull { it.location?.coordinate }
            
            if (coordinates.size == 1) {
                return coordinates.first()
            }
            
            val totalLat = coordinates.sumOf { it.latitude }
            val totalLon = coordinates.sumOf { it.longitude }
            
            return LatLng(
                totalLat / coordinates.size,
                totalLon / coordinates.size
            )
        }
    }
}

/**
 * 📏 Extension pour calculer la distance entre deux coordonnées (identique iOS)
 * Utilise la formule haversine pour distance réelle en kilomètres
 */
fun LatLng.distanceTo(other: LatLng): Double {
    val earthRadius = 6371.0 // Rayon terrestre en kilomètres
    
    val lat1Rad = Math.toRadians(this.latitude)
    val lat2Rad = Math.toRadians(other.latitude)
    val deltaLatRad = Math.toRadians(other.latitude - this.latitude)
    val deltaLonRad = Math.toRadians(other.longitude - this.longitude)
    
    val a = sin(deltaLatRad / 2).pow(2) + 
            cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
    
    val c = 2 * asin(sqrt(a))
    
    return earthRadius * c
}
