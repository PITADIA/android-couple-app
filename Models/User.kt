package com.love2loveapp.model

import android.content.Context
import android.location.Location
import androidx.annotation.StringRes
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.love2loveapp.model.AppConstants

// If you use Jetpack Compose, you can also expose helpers that return localized strings via stringResource
// import androidx.compose.runtime.Composable
// import androidx.compose.ui.res.stringResource

/**
 * ===============================
 *  User Location (Android)
 * ===============================
 */

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null,
    val city: String? = null,
    val country: String? = null,
    val lastUpdated: Date = Date()
) {
    /** Android Location instance (for interop) */
    fun toAndroidLocation(provider: String = "cached") = Location(provider).apply {
        latitude = this@UserLocation.latitude
        longitude = this@UserLocation.longitude
        time = this@UserLocation.lastUpdated.time
    }

    /** Display name with Android resources fallback (strings.xml) */
    fun displayName(context: Context): String {
        return when {
            !city.isNullOrBlank() && !country.isNullOrBlank() -> "$city, $country"
            !address.isNullOrBlank() -> address
            else -> context.getString(R.string.location_generic) // ex: "Localisation"
        }
    }

    /** Distance to another location in kilometers */
    fun distanceKmTo(other: UserLocation): Double {
        val results = FloatArray(1)
        Location.distanceBetween(latitude, longitude, other.latitude, other.longitude, results)
        return results[0].toDouble() / 1000.0
    }
}

/**
 * ===============================
 *  AppUser (Android)
 * ===============================
 */

data class AppUser(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    val birthDate: Date,
    val relationshipGoals: List<String> = emptyList(),
    val relationshipDuration: RelationshipDuration = RelationshipDuration.NONE,
    val relationshipImprovement: String? = null,
    val questionMode: String? = null,
    val partnerCode: String? = null,
    val partnerId: String? = null,
    val partnerConnectedAt: Date? = null,
    val subscriptionInheritedFrom: String? = null,
    val subscriptionInheritedAt: Date? = null,
    val connectedPartnerCode: String? = null,
    val connectedPartnerId: String? = null,
    val connectedAt: Date? = null,
    val isSubscribed: Boolean = false,
    val onboardingInProgress: Boolean = false,
    val relationshipStartDate: Date? = null,
    val profileImageURL: String? = null,
    val profileImageUpdatedAt: Date? = null,
    val currentLocation: UserLocation? = null,
    val languageCode: String? = Locale.getDefault().language,
    // Freemium tracking — Daily Questions
    val dailyQuestionFirstAccessDate: Date? = null,
    val dailyQuestionMaxDayReached: Int = 0,
    // Freemium tracking — Daily Challenges
    val dailyChallengeFirstAccessDate: Date? = null,
    val dailyChallengeMaxDayReached: Int = 0
) {
    init {
        // Auto-generate a friendly name if empty, localized by device language
        if (name.isBlank()) {
            val shortId = id.take(4)
            val lang = (languageCode ?: Locale.getDefault().language) ?: "en"
            name = if (lang.startsWith("fr", ignoreCase = true)) {
                "Utilisateur$shortId"
            } else {
                "User$shortId"
            }
        }
    }
}

typealias User = AppUser

/**
 * ===============================
 *  Relationship Duration (enum)
 *  NOTE: Do not hardcode human text; keep it in strings.xml
 * ===============================
 */

enum class RelationshipDuration(val code: String, @StringRes val labelRes: Int) {
    NONE("", R.string.relationship_duration_none),
    LESS_THAN_YEAR("lt_1y", R.string.relationship_duration_less_than_year),
    ONE_TO_THREE_YEARS("1_3y", R.string.relationship_duration_one_to_three),
    MORE_THAN_THREE_YEARS("gt_3y", R.string.relationship_duration_more_than_three),
    NOT_IN_RELATIONSHIP("not_in", R.string.relationship_duration_not_in_relationship);

    companion object {
        fun fromCode(code: String?): RelationshipDuration = values().firstOrNull { it.code == code } ?: NONE
    }
}

/**
 * ===============================
 *  Subscription Plan (enum)
 *  Replaces StoreKit with Android resources + your pricing service
 * ===============================
 */

