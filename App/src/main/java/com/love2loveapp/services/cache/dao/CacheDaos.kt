package com.love2loveapp.services.cache.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.love2loveapp.services.cache.entities.DailyQuestionEntity
import com.love2loveapp.services.cache.entities.DailyChallengeEntity
import com.love2loveapp.services.cache.entities.FavoriteQuestionEntity

/**
 * 📱 DAOs Room pour Cache Sophistiqué Android
 * 
 * Équivalent des requêtes Realm iOS:
 * - Performance optimisée avec indices
 * - Requêtes asynchrones et LiveData
 * - Gestion automatique des conflits
 * - Nettoyage intelligent par âge
 */

// =======================
// DAILY QUESTIONS DAO
// =======================

@Dao
interface DailyQuestionsDao {
    
    /**
     * Récupère la question du jour pour un couple et une date
     * Équivalent de la requête Realm filter("coupleId == %@ AND scheduledDate == %@")
     */
    @Query("SELECT * FROM daily_questions WHERE coupleId = :coupleId AND scheduledDate = :date LIMIT 1")
    suspend fun getDailyQuestion(coupleId: String, date: String): DailyQuestionEntity?
    
    /**
     * Récupère toutes les questions en cache pour un couple (dernières en premier)
     * Équivalent de la requête Realm sorted(byKeyPath: "scheduledDate", ascending: false)
     */
    @Query("SELECT * FROM daily_questions WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    suspend fun getCachedDailyQuestions(coupleId: String, limit: Int = 30): List<DailyQuestionEntity>
    
    /**
     * LiveData pour observation temps réel (équivalent @Published iOS)
     */
    @Query("SELECT * FROM daily_questions WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    fun getCachedDailyQuestionsLiveData(coupleId: String, limit: Int = 30): LiveData<List<DailyQuestionEntity>>
    
    /**
     * Cache une question quotidienne (REPLACE pour mise à jour)
     * Équivalent de realm.add(realmQuestion, update: .modified)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDailyQuestion(question: DailyQuestionEntity)
    
    /**
     * Cache plusieurs questions en batch (performance)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDailyQuestions(questions: List<DailyQuestionEntity>)
    
    /**
     * Supprime les questions d'un couple (nettoyage)
     */
    @Query("DELETE FROM daily_questions WHERE coupleId = :coupleId")
    suspend fun clearQuestionsForCouple(coupleId: String)
    
    /**
     * Nettoyage intelligent par âge (équivalent compactage Realm iOS)
     * Supprime les questions plus anciennes que la limite
     */
    @Query("DELETE FROM daily_questions WHERE cached_at < :cutoffTime")
    suspend fun cleanupOldQuestions(cutoffTime: Long)
    
    /**
     * Compte le nombre de questions en cache
     */
    @Query("SELECT COUNT(*) FROM daily_questions WHERE coupleId = :coupleId")
    suspend fun getQuestionCount(coupleId: String): Int
}

// =======================
// DAILY CHALLENGES DAO  
// =======================

@Dao
interface DailyChallengesDao {
    
    /**
     * Récupère le défi du jour pour un couple et une date
     * Utilise des timestamps pour gérer les plages de dates
     */
    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId AND scheduledDate >= :startOfDay AND scheduledDate < :endOfDay LIMIT 1")
    suspend fun getDailyChallenge(coupleId: String, startOfDay: Long, endOfDay: Long): DailyChallengeEntity?
    
    /**
     * Récupère tous les défis en cache pour un couple
     */
    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    suspend fun getCachedDailyChallenges(coupleId: String, limit: Int = 30): List<DailyChallengeEntity>
    
    /**
     * LiveData pour observation temps réel
     */
    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId ORDER BY scheduledDate DESC LIMIT :limit")
    fun getCachedDailyChallengesLiveData(coupleId: String, limit: Int = 30): LiveData<List<DailyChallengeEntity>>
    
    /**
     * Cache un défi quotidien
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheDailyChallenge(challenge: DailyChallengeEntity)
    
    /**
     * Met à jour un défi (pour marquer comme complété)
     * Équivalent de realm.write { challenge.isCompleted = true }
     */
    @Update
    suspend fun updateDailyChallenge(challenge: DailyChallengeEntity)
    
    /**
     * Marque un défi comme complété
     */
    @Query("UPDATE daily_challenges SET isCompleted = 1, completed_at = :completedAt WHERE id = :challengeId")
    suspend fun markChallengeCompleted(challengeId: String, completedAt: Long = System.currentTimeMillis())
    
    /**
     * Récupère les défis complétés pour statistiques
     */
    @Query("SELECT * FROM daily_challenges WHERE coupleId = :coupleId AND isCompleted = 1 ORDER BY completed_at DESC")
    suspend fun getCompletedChallenges(coupleId: String): List<DailyChallengeEntity>
    
    /**
     * Supprime les défis d'un couple
     */
    @Query("DELETE FROM daily_challenges WHERE coupleId = :coupleId")
    suspend fun clearChallengesForCouple(coupleId: String)
    
    /**
     * Nettoyage par âge
     */
    @Query("DELETE FROM daily_challenges WHERE cached_at < :cutoffTime")
    suspend fun cleanupOldChallenges(cutoffTime: Long)
}

// =======================
// FAVORITES DAO
// =======================

@Dao
interface FavoritesDao {
    
    /**
     * Récupère tous les favoris d'un utilisateur (LiveData pour UI réactive)
     * Équivalent de @Published var favoriteQuestions iOS
     */
    @Query("SELECT * FROM favorite_questions WHERE userId = :userId ORDER BY date_added DESC")
    fun getFavoritesLiveData(userId: String): LiveData<List<FavoriteQuestionEntity>>
    
    /**
     * Récupère tous les favoris d'un utilisateur (suspend pour logique métier)
     */
    @Query("SELECT * FROM favorite_questions WHERE userId = :userId ORDER BY date_added DESC")
    suspend fun getFavorites(userId: String): List<FavoriteQuestionEntity>
    
    /**
     * Vérifie si une question est en favoris
     * Équivalent de la vérification existence Realm iOS
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_questions WHERE userId = :userId AND questionId = :questionId)")
    suspend fun isFavorite(userId: String, questionId: String): Boolean
    
    /**
     * Ajoute un favori
     * Équivalent de realm.add(realmFavorite, update: .modified)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteQuestionEntity)
    
    /**
     * Ajoute plusieurs favoris en batch
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<FavoriteQuestionEntity>)
    
    /**
     * Supprime un favori spécifique
     * Équivalent de realm.delete(favoriteToDelete)
     */
    @Query("DELETE FROM favorite_questions WHERE questionId = :questionId AND userId = :userId")
    suspend fun removeFavorite(questionId: String, userId: String)
    
    /**
     * Supprime tous les favoris d'un utilisateur
     */
    @Query("DELETE FROM favorite_questions WHERE userId = :userId")
    suspend fun clearFavorites(userId: String)
    
    /**
     * Synchronisation avec Firestore - Supprime favoris qui ne sont plus partagés
     * Équivalent de la logique syncToLocalCache() iOS
     */
    @Query("DELETE FROM favorite_questions WHERE userId = :userId AND questionId NOT IN (:sharedQuestionIds)")
    suspend fun removeNonSharedFavorites(userId: String, sharedQuestionIds: List<String>)
    
    /**
     * Compte le nombre de favoris
     */
    @Query("SELECT COUNT(*) FROM favorite_questions WHERE userId = :userId")
    suspend fun getFavoritesCount(userId: String): Int
    
    /**
     * Nettoyage par âge si nécessaire
     */
    @Query("DELETE FROM favorite_questions WHERE cached_at < :cutoffTime")
    suspend fun cleanupOldFavorites(cutoffTime: Long)
    
    /**
     * Récupère les favoris par catégorie
     */
    @Query("SELECT * FROM favorite_questions WHERE userId = :userId AND categoryTitle = :categoryTitle ORDER BY date_added DESC")
    suspend fun getFavoritesByCategory(userId: String, categoryTitle: String): List<FavoriteQuestionEntity>
}
