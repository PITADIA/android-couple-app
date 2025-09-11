package com.love2loveapp.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

// =============================
// CompositionLocal providers (équivalents EnvironmentObject iOS)
// =============================

val LocalQuestionCacheManager = staticCompositionLocalOf<Any> { 
    error("QuestionCacheManager not provided") 
}
val LocalPerformanceMonitor = staticCompositionLocalOf<Any> { 
    error("PerformanceMonitor not provided") 
}
val LocalFavoritesService = staticCompositionLocalOf<Any> { 
    error("FavoritesService not provided") 
}
val LocalPackProgressService = staticCompositionLocalOf<Any> { 
    error("PackProgressService not provided") 
}

// =============================
// AppState / ViewModel (exemple)
// =============================

data class CurrentUser(val uid: String, val name: String?)

data class AppStateUi(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val hasUserStartedOnboarding: Boolean = false,
    val isOnboardingCompleted: Boolean = false,
    val forceOnboarding: Boolean = false,
    val currentUser: CurrentUser? = null
)

class ContentViewModel : androidx.lifecycle.ViewModel() {

    private val _ui = MutableStateFlow(AppStateUi())
    val ui = _ui

    // Équivalent de NotificationCenter "AuthenticationStateChanged"
    private val _authEvents = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val authEvents = _authEvents.asSharedFlow()

    // Exemples de setters. En prod, ces valeurs viendront d’un Repository/SDK.
    fun setLoading(v: Boolean) = _ui.update { it.copy(isLoading = v) }
    fun setAuthenticated(v: Boolean) {
        _ui.update { it.copy(isAuthenticated = v) }
        _authEvents.tryEmit(v)
    }
    fun setOnboarding(started: Boolean, completed: Boolean) =
        _ui.update { it.copy(hasUserStartedOnboarding = started, isOnboardingCompleted = completed) }

    fun forceOnboarding(v: Boolean) = _ui.update { it.copy(forceOnboarding = v) }
    fun setCurrentUser(u: CurrentUser?) = _ui.update { it.copy(currentUser = u) }
}

// ======================================
// Composable principal (portage ContentView)
// ======================================

@Composable
fun ContentScreen(
    viewModel: ContentViewModel = viewModel(),
    // si tu veux passer un flux de deep-links depuis l’Activity :
    deepLinkIntent: Intent? = null,
    onOpenSubscription: () -> Unit = {} // callback si tu préfères gérer l’ouverture dans NavHost
) {
    val context = LocalContext.current
    val analytics = remember { FirebaseAnalytics.getInstance(context) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    var isTransitioning by remember { mutableStateOf(false) }
    var isOnboardingActive by remember { mutableStateOf(false) }

    // --- Logging route (équivalent _printCurrentRoute)
    LaunchedEffect(ui, isTransitioning) {
        when {
            ui.isLoading -> logRoute("LaunchScreen [loading=true]")
            isTransitioning -> logRoute("LoadingTransition [transitioning=true]")
            !ui.isAuthenticated -> logRoute("AuthenticationView [authenticated=false]")
            ui.isAuthenticated && !ui.hasUserStartedOnboarding && !ui.isOnboardingCompleted ->
                logRoute("AuthenticationView [authenticated=true, !hasUserStarted && !completed]")
            ui.isAuthenticated && (ui.hasUserStartedOnboarding || ui.forceOnboarding) && !ui.isOnboardingCompleted ->
                logRoute("OnboardingView [authenticated=true, (hasUserStarted || force) && !completed]")
            else -> logRoute("TabContainerView [authenticated=true, onboarding completed]")
        }
    }

    // --- Observer “AuthenticationStateChanged” → transition 1.5s
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        viewModel.authEvents.collect { isAuth ->
            if (isAuth) {
                Log.d("ContentScreen", "Notification AuthenticationStateChanged reçue → transition")
                isTransitioning = true
                delay(1500)
                isTransitioning = false
            }
        }
    }

    // --- Scene phase handling → équivalent scenePhase iOS avec logs détaillés
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                Log.d("ContentScreen", "🔄 Lifecycle: onCreate - Application créée")
            }
            
            override fun onStart(owner: LifecycleOwner) {
                Log.d("ContentScreen", "🌱 Lifecycle: onStart - App devient visible (équivalent scenePhase.active)")
                BadgeManager.clearBadge(context)
                // Équivalent iOS: applicationDidBecomeActive
                trackAppForegroundAnalytics()
            }
            
            override fun onResume(owner: LifecycleOwner) {
                Log.d("ContentScreen", "▶️ Lifecycle: onResume - App au premier plan interactif")
                // Équivalent iOS: applicationDidBecomeActive (interaction utilisateur possible)
            }
            
            override fun onPause(owner: LifecycleOwner) {
                Log.d("ContentScreen", "⏸️ Lifecycle: onPause - App perd le focus (équivalent scenePhase.inactive)")
                // Équivalent iOS: applicationWillResignActive
                saveAppState()
            }
            
            override fun onStop(owner: LifecycleOwner) {
                Log.d("ContentScreen", "🛑 Lifecycle: onStop - App en arrière-plan (équivalent scenePhase.background)")
                // Équivalent iOS: applicationDidEnterBackground
                performBackgroundCleanup()
            }
            
            override fun onDestroy(owner: LifecycleOwner) {
                Log.d("ContentScreen", "💀 Lifecycle: onDestroy - Application détruite")
                // Équivalent iOS: applicationWillTerminate
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // --- onAppear (Compose) → première ouverture + retour quotidien + observers custom
    LaunchedEffect(Unit) {
        Log.d("ContentScreen", "ContentScreen: Vue principale apparue")
        markFirstLaunchIfNeeded(analytics)
        trackDailyReturn(analytics, context)
        // setupObservers(): côté Android, on écoute déjà authEvents ci-dessus
    }

    // --- onChange équivalents (quelques logs)
    LaunchedEffect(ui.isAuthenticated) {
        Log.d("ContentScreen", "Changement authentification: ${ui.isAuthenticated}")
    }
    LaunchedEffect(ui.isOnboardingCompleted) {
        Log.d("ContentScreen", "Changement onboarding: ${ui.isOnboardingCompleted}")
    }
    LaunchedEffect(ui.currentUser) {
        Log.d("ContentScreen", "Changement utilisateur: ${ui.currentUser?.name ?: "nil"}")
    }
    LaunchedEffect(ui.isLoading) {
        Log.d("ContentScreen", "Changement chargement: ${ui.isLoading}")
    }

    // --- Deep link (Intent) ex: scheme coupleapp://subscription
    LaunchedEffect(deepLinkIntent) {
        deepLinkIntent?.data?.let { uri ->
            Log.d("ContentScreen", "🔗 DeepLink reçu: $uri")
            handleDeepLinkAndroid(uri, onOpenSubscription)
        }
    }

    // --- UI routing
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        when {
            ui.isLoading -> LaunchScreenView()               // Splash
            isTransitioning -> LoadingTransitionView()       // Vue transition 1.5s
            !ui.isAuthenticated -> AuthenticationView()      // Présentation
            ui.isAuthenticated && !ui.hasUserStartedOnboarding && !ui.isOnboardingCompleted ->
                AuthenticationView()                         // Présentation (auth auto mais onboarding pas démarré)
            ui.isAuthenticated && (ui.hasUserStartedOnboarding || ui.forceOnboarding) && !ui.isOnboardingCompleted -> {
                OnboardingView(
                    onAppear = { isOnboardingActive = true },
                    onDisappear = { isOnboardingActive = false }
                )
            }
            else -> TabContainerView()                       // App principale
        }
    }
}

