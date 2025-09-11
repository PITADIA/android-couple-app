package com.love2love.model

import android.content.Context
import android.location.Location
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interface d'abstraction du service de chiffrement/hybridation côté Android.
 * Implémentez ces méthodes selon votre logique existante (équivalent Swift).
 */
interface LocationEncryptionService {
    /**
     * Lit un message (titre/description) à partir d'une map Firestore potentiellement chiffrée.
     * La map d'entrée peut être pré-transformée (renommage de clefs) pour s'aligner
     * sur les noms attendus (ex: encryptedText, textNonce, textIv...).
     */
    fun readMessageFromFirestore(data: Map<String, Any?>): String?

    /**
     * Prépare les champs à enregistrer en Firestore pour un message en clair.
     * Retourne par ex. { encryptedText: ..., textIv: ..., textNonce: ... } (ou fallback texte).
     */
    fun processMessageForStorage(plain: String): Map<String, Any?>

    /**
     * Lit une localisation depuis Firestore (nouveau/ancien format), retourne un conteneur
     * permettant de récupérer une Location Android.
     */
    fun readLocation(data: Map<String, Any?>): EncryptedLocationData?

    /**
     * Prépare les champs à enregistrer pour une Location Android (nouveau format chiffré).
     */
    fun processLocationForStorage(location: Location): Map<String, Any?>
}

/**
 * Conteneur pour une localisation lue via LocationEncryptionService.
 */
data class EncryptedLocationData(
    val latitude: Double,
    val longitude: Double,
    val isEncrypted: Boolean
) {
    fun toAndroidLocation(provider: String = "gps"): Location = Location(provider).apply {
        latitude = this@EncryptedLocationData.latitude
        longitude = this@EncryptedLocationData.longitude
    }
}

// ==========================
// Location Data (Android)
// ==========================

data class JournalLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null
) {
    fun toLocation(provider: String = "gps"): Location = Location(provider).apply {
        latitude = this@JournalLocation.latitude
        longitude = this@JournalLocation.longitude
    }

    /**
     * Affichage localisé du nom de lieu.
     * IMPORTANT traductions: ajoutez dans strings.xml ->
     * <string name="location_default">Localisation</string>
     */
    fun displayName(context: Context): String = when {
        !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
        !address.isNullOrBlank() -> address!!
        else -> context.getString(R.string.location_default)
    }
}

// ==========================
// Journal Entry (Android)
// ==========================

