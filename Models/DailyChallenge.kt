import java.util.Date
import java.util.UUID

// MARK: - DailyChallenge Model

data class DailyChallenge(
    val id: String = UUID.randomUUID().toString(),
    val challengeKey: String, // "daily_challenge_1", "daily_challenge_2", etc.
    val challengeDay: Int,
    val scheduledDate: Date,
    val coupleId: String,
    var isCompleted: Boolean = false,
    var completedAt: Date? = null
) {

    // Computed Property: texte localisé du défi
    val localizedText: String
        get() = challengeKey.localized("DailyChallenges")

    companion object {
        // Génère l'ID unique pour Firestore
        fun generateId(coupleId: String, date: Date): String {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
            return "${coupleId}_${formatter.format(date)}"
        }
    }
}

// MARK: - SavedChallenge Model

data class SavedChallenge(
    val id: String = UUID.randomUUID().toString(),
    val challengeKey: String,
    val challengeDay: Int,
    val savedAt: Date = Date(),
    val userId: String
) {

    // Computed Property: texte localisé du défi sauvegardé
    val localizedText: String
        get() = challengeKey.localized("DailyChallenges")

    companion object {
        // Génère l'ID unique pour Firestore
        fun generateId(userId: String, challengeKey: String): String {
            return "${userId}_${challengeKey}"
        }
    }
}

// MARK: - DailyChallengeSettings

data class DailyChallengeSettings(
    val coupleId: String,
    val startDate: Date,
    val timezone: String = "Europe/Paris",
    var currentDay: Int = 1,
    val createdAt: Date = Date(),
    var lastVisitDate: Date = Date()
)
