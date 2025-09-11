@file:Suppress("unused")

package com.love2loveapp.core.ui.extensions

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.StringRes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import java.util.Locale
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.os.Build.MANUFACTURER
import android.os.Build.MODEL
import android.os.Build.PRODUCT
import com.love2loveapp.model.AppConstants

/**
 * ============================================
 *  DeviceUtils — équivalent de UIDevice.modelName
 * ============================================
 */
object DeviceUtils {
    /**
     * Retourne un nom lisible du modèle (ex: "Samsung Galaxy S23" ou "Google Pixel 8").
     * Fallback: "Manufacturer Model".
     */
    fun modelName(): String {
        val manufacturer = MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = MODEL.orEmpty()
        return when {
            model.startsWith(manufacturer, ignoreCase = true) -> model
            manufacturer.isBlank() -> model.ifBlank { "Android Device" }
            else -> "$manufacturer $model"
        }
    }
}

/**
 * ============================================
 *  BadgeManager — gestion badge & notifications
 *  Note: sur Android, le “badge” dépend du launcher.
 *  La valeur affichée correspond au nombre de notifications actives.
 *  Stratégie : poster une notif silencieuse avec setNumber(count).
 * ============================================
 */
object BadgeManager {
    private const val CHANNEL_ID = AppConstants.UIExtensions.BADGE_CHANNEL_ID
    private const val CHANNEL_NAME = "Badges & Silent Updates"
    private const val BADGE_NOTIFICATION_ID = AppConstants.UIExtensions.BADGE_NOTIFICATION_ID

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(true)   // autorise le badge
                    enableVibration(false)
                    enableLights(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    description = "Channel for app badge and silent updates"
                }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Remet le "badge" à zéro en annulant toutes les notifications.
     * (Sur la plupart des launchers, plus de notifications == pas de badge)
     */
    fun clearBadge(context: Context) {
        ensureChannel(context)
        NotificationManagerCompat.from(context).cancelAll()
        // Rien d’autre n’est fiable cross-launcher sans lib tierce.
    }

    /**
     * Définit le badge en postant une notification silencieuse avec un nombre.
     * ⚠️ Nécessite une petite icône valide (remplace R.drawable.ic_notification).
     */
    fun setBadge(context: Context, count: Int) {
        ensureChannel(context)
        val safeCount = count.coerceAtLeast(0)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                // TODO: remplace par ton icône notifs
                context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
                    .takeIf { it != 0 } ?: android.R.drawable.stat_notify_chat
            )
            .setContentTitle(context.getString(
                context.resources.getIdentifier("app_name", "string", context.packageName)
                    .takeIf { it != 0 } ?: android.R.string.untitled
            ))
            .setContentText(if (safeCount > 0)
                "${safeCount} ${context.getString(
                    context.resources.getIdentifier("notifications", "string", context.packageName)
                        .takeIf { it != 0 } ?: android.R.string.untitled
                )}" else ""
            )
            .setNumber(safeCount)               // ⇦ nombre affiché par certains launchers
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setOngoing(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)

        NotificationManagerCompat.from(context).notify(BADGE_NOTIFICATION_ID, builder.build())
    }

    /** Supprime notifications ET badge (comportement iOS clearAll) */
    fun clearAllNotificationsAndBadge(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}

/**
 * ============================================
 *  ChatText — équivalent SwiftUI + UILabel pushOut/standard
 *  Compose gère naturellement le multi-ligne; on ajuste le "lineBreak".
 *  isCurrentUser = false → line-break plus naturel (Paragraph).
 *  isCurrentUser = true  → line-break simple/net.
 * ============================================
 */
@Composable
fun ChatText(
    text: String,
    isCurrentUser: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    fontSize: TextUnit = 16.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val style: TextStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontSize = fontSize,
            lineBreak = if (isCurrentUser) LineBreak.Simple else LineBreak.Paragraph
        )
    )

    Text(
        text = text,
        color = color,
        textAlign = textAlign,
        style = style,
        softWrap = true,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier
    )
}

/**
 * ============================================
 *  Color.fromHex — équivalent Color(hex:)
 *  Supporte #RGB, #RRGGBB, #AARRGGBB (avec ou sans #)
 * ============================================
 */
fun Color.Companion.fromHex(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    fun ch2(i: Int) = cleaned.substring(i, i + 2).toInt(16) / 255f

    return when (cleaned.length) {
        3 -> { // RGB (12-bit)
            val r = cleaned[0].toString().repeat(2).toInt(16) / 255f
            val g = cleaned[1].toString().repeat(2).toInt(16) / 255f
            val b = cleaned[2].toString().repeat(2).toInt(16) / 255f
            Color(r, g, b, 1f)
        }
        6 -> {
            val r = ch2(0); val g = ch2(2); val b = ch2(4)
            Color(r, g, b, 1f)
        }
        8 -> {
            val a = ch2(0); val r = ch2(2); val g = ch2(4); val b = ch2(6)
            Color(r, g, b, a)
        }
        else -> Color(1f, 1f, 0f, 1f) // fallback (jaune)
    }
}