data class JournalEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String,
    var description: String,
    var eventDate: Date,
    var createdAt: Date = Date(),
    var updatedAt: Date = Date(),
    var authorId: String,
    var authorName: String,
    var imageURL: String? = null,
    var localImagePath: String? = null, // chemin local (non persisté Firestore)
    var isShared: Boolean = true,
    var partnerIds: List<String> = emptyList(),
    var location: JournalLocation? = null
) {
    // ---------- Helpers d'affichage (localisés par Locale)
    fun formattedEventDate(locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(eventDate)

    fun dayOfMonth(locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("d", locale).format(eventDate)

    fun monthAbbreviation(locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("MMM", locale).format(eventDate)

    fun monthYear(locale: Locale = Locale.getDefault()): String =
        SimpleDateFormat("MMMM yyyy", locale).format(eventDate)

    fun hasImage(): Boolean = !imageURL.isNullOrEmpty() || !localImagePath.isNullOrEmpty()

    /**
     * Équivalence "contenu" (équivalent du == custom Swift).
     * NB: la data class possède déjà equals() structurel ; utilisez ceci si vous voulez
     * reproduire le comparateur restreint Swift (id, title, description, eventDate, imageURL).
     */
    fun contentEquals(other: JournalEntry): Boolean =
        id == other.id &&
        title == other.title &&
        description == other.description &&
        eventDate == other.eventDate &&
        imageURL == other.imageURL

    // ---------- Firestore I/O ----------
    companion object {
        /**
         * Désérialisation depuis DocumentSnapshot (avec chiffrement hybride).
         * Retourne null si eventDate manquant/incorrect.
         */
        fun fromDocument(
            document: DocumentSnapshot,
            encryption: LocationEncryptionService
        ): JournalEntry? {
            val data = document.data ?: return null

            // Titre (mapping des clés vers format attendu par le service de chiffrement)
            val title = encryption.readMessageFromFirestore(
                data.mapKeysForText("title")
            ) ?: (data["title"] as? String).orEmpty()

            // Description
            val description = encryption.readMessageFromFirestore(
                data.mapKeysForText("description")
            ) ?: (data["description"] as? String).orEmpty()

            val eventDate: Date = when (val raw = data["eventDate"]) {
                is Timestamp -> raw.toDate()
                is Date -> raw
                else -> return null
            }

            val createdAt: Date = when (val raw = data["createdAt"]) {
                is Timestamp -> raw.toDate()
                is Date -> raw
                else -> Date()
            }
            val updatedAt: Date = when (val raw = data["updatedAt"]) {
                is Timestamp -> raw.toDate()
                is Date -> raw
                else -> Date()
            }

            val authorId = (data["authorId"] as? String).orEmpty()
            val authorName = (data["authorName"] as? String).orEmpty()
            val imageURL = data["imageURL"] as? String
            val isShared = (data["isShared"] as? Boolean) ?: true
            val partnerIds = (data["partnerIds"] as? List<*>)
                ?.filterIsInstance<String>() ?: emptyList()

            // Localisation (nouveau + legacy)
            val encLoc = encryption.readLocation(data)
            val location: JournalLocation? = if (encLoc != null) {
                val loc = encLoc.toAndroidLocation()
                // Métadonnées additionnelles (non sensibles)
                var address: String? = data["locationAddress"] as? String
                var city: String? = data["locationCity"] as? String
                var country: String? = data["locationCountry"] as? String

                // Fallback legacy si vide
                if (city == null && country == null) {
                    @Suppress("UNCHECKED_CAST")
                    val legacy = data["location"] as? Map<String, Any?>
                    if (legacy != null) {
                        if (address == null) address = legacy["address"] as? String
                        city = legacy["city"] as? String
                        country = legacy["country"] as? String
                    }
                }

                JournalLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    address = address,
                    city = city,
                    country = country
                )
            } else {
                null
            }

            return JournalEntry(
                id = document.id,
                title = title,
                description = description,
                eventDate = eventDate,
                createdAt = createdAt,
                updatedAt = updatedAt,
                authorId = authorId,
                authorName = authorName,
                imageURL = imageURL,
                localImagePath = null, // ne jamais persister
                isShared = isShared,
                partnerIds = partnerIds,
                location = location
            )
        }
    }

    /**
     * Sérialisation Firestore (avec chiffrement hybride).
     * - N'ajoute pas localImagePath.
     * - Écrit le nouveau format chiffré ET conserve les méta non sensibles.
     */
    fun toFirestoreMap(encryption: LocationEncryptionService): Map<String, Any?> {
        val dict = mutableMapOf<String, Any?>()

        dict["eventDate"] = Timestamp(eventDate)
        dict["createdAt"] = Timestamp(createdAt)
        dict["updatedAt"] = Timestamp(updatedAt)
        dict["authorId"] = authorId
        dict["authorName"] = authorName
        dict["isShared"] = isShared
        dict["partnerIds"] = partnerIds
        imageURL?.let { dict["imageURL"] = it }

        // Titre chiffré (renommage des clés: encryptedText->encryptedTitle, text*->title*)
        val encTitle = encryption.processMessageForStorage(title)
            .renameKeysForField("title")
        dict.putAll(encTitle)

        // Description chiffrée
        val encDesc = encryption.processMessageForStorage(description)
            .renameKeysForField("description")
        dict.putAll(encDesc)

        // Localisation (nouveau format + métadonnées additionnelles)
        location?.let { loc ->
            val androidLoc = loc.toLocation()
            val encLoc = encryption.processLocationForStorage(androidLoc)
            if (encLoc.isNotEmpty()) dict.putAll(encLoc)

            loc.address?.let { dict["locationAddress"] = it }
            loc.city?.let { dict["locationCity"] = it }
            loc.country?.let { dict["locationCountry"] = it }
        }

        return dict
    }
}

// ==========================
// Map helpers (renommage clés)
// ==========================

private fun Map<String, Any?>.mapKeysForText(field: String): Map<String, Any?> {
    // field == "title" ou "description"
    val encryptedField = "encrypted" + field.replaceFirstChar { it.titlecase(Locale.ROOT) }
    return entries.associate { (k, v) ->
        val newKey = when (k) {
            encryptedField -> "encryptedText"
            else -> k.replace(field, "text")
        }
        newKey to v
    }
}

/**
 * Renomme les clés générées par le service pour un champ textuel spécifique.
 * Exemple: pour field="title" -> encryptedText->encryptedTitle, textNonce->titleNonce, etc.
 */
private fun Map<String, Any?>.renameKeysForField(field: String): Map<String, Any?> {
    return entries.associate { (k, v) ->
        val newKey = when (k) {
            "encryptedText" -> "encrypted" + field.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> k.replace("text", field)
        }
        newKey to v
    }
}