enum class SubscriptionPlanType(val productId: String, @StringRes val displayNameRes: Int, @StringRes val periodRes: Int) {
    WEEKLY(
        productId = AppConstants.Products.WEEKLY_SUBSCRIPTION,
        displayNameRes = R.string.plan_weekly,
        periodRes = R.string.period_week
    ),
    MONTHLY(
        productId = AppConstants.Products.MONTHLY_SUBSCRIPTION,
        displayNameRes = R.string.plan_monthly_free_trial,
        periodRes = R.string.period_month
    );

    val hasFreeTrial: Boolean get() = true
    val freeTrialDays: Int get() = AppConstants.FREE_TRIAL_DAYS

    fun displayName(context: Context): String = context.getString(displayNameRes)
    fun periodName(context: Context): String = context.getString(periodRes)

    /** Price strings should come from Play Billing / RevenueCat; we expose a thin facade here. */
    fun price(context: Context): String = PricingService.getLocalizedPrice(context, this)
    fun pricePerUser(context: Context): String = PricingService.getPricePerUser(context, this)
}

/**
 * ===============================
 *  Pricing Facade (stub)
 *  Plug your Google Play Billing or RevenueCat logic here.
 * ===============================
 */

object PricingService {
    /**
     * Return a localized price for the given plan, e.g. from Play Billing's ProductDetails.OneTimePurchaseOfferDetails or SubscriptionOfferDetails.
     * Replace the stub below with your actual implementation.
     */
    fun getLocalizedPrice(context: Context, plan: SubscriptionPlanType): String {
        // TODO: Implement using your billing layer. Stub for now:
        return when (plan) {
            SubscriptionPlanType.WEEKLY -> context.getString(R.string.price_weekly_placeholder) // e.g. "4,49 €"
            SubscriptionPlanType.MONTHLY -> context.getString(R.string.price_monthly_placeholder) // e.g. "9,99 €"
        }
    }

    /** Price per user = price / 2 (as per your iOS logic). */
    fun getPricePerUser(context: Context, plan: SubscriptionPlanType): String {
        // TODO: Compute based on real numeric price; here we return a placeholder label.
        return when (plan) {
            SubscriptionPlanType.WEEKLY -> context.getString(R.string.price_per_user_weekly_placeholder)
            SubscriptionPlanType.MONTHLY -> context.getString(R.string.price_per_user_monthly_placeholder)
        }
    }
}

/**
 * ===============================
 *  Localization helpers
 * ===============================
 */

/** Resolve a dynamic key name (e.g., "daily_challenge_12") from strings.xml; fallback to the key itself if not found. */
fun Context.getStringByName(name: String): String {
    val resId = resources.getIdentifier(name, "string", packageName)
    return if (resId != 0) getString(resId) else name
}

// If using Compose, you might also expose:
// @Composable
// fun planDisplayName(plan: SubscriptionPlanType): String = stringResource(id = plan.displayNameRes)
// @Composable
// fun planPeriodName(plan: SubscriptionPlanType): String = stringResource(id = plan.periodRes)

/**
 * ===============================
 *  strings.xml — required keys (examples)
 * ===============================
 * <string name="location_generic">Localisation</string>
 * <string name="plan_weekly">Hebdomadaire</string>
 * <string name="plan_monthly_free_trial">Mensuel · Essai gratuit</string>
 * <string name="period_week">Semaine</string>
 * <string name="period_month">Mois</string>
 * <string name="relationship_duration_none">Non précisé</string>
 * <string name="relationship_duration_less_than_year">Moins d'un an</string>
 * <string name="relationship_duration_one_to_three">Entre 1 et 3 ans</string>
 * <string name="relationship_duration_more_than_three">Plus de 3 ans</string>
 * <string name="relationship_duration_not_in_relationship">Je ne suis pas en couple</string>
 * <!-- Placeholders for pricing until Billing is wired -->
 * <string name="price_weekly_placeholder">–</string>
 * <string name="price_monthly_placeholder">–</string>
 * <string name="price_per_user_weekly_placeholder">– / utilisateur</string>
 * <string name="price_per_user_monthly_placeholder">– / utilisateur</string>
 */
