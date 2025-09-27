package com.love2loveapp.services.cache.entities

import androidx.room.*
import com.love2loveapp.models.DailyQuestion
import com.love2loveapp.models.DailyChallenge
import com.love2loveapp.models.FavoriteQuestion
import com.google.firebase.Timestamp
import java.util.*

/**
 * 📱 Entités Room pour Cache Sophistiqué Android
 * 
 * Équivalent des modèles Realm iOS:
 * - RealmDailyQuestion → DailyQuestionEntity
 * - RealmDailyChallenge → DailyChallengeEntity  
 * - RealmFavoriteQuestion → FavoriteQuestionEntity
 * - Migration et conversion automatiques
 * - Index pour performance comme Realm iOS
 */

// =======================
// DAILY QUESTIONS CACHE
// =======================

@Entity(
    tableName = "daily_questions",
    indices = [
        Index(value = ["coupleId", "scheduledDate"], unique = true),
        Index(value = ["coupleId"]),
        Index(value = ["scheduledDate"])
    ]
)
data class DailyQuestionEntity(
    @PrimaryKey 
    val id: String,
    
    val coupleId: String,
    val scheduledDate: String, // Format "yyyy-MM-dd"
    val questionKey: String,
    val questionDay: Int,
    val status: String = "pending",
    val timezone: String = "Europe/Paris",
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
) {
    /**
     * Conversion vers DailyQuestion (équivalent toDailyQuestion() Realm iOS)
     */
    fun toDailyQuestion(): DailyQuestion {
        return DailyQuestion(
            id = id,
            coupleId = coupleId,
            scheduledDate = scheduledDate,
            scheduledDateTime = com.google.firebase.Timestamp(Date(createdAt)),
            questionKey = questionKey,
            questionDay = questionDay,
            status = status,
            timezone = timezone,
            createdAt = com.google.firebase.Timestamp(Date(createdAt)),
            updatedAt = com.google.firebase.Timestamp(Date(cachedAt))
        )
    }
    
    companion object {
        /**
         * Conversion depuis DailyQuestion (équivalent constructeur Realm iOS)
         */
        fun fromDailyQuestion(dailyQuestion: DailyQuestion): DailyQuestionEntity {
            return DailyQuestionEntity(
                id = dailyQuestion.id,
                coupleId = dailyQuestion.coupleId,
                scheduledDate = dailyQuestion.scheduledDate,
                questionKey = dailyQuestion.questionKey,
                questionDay = dailyQuestion.questionDay,
                status = dailyQuestion.status,
                timezone = dailyQuestion.timezone
            )
        }
    }
}

// =======================
// DAILY CHALLENGES CACHE
// =======================

@Entity(
    tableName = "daily_challenges",
    indices = [
        Index(value = ["coupleId", "scheduledDate"], unique = true),
        Index(value = ["coupleId"]),
        Index(value = ["scheduledDate"])
    ]
)
data class DailyChallengeEntity(
    @PrimaryKey 
    val id: String,
    
    val coupleId: String,
    val scheduledDate: Long, // Timestamp Unix
    val challengeKey: String,
    val challengeDay: Int,
    
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    
    @ColumnInfo(name = "created_at") 
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
) {
    /**
     * Conversion vers DailyChallenge (équivalent toDailyChallenge() Realm iOS)
     */
    fun toDailyChallenge(): DailyChallenge {
        return DailyChallenge(
            id = id,
            coupleId = coupleId,
            scheduledDate = Timestamp(scheduledDate / 1000, ((scheduledDate % 1000) * 1000000).toInt()),
            challengeKey = challengeKey,
            challengeDay = challengeDay,
            isCompleted = isCompleted,
            completedAt = completedAt?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) },
            createdAt = Timestamp(createdAt / 1000, ((createdAt % 1000) * 1000000).toInt()),
            updatedAt = Timestamp(cachedAt / 1000, ((cachedAt % 1000) * 1000000).toInt())
        )
    }
    
    companion object {
        /**
         * Conversion depuis DailyChallenge (équivalent constructeur Realm iOS)
         */
        fun fromDailyChallenge(dailyChallenge: DailyChallenge): DailyChallengeEntity {
            return DailyChallengeEntity(
                id = dailyChallenge.id,
                coupleId = dailyChallenge.coupleId,
                scheduledDate = dailyChallenge.scheduledDate.seconds * 1000 + dailyChallenge.scheduledDate.nanoseconds / 1000000,
                challengeKey = dailyChallenge.challengeKey,
                challengeDay = dailyChallenge.challengeDay,
                isCompleted = dailyChallenge.isCompleted,
                completedAt = dailyChallenge.completedAt?.let { it.seconds * 1000 + it.nanoseconds / 1000000 }
            )
        }
    }
}

// =======================
// FAVORITES CACHE
// =======================

@Entity(
    tableName = "favorite_questions",
    indices = [
        Index(value = ["userId", "questionId"], unique = true),
        Index(value = ["userId"]),
        Index(value = ["questionId"]),
        Index(value = ["date_added"]) // Nom de colonne réel, pas propriété Kotlin
    ]
)
data class FavoriteQuestionEntity(
    @PrimaryKey 
    val id: String,
    
    val questionId: String,
    val userId: String,
    val categoryTitle: String,
    val questionText: String,
    val emoji: String,
    
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis()
) {
    /**
     * Conversion vers FavoriteQuestion (équivalent toFavoriteQuestion() Realm iOS)
     */
    fun toFavoriteQuestion(): FavoriteQuestion {
        return FavoriteQuestion(
            id = id,
            questionId = questionId,
            categoryTitle = categoryTitle,
            questionText = questionText,
            emoji = emoji,
            dateAdded = Date(dateAdded)
        )
    }
    
    companion object {
        /**
         * Conversion depuis FavoriteQuestion (équivalent constructeur Realm iOS)
         */
        fun fromFavoriteQuestion(favoriteQuestion: FavoriteQuestion, userId: String): FavoriteQuestionEntity {
            return FavoriteQuestionEntity(
                id = favoriteQuestion.id,
                questionId = favoriteQuestion.questionId,
                userId = userId,
                categoryTitle = favoriteQuestion.categoryTitle,
                questionText = favoriteQuestion.questionText,
                emoji = favoriteQuestion.emoji,
                dateAdded = favoriteQuestion.dateAdded.time
            )
        }
    }
}

// =======================
// CONVERTERS ROOM (équivalent TypeConverters iOS/CoreData)
// =======================

class CacheConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