// ==========================
// Helpers / Analytics / Badge
==========================

private fun logRoute(msg: String) {
    Log.d("ContentScreen", "ContentView: showing $msg")
}

private fun markFirstLaunchIfNeeded(analytics: FirebaseAnalytics) {
    val key = "has_launched_before"
    val prefs = analytics.app.applicationContext.getSharedPreferences("l2l_prefs", Context.MODE_PRIVATE)
    val isFirstLaunch = !prefs.getBoolean(key, false)
    if (isFirstLaunch) {
        analytics.logEvent("premiere_ouverture") { }
        Log.d("ContentScreen", "📊 Événement Firebase: premiere_ouverture")
        prefs.edit().putBoolean(key, true).apply()
    }
}

private fun trackDailyReturn(analytics: FirebaseAnalytics, context: Context) {
    val prefs = context.getSharedPreferences("l2l_prefs", Context.MODE_PRIVATE)
    val keyLast = "last_daily_visit"
    val keyStreakStart = "streak_start_date"

    val todayStart = dayStart(System.currentTimeMillis())
    val last = prefs.getLong(keyLast, Long.MIN_VALUE)
    val lastDayStart = if (last == Long.MIN_VALUE) Long.MIN_VALUE else dayStart(last)

    if (todayStart > lastDayStart) {
        // nouveau jour
        val streakStart = max(prefs.getLong(keyStreakStart, todayStart), todayStart)
        val consecutiveDays = daysBetween(streakStart, todayStart).coerceAtLeast(1)
        analytics.logEvent("retour_quotidien") {
            param("jours_consecutifs", consecutiveDays.toLong())
        }
        Log.d(
            "ContentScreen",
            "📊 Événement Firebase: retour_quotidien - jours_consecutifs: $consecutiveDays"
        )
        prefs.edit()
            .putLong(keyLast, todayStart)
            .apply()
        if (consecutiveDays == 1) {
            prefs.edit().putLong(keyStreakStart, todayStart).apply()
        }
    }
}

private fun dayStart(ts: Long): Long {
    // 00:00 local
    val dayMs = 24 * 60 * 60 * 1000L
    return (ts / dayMs) * dayMs
}

