package com.love2loveapp.services.cache

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.love2loveapp.services.cache.dao.*
import com.love2loveapp.services.cache.entities.*

/**
 * 📱 Base de Données Cache Sophistiqué Android
 * 
 * Architecture équivalente Realm iOS:
 * - Configuration automatique comme Realm.Configuration
 * - Migrations automatiques comme schemaVersion iOS
 * - Compactage automatique comme shouldCompactOnLaunch iOS
 * - Point d'entrée unique pour tous les caches
 * - Performances optimisées avec indices
 */

@Database(
    entities = [
        DailyQuestionEntity::class,
        DailyChallengeEntity::class,
        FavoriteQuestionEntity::class
    ],
    version = 1, // Première version sans migrations
    exportSchema = false
)
@TypeConverters(CacheConverters::class)
abstract class CacheDatabase : RoomDatabase() {
    
    // Abstract DAOs (équivalent des objets Realm iOS)
    abstract fun dailyQuestionsDao(): DailyQuestionsDao
    abstract fun dailyChallengesDao(): DailyChallengesDao 
    abstract fun favoritesDao(): FavoritesDao
    
    companion object {
        private const val DATABASE_NAME = "love2love_cache.db"
        private const val MAX_DATABASE_SIZE_MB = 50L // 50MB max comme iOS
        
        @Volatile
        private var INSTANCE: CacheDatabase? = null
        
        fun getDatabase(context: Context): CacheDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CacheDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .addCallback(DatabaseCallback())
                .setAutoCloseTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Migration manuelle 1→2 si AutoMigration échoue
         * Équivalent de migrationBlock iOS Realm
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Ajouter nouvelles colonnes si nécessaire
                try {
                    database.execSQL("ALTER TABLE daily_questions ADD COLUMN cached_at INTEGER DEFAULT ${System.currentTimeMillis()}")
                    database.execSQL("ALTER TABLE daily_challenges ADD COLUMN cached_at INTEGER DEFAULT ${System.currentTimeMillis()}")
                    database.execSQL("ALTER TABLE favorite_questions ADD COLUMN cached_at INTEGER DEFAULT ${System.currentTimeMillis()}")
                } catch (e: Exception) {
                    // Colonnes déjà existantes ou autre erreur
                    android.util.Log.w("CacheDatabase", "Migration warning: ${e.message}")
                }
            }
        }
        
        /**
         * Callback pour configuration initial et maintenance
         * Équivalent shouldCompactOnLaunch iOS
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                android.util.Log.d("CacheDatabase", "✅ Base de données cache créée")
            }
            
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                
                // Vérifier taille et déclencher compactage si nécessaire
                val dbFile = java.io.File(db.path ?: "")
                if (dbFile.exists()) {
                    val sizeMB = dbFile.length() / (1024 * 1024)
                    android.util.Log.d("CacheDatabase", "📊 Taille base données: ${sizeMB}MB")
                    
                    if (sizeMB > MAX_DATABASE_SIZE_MB) {
                        android.util.Log.w("CacheDatabase", "⚠️ Base données voluminuse (${sizeMB}MB > ${MAX_DATABASE_SIZE_MB}MB)")
                        // TODO: Déclencher nettoyage automatique
                    }
                }
            }
        }
        
        /**
         * Force fermeture et suppression pour tests/debug
         * Équivalent clearCache() complet iOS
         */
        suspend fun nukeDatabase(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                dbFile.delete()
                android.util.Log.i("CacheDatabase", "🗑️ Base données cache supprimée")
            }
        }
    }
}