/**
 * ============================================
 *  Localisation & helpers strings.xml
 *  Remplace: "key".localized(tableName: ...)
 *  Par:      context.getString(R.string.key)
 *  Ou en Compose: stringResource(R.string.key)
 * ============================================
 */
fun Context.t(@StringRes id: Int, vararg args: Any): String =
    getString(id, *args)

/**
 * ============================================
 *  String conversions — km → miles si langue "en"
 *  Reprend la logique Swift avec Regex.
 * ============================================
 */
fun String.convertedForLocale(context: Context): String {
    val lang = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.resources.configuration.locales[0]?.language ?: "en"
    } else {
        @Suppress("DEPRECATION")
        context.resources.configuration.locale?.language ?: "en"
    }

    return if (lang.equals("en", ignoreCase = true)) convertKmToMiles() else this
}

private fun String.convertKmToMiles(): String {
    val trimmed = trim()
    if (trimmed == "? km") return replaceFirst("? km", "? mi")

    val kmRegex = Regex("""([0-9]+(?:\.[0-9]+)?)\s*km""")
    val mRegex  = Regex("""([0-9]+)\s*m""")

    kmRegex.find(this)?.let { m ->
        val kmValue = m.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return this
        val miles = kmValue * 0.621371
        val formatted = if (miles < 10) String.format(Locale.US, "%.1f mi", miles) else "${miles.toInt()} mi"
        return replaceRange(m.range, formatted)
    }

    mRegex.find(this)?.let { m ->
        val meters = m.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return this
        val miles = meters / 1609.34
        val formatted = if (miles < 10) String.format(Locale.US, "%.1f mi", miles) else "${miles.toInt()} mi"
        return replaceRange(m.range, formatted)
    }

    return this
}

/**
 * ============================================
 *  Text helpers (Compose) — remplace Text(ui:) etc.
 *  Utilise stringResource(R.string.key).
 * ============================================
 */
@Composable
fun UiText(@StringRes id: Int, vararg args: Any) {
    Text(text = stringResource(id = id, formatArgs = args))
}

@Composable
fun LocalizedText(@StringRes id: Int, vararg args: Any) {
    Text(text = stringResource(id = id, formatArgs = args))
}

/**
 * ============================================
 *  Date/Time formatters — équivalents DateFormatter.*
 * ============================================
 */
object Formatters {
    /** ⏰ HH:mm:ss selon locale (API 26+) */
    val timeFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
    }

    /** 📅 yyyy-MM-dd en timezone locale (pour clés cache) */
    val dayFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
    }

    /** 🌍 Debug avec fuseau (yyyy-MM-dd HH:mm:ss zzz) */
    val timezoneDebugFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz").withZone(ZoneId.systemDefault())
    }
}

/**
 * ============================================
 *  Share Sheet — équivalent UIActivityViewController
 * ============================================
 */
fun Context.showShareSheet(text: String? = null, uris: List<Uri> = emptyList(), mime: String = "*/*") {
    val intent = when {
        uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
        uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uris.first())
            if (!text.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
        else -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text ?: "")
        }
    }
    startActivity(Intent.createChooser(intent, getString(
        resources.getIdentifier("share_via", "string", packageName)
            .takeIf { it != 0 } ?: android.R.string.copy
    )))
}

/**
 * ============================================
 *  Keyboard helpers — équivalents hideKeyboard()
 * ============================================
 */
fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    currentFocus?.let { view ->
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    } ?: run {
        // Fallback si aucune vue focus
        val view = View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
    clearFocus()
}

@Composable
fun hideKeyboardComposable() {
    val view = LocalView.current
    view.hideKeyboard()
}

/* ===========================
   NOTES DE MIGRATION iOS → ANDROID
   ---------------------------
   • Localisation:
       - iOS:  "key".localized(tableName: "UI")
       - Android (Kotlin): context.getString(R.string.key)
       - Android (Compose): stringResource(R.string.key)

   • ChatText:
       - iOS: UILabel avec lineBreakStrategy .pushOut / .standard
       - Android: Compose Text avec LineBreak.Paragraph / LineBreak.Simple

   • Badge:
       - iOS: UNUserNotificationCenter.setBadgeCount / appIconBadgeNumber
       - Android: dépend du launcher; simuler via notif silencieuse + setNumber(count).
         clearBadge() = cancelAll()

   • Dates:
       - iOS: DateFormatter statiques
       - Android: java.time DateTimeFormatter (API 26+)

   • Partage:
       - iOS: UIActivityViewController
       - Android: Intent ACTION_SEND / ACTION_SEND_MULTIPLE
   =========================== */