private fun daysBetween(startDayStart: Long, endDayStart: Long): Int {
    val dayMs = 24 * 60 * 60 * 1000L
    return ((endDayStart - startDayStart) / dayMs).toInt() + 1
}

private fun handleDeepLinkAndroid(uri: Uri, onOpenSubscription: () -> Unit) {
    if (uri.scheme != "coupleapp") {
        Log.w("ContentScreen", "❌ Scheme non reconnu: ${uri.scheme}")
        return
    }
    when (uri.host) {
        "subscription" -> {
            Log.d("ContentScreen", "🔗 Redirection vers abonnement depuis widget")
            onOpenSubscription()
        }
        else -> Log.w("ContentScreen", "❌ Host non reconnu: ${uri.host}")
    }
}

// === 🔄 LIFECYCLE HELPERS ===

private fun trackAppForegroundAnalytics() {
    // Métriques équivalentes au scenePhase iOS
    Log.d("ContentScreen", "📊 App retour premier plan - tracking analytics")
    // TODO: ajouter métriques Firebase sur temps de session, etc.
}

private fun saveAppState() {
    // Sauvegarde l'état de l'app quand elle perd le focus (équivalent iOS inactive)
    Log.d("ContentScreen", "💾 Sauvegarde état app (perte focus)")
    // TODO: sauvegarder état navigation, draft messages, etc.
}

private fun performBackgroundCleanup() {
    // Nettoyage équivalent à iOS background
    Log.d("ContentScreen", "🧹 Nettoyage arrière-plan")
    // TODO: libérer ressources, annuler tâches réseau non critiques, etc.
}

object BadgeManager {
    fun clearBadge(context: Context) {
        Log.d("BadgeManager", "🔔 Clearing badge and notifications")
        
        // Nettoyer les notifications système
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
        
        // 🎯 AMÉLIORATION: Support des badges réels avec ShortcutBadger
        try {
            // Import: me.leolin.shortcutbadger.ShortcutBadger
            val shortcutBadgerClass = Class.forName("me.leolin.shortcutbadger.ShortcutBadger")
            val removeCountMethod = shortcutBadgerClass.getMethod("removeCount", Context::class.java)
            removeCountMethod.invoke(null, context)
            Log.d("BadgeManager", "✅ Badge matériel effacé via ShortcutBadger")
        } catch (e: Exception) {
            Log.d("BadgeManager", "ℹ️ ShortcutBadger non disponible ou erreur: ${e.message}")
            // Fallback silencieux - pas critique
        }
    }
    
    fun setBadge(context: Context, count: Int) {
        Log.d("BadgeManager", "🔔 Setting badge count to $count")
        
        try {
            val shortcutBadgerClass = Class.forName("me.leolin.shortcutbadger.ShortcutBadger")
            val applyCountMethod = shortcutBadgerClass.getMethod("applyCount", Context::class.java, Int::class.java)
            applyCountMethod.invoke(null, context, count)
            Log.d("BadgeManager", "✅ Badge matériel mis à $count via ShortcutBadger")
        } catch (e: Exception) {
            Log.d("BadgeManager", "ℹ️ ShortcutBadger non disponible pour setBadge: ${e.message}")
            // Fallback: juste logger le count (pas de badge physique)
        }
    }
    
    fun isBadgeSupported(context: Context): Boolean {
        return try {
            val shortcutBadgerClass = Class.forName("me.leolin.shortcutbadger.ShortcutBadger")
            val isSupportedMethod = shortcutBadgerClass.getMethod("isBadgeCounterSupported", Context::class.java)
            isSupportedMethod.invoke(null, context) as Boolean
        } catch (e: Exception) {
            Log.d("BadgeManager", "ℹ️ ShortcutBadger non disponible pour check support: ${e.message}")
            false
        }
    }
}

// =======================================
// Composants d’écran (placeholders propres)
// =======================================

@Composable
fun LaunchScreenView() {
    // Équivalent LoadingSplashView (dégradé rose/orange, emoji “🔥”, progress)
    val gradient = Brush.linearGradient(
        listOf(Color(0xFFFD267A), Color(0xFFFF655B))
    )
    val infinite = rememberInfiniteTransition(label = "logoAnim")
    val scale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1000, easing = LinearEasing),
            RepeatMode.Reverse
        ), label = "logoScale"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🔥",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.scale(scale)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Love2Love",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Chargement…",
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun LoadingTransitionView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // remplace l’icône SF Symbols par un drawable local si tu veux
            Text(text = "✅", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Connection successful",
                color = Color.Black
            )
        }
    }
}

@Composable
fun AuthenticationView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Authentication / Presentation Screen")
    }
}

@Composable
fun OnboardingView(
    onAppear: () -> Unit = {},
    onDisappear: () -> Unit = {}
) {
    DisposableEffect(Unit) {
        onAppear()
        onDispose { onDisappear() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Onboarding Flow")
    }
}

@Composable
fun TabContainerView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Main App Tabs")
    }
}
