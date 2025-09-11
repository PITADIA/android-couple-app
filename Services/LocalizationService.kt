// LocalizationService.kt
package com.love2love.i18n

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import android.util.Log

object LocalizationService {
    @Volatile private var lastLoggedLanguage: String? = null

    // --- Accès direct et sûr (préféré) : R.string.some_key ---
    fun getString(context: Context, @StringRes resId: Int, vararg formatArgs: Any): String {
        return if (formatArgs.isNotEmpty()) context.getString(resId, *formatArgs)
        else context.getString(resId)
    }

    // --- Lookup dynamique par nom de clé (quand tu n’as que "daily_challenge_17") ---
    fun getString(context: Context, key: String, vararg formatArgs: Any): String {
        val id = context.resources.getIdentifier(key, "string", context.packageName)
        return if (id != 0) getString(context, id, *formatArgs) else key
    }

    // --- Helpers façon "UI.xcstrings" / "Categories.xcstrings" via préfixes ---
    fun ui(context: Context, key: String, vararg formatArgs: Any): String =
        getString(context, "ui_$key", *formatArgs)

    fun category(context: Context, key: String, vararg formatArgs: Any): String =
        getString(context, "category_$key", *formatArgs)

    // ---------------- Currency helpers (fallbacks) ----------------
    @Deprecated("Fallback only. Préfère Play Billing pour les prix.")
    fun localizedCurrencySymbol(
        priceString: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val region = locale.country.ifBlank { "FR" }
        val lang = locale.language.ifBlank { "fr" }

        // garde la valeur numérique telle quelle (ex: 4,99 / 4.99)
        val numeric = priceString.replace(Regex("[^0-9,\\.]"), "")

        val currencySymbol = when {
            lang == "en" -> when (region) {
                "US" -> "$"
                "GB" -> "£"
                "CA" -> "CAD$"
                "AU" -> "AUD$"
                else -> "$"
            }
            else -> when (region) {
                "US","PR","VI","GU","AS","MP" -> "$"
                "GB","IM","JE","GG" -> "£"
                "CA" -> "CAD$"
                "CH","LI" -> "CHF"
                "JP" -> "¥"
                "AU" -> "AUD$"
                else -> "€"
            }
        }

        return when {
            currencySymbol == "£" -> "£$numeric"
            currencySymbol == "¥" -> "¥$numeric"
            currencySymbol == "$" -> "$$numeric"
            currencySymbol.contains("CAD") ||
            currencySymbol.contains("AUD") ||
            currencySymbol.contains("CHF") -> "$numeric $currencySymbol"
            else -> priceString // ex: € on garde tel quel
        }
    }

    fun dividePrice(
        formattedPrice: String,
        divisor: Double = 2.0,
        locale: Locale = Locale.getDefault()
    ): String {
        val clean = formattedPrice.replace(Regex("[^0-9,\\.]"), "")
        val normalized = clean.replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return formattedPrice
        val result = value / divisor

        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = Currency.getInstance(getCurrencyCode(formattedPrice))
        return nf.format(result)
    }

    private fun getCurrencyCode(formattedPrice: String): String = when {
        formattedPrice.contains("CHF") -> "CHF"
        formattedPrice.contains("£")   -> "GBP"
        formattedPrice.contains("¥")   -> "JPY"
        formattedPrice.contains("$")   -> "USD"
        formattedPrice.contains("€")   -> "EUR"
        else -> "EUR"
    }

    // ---------------- Images localisées (retourne le nom logique) ----------------
    fun localizedImageName(
        frenchImage: String,
        defaultImage: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val lang = locale.language.ifBlank { "en" }
        maybeLogLanguage(lang)
        return if (lang == "fr") frenchImage else defaultImage
    }

    fun localizedImageName(
        frenchImage: String,
        defaultImage: String,
        germanImage: String,
        locale: Locale = Locale.getDefault()
    ): String {
        val lang = locale.language.ifBlank { "en" }
        maybeLogLanguage(lang)
        return when (lang) {
            "fr" -> frenchImage
            "de" -> germanImage
            else -> defaultImage
        }
    }

    @DrawableRes
    fun drawableResId(context: Context, name: String): Int =
        context.resources.getIdentifier(name, "drawable", context.packageName)

    private fun maybeLogLanguage(language: String) {
        if (lastLoggedLanguage != language) {
            Log.i("LocalizationService", "Langue système détectée: $language")
            lastLoggedLanguage = language
        }
    }
}

// ---------- Extensions & helpers (Android Views + Compose) ----------
package com.love2love.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

// Compose : usage préféré quand tu as un @StringRes
@Composable
fun localizedString(@StringRes resId: Int, vararg args: Any): String =
    if (args.isNotEmpty()) stringResource(id = resId, formatArgs = *args)
    else stringResource(id = resId)

// Compose : lookup dynamique par nom (ex: "daily_challenge_17")
@Composable
fun stringResourceByName(name: String, vararg args: Any): String {
    val context = LocalContext.current
    val id = context.resources.getIdentifier(name, "string", context.packageName)
    return if (id != 0) context.getString(id, *args) else name
}

// Parité avec tes extensions Swift
fun String.localizedUI(context: Context, vararg args: Any): String =
    LocalizationService.ui(context, this, *args)

fun String.localizedCategory(context: Context, vararg args: Any): String =
    LocalizationService.category(context, this, *args)

// Exemple d’équivalent à Question.optimizedLocalizedText
data class Question(val text: String)
val Question.optimizedLocalizedText: String
    get() = text
