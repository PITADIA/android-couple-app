package com.love2loveapp.services.map

import android.util.Log
import com.love2loveapp.models.JournalCluster
import com.love2loveapp.models.JournalEntry
import com.love2loveapp.models.distanceTo

/**
 * 🧠 JournalClusteringService - Système de clustering adaptatif selon rapport iOS
 * Distance de clustering dynamique basée sur le niveau de zoom (500km → 1km)
 */
object JournalClusteringService {
    
    private const val TAG = "JournalClustering"
    
    /**
     * 🎯 Créer clusters stables avec distance adaptative (identique iOS)
     * @param entries Liste des événements avec géolocalisation
     * @param zoomLevel Niveau de zoom de la carte (plus élevé = plus zoomé)
     * @return Liste des clusters avec IDs stables
     */
    fun createStableClusters(
        entries: List<JournalEntry>, 
        zoomLevel: Float
    ): List<JournalCluster> {
        if (entries.isEmpty()) return emptyList()
        
        // 📏 Distance de clustering basée sur le niveau de zoom (identique iOS)
        val clusterDistance = when {
            zoomLevel > 15.0f -> 500.0  // Vue monde = 500km
            zoomLevel > 10.0f -> 200.0  // Vue continent = 200km  
            zoomLevel > 5.0f -> 100.0   // Vue pays = 100km
            zoomLevel > 2.0f -> 50.0    // Vue région = 50km
            zoomLevel > 1.0f -> 25.0    // Vue département = 25km
            zoomLevel > 0.5f -> 15.0    // Vue ville = 15km
            zoomLevel > 0.2f -> 5.0     // Vue quartier = 5km
            else -> 1.0                 // Vue détaillée = 1km
        }
        
        val clusters = mutableListOf<JournalCluster>()
        val processedEntries = mutableSetOf<String>()
        
        // 🔄 Algorithme de clustering géographique (identique iOS)
        for (entry in entries) {
            if (processedEntries.contains(entry.id)) continue
            
            val location = entry.location ?: continue
            
            // Trouver tous les événements proches
            val nearbyEntries = mutableListOf(entry)
            processedEntries.add(entry.id)
            
            for (otherEntry in entries) {
                if (processedEntries.contains(otherEntry.id)) continue
                
                val otherLocation = otherEntry.location ?: continue
                
                // Calculer la distance réelle en kilomètres
                val distance = location.coordinate.distanceTo(otherLocation.coordinate)
                
                if (distance < clusterDistance) {
                    nearbyEntries.add(otherEntry)
                    processedEntries.add(otherEntry.id)
                }
            }
            
            // 🎯 Créer le cluster avec position centrale et ID stable
            val centerCoordinate = JournalCluster.calculateCenterCoordinate(nearbyEntries)
            val stableId = JournalCluster.createId(nearbyEntries)
            
            val cluster = JournalCluster(
                id = stableId,
                coordinate = centerCoordinate,
                entries = nearbyEntries.sortedByDescending { it.eventDate }
            )
            
            clusters.add(cluster)
        }
        
        return clusters
    }
    
    /**
     * 📊 Calculer les statistiques géographiques (identique iOS)
     */
    data class MapStatistics(
        val totalEvents: Int,
        val uniqueCities: Int,
        val uniqueCountries: Int
    )
    
    fun calculateMapStatistics(entries: List<JournalEntry>): MapStatistics {
        val entriesWithLocation = entries.filter { it.hasLocation }
        
        val uniqueCities = entriesWithLocation
            .mapNotNull { it.location?.city }
            .toSet().size
        
        val uniqueCountries = entriesWithLocation
            .mapNotNull { it.location?.country }
            .toSet().size
        
        return MapStatistics(
            totalEvents = entriesWithLocation.size,
            uniqueCities = uniqueCities,
            uniqueCountries = uniqueCountries
        )
    }
}
