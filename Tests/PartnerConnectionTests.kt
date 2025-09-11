// File: PartnerConnectionTests.kt
@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.love2loveapp.core.tests

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// -----------------------------
// Android-like resources (tests)
// -----------------------------
object R {
    object string {
        const val daily_question_display_name = 1
        // Ajoute ici d’autres IDs si besoin, ex:
        // const val daily_challenge_display_name = 2
    }
}

/**
 * Abstraction côté tests pour éviter un vrai Android Context.
 * En prod : utilise context.getString(id) ou stringResource(id).
 */
fun interface StringProvider {
    fun getString(id: Int): String
}

// Un "fake" provider pour les tests unitaires JVM
private val testStrings = StringProvider { id ->
    when (id) {
        R.string.daily_question_display_name -> "Question du Jour"
        else -> "MISSING_STRING_$id"
    }
}

// -----------------------------
// Modèles et logique applicative
// -----------------------------

@Serializable
data class IntroFlags(
    var dailyQuestion: Boolean = false,
    var dailyChallenge: Boolean = false
) {
    companion object {
        val default = IntroFlags(dailyQuestion = false, dailyChallenge = false)
    }
}

sealed interface DailyContentRoute {
    data object Main : DailyContentRoute
    data object Loading : DailyContentRoute
    data class Intro(val showConnect: Boolean) : DailyContentRoute
    data class Paywall(val day: Int) : DailyContentRoute
    data class Error(val message: String) : DailyContentRoute
}

val DailyContentRoute.isIntro get() = this is DailyContentRoute.Intro
val DailyContentRoute.isPaywall get() = this is DailyContentRoute.Paywall
val DailyContentRoute.isMain get() = this === DailyContentRoute.Main
val DailyContentRoute.isError get() = this is DailyContentRoute.Error
val DailyContentRoute.isLoading get() = this === DailyContentRoute.Loading

enum class DailyContentKind { DAILY_QUESTION, DAILY_CHALLENGE }

object DailyContentRouteCalculator {
    /**
     * Équivalent de calculateRoute(for:hasConnectedPartner:...) côté Swift.
     */
    fun calculateRoute(
        kind: DailyContentKind,
        hasConnectedPartner: Boolean,
        hasSeenIntro: Boolean,
        shouldShowPaywall: Boolean,
        paywallDay: Int,
        serviceHasError: Boolean,
        serviceErrorMessage: String?,
        serviceIsLoading: Boolean
    ): DailyContentRoute {
        return when {
            serviceHasError -> DailyContentRoute.Error(serviceErrorMessage ?: "Unknown error")
            serviceIsLoading -> DailyContentRoute.Loading
            !hasConnectedPartner -> DailyContentRoute.Intro(showConnect = true)
            !hasSeenIntro -> DailyContentRoute.Intro(showConnect = false)
            shouldShowPaywall -> DailyContentRoute.Paywall(day = paywallDay)
            else -> DailyContentRoute.Main
        }
    }
}

object ConnectionConfig {
    // Valeurs arbitraires > 0 pour satisfaire les tests
    const val preparingMinDuration: Long = 1_000L
    const val preparingMaxTimeout: Long = 10_000L
    const val readinessCheckInterval: Long = 250L

    enum class ConnectionContext(val rawValue: String, private val displayNameResId: Int) {
        DAILY_QUESTION("daily_question", R.string.daily_question_display_name);
        // DAILY_CHALLENGE("daily_challenge", R.string.daily_challenge_display_name);

        fun displayName(strings: StringProvider): String = strings.getString(displayNameResId)
    }

    // Clé de persistance des flags d’intro (équivalent Swift)
    fun introFlagsKey(forCoupleId: String): String = "introFlags_$forCoupleId"

    // Événements analytics (placeholder pour compatibilité tests/mocks)
    sealed class AnalyticsEvent(val name: String) {
        data object ConnectAttempt : AnalyticsEvent("connect_attempt")
        data object ConnectSuccess : AnalyticsEvent("connect_success")
        data class Error(val reason: String) : AnalyticsEvent("connect_error")
    }
}

object PartnerConnectionSuccessView {
    enum class Mode {
        SIMPLE_DISMISS,
        WAIT_FOR_SERVICES;

        val displayName: String
            get() = when (this) {
                SIMPLE_DISMISS -> "simple"
                WAIT_FOR_SERVICES -> "wait_services"
            }
    }
}

// Modèle utilisateur minimal pour compatibilité des mocks
data class AppUser(val id: String, val name: String? = null)

// État global minimal (base) – sera moqué dans les tests
open class AppState {
    open var introFlags: IntroFlags = IntroFlags.default
    open var currentUser: AppUser? = null

    open fun markDailyQuestionIntroAsSeen() {
        introFlags = introFlags.copy(dailyQuestion = true)
    }

    open fun markDailyChallengeIntroAsSeen() {
        introFlags = introFlags.copy(dailyChallenge = true)
    }
}

// Service analytics minimal (base) – sera moqué dans les tests
open class AnalyticsService {
    open fun track(event: ConnectionConfig.AnalyticsEvent) {
        // No-op base
    }
}

// -----------------------------
// Tests unitaires (JUnit4)
// -----------------------------
class PartnerConnectionTests {

    // MARK: - IntroFlags Tests

    @Test
    fun testIntroFlagsDefault() {
        val flags = IntroFlags.default
        assertFalse("Daily question intro should be false by default", flags.dailyQuestion)
        assertFalse("Daily challenge intro should be false by default", flags.dailyChallenge)
    }

    @Test
    fun testIntroFlagsCodable() {
        // Nécessite kotlinx-serialization-json dans les deps de tests
        val originalFlags = IntroFlags(dailyQuestion = true, dailyChallenge = false)

        val json = Json.encodeToString(IntroFlags.serializer(), originalFlags)
        val decoded = Json.decodeFromString(IntroFlags.serializer(), json)

        assertEquals(originalFlags.dailyQuestion, decoded.dailyQuestion)
        assertEquals(originalFlags.dailyChallenge, decoded.dailyChallenge)
    }

    // MARK: - DailyContentRoute Tests

    @Test
    fun testDailyContentRouteEquality() {
        // Equality
        assertEquals(DailyContentRoute.Main, DailyContentRoute.Main)
        assertEquals(DailyContentRoute.Loading, DailyContentRoute.Loading)
        assertEquals(
            DailyContentRoute.Intro(showConnect = true),
            DailyContentRoute.Intro(showConnect = true)
        )
        assertEquals(DailyContentRoute.Paywall(day = 5), DailyContentRoute.Paywall(day = 5))
        assertEquals(DailyContentRoute.Error("test"), DailyContentRoute.Error("test"))

        // Inequality
        assertNotEquals(
            DailyContentRoute.Intro(showConnect = true),
            DailyContentRoute.Intro(showConnect = false)
        )
        assertNotEquals(
            DailyContentRoute.Paywall(day = 5),
            DailyContentRoute.Paywall(day = 3)
        )
        assertNotEquals(
            DailyContentRoute.Error("test1"),
            DailyContentRoute.Error("test2")
        )
    }

    @Test
    fun testDailyContentRouteProperties() {
        assertTrue(DailyContentRoute.Intro(showConnect = true).isIntro)
        assertFalse(DailyContentRoute.Main.isIntro)

        assertTrue(DailyContentRoute.Paywall(day = 1).isPaywall)
        assertFalse(DailyContentRoute.Main.isPaywall)

        assertTrue(DailyContentRoute.Main.isMain)
        assertFalse(DailyContentRoute.Loading.isMain)

        assertTrue(DailyContentRoute.Error("test").isError)
        assertTrue(DailyContentRoute.Loading.isLoading)
        assertFalse(DailyContentRoute.Main.isError)
    }

    // MARK: - DailyContentRouteCalculator Tests

    @Test
    fun testRouteCalculatorNoPartner() {
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = false,
            hasSeenIntro = false,
            shouldShowPaywall = false,
            paywallDay = 1,
            serviceHasError = false,
            serviceErrorMessage = null,
            serviceIsLoading = false
        )
        assertEquals(
            DailyContentRoute.Intro(showConnect = true),
            route
        )
    }

    @Test
    fun testRouteCalculatorPartnerConnectedButIntroNotSeen() {
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = true,
            hasSeenIntro = false,
            shouldShowPaywall = false,
            paywallDay = 1,
            serviceHasError = false,
            serviceErrorMessage = null,
            serviceIsLoading = false
        )
        assertEquals(
            DailyContentRoute.Intro(showConnect = false),
            route
        )
    }

    @Test
    fun testRouteCalculatorPaywall() {
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = true,
            hasSeenIntro = true,
            shouldShowPaywall = true,
            paywallDay = 5,
            serviceHasError = false,
            serviceErrorMessage = null,
            serviceIsLoading = false
        )
        assertEquals(DailyContentRoute.Paywall(day = 5), route)
    }

    @Test
    fun testRouteCalculatorMain() {
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = true,
            hasSeenIntro = true,
            shouldShowPaywall = false,
            paywallDay = 1,
            serviceHasError = false,
            serviceErrorMessage = null,
            serviceIsLoading = false
        )
        assertEquals(DailyContentRoute.Main, route)
    }

    @Test
    fun testRouteCalculatorError() {
        val errorMessage = "Test error"
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = true,
            hasSeenIntro = true,
            shouldShowPaywall = false,
            paywallDay = 1,
            serviceHasError = true,
            serviceErrorMessage = errorMessage,
            serviceIsLoading = false
        )
        assertEquals(DailyContentRoute.Error(errorMessage), route)
    }

    @Test
    fun testRouteCalculatorLoading() {
        val route = DailyContentRouteCalculator.calculateRoute(
            kind = DailyContentKind.DAILY_QUESTION,
            hasConnectedPartner = true,
            hasSeenIntro = true,
            shouldShowPaywall = false,
            paywallDay = 1,
            serviceHasError = false,
            serviceErrorMessage = null,
            serviceIsLoading = true
        )
        assertEquals(DailyContentRoute.Loading, route)
    }

    // MARK: - ConnectionConfig Tests

    @Test
    fun testConnectionConfigConstants() {
        assertTrue(ConnectionConfig.preparingMinDuration > 0)
        assertTrue(ConnectionConfig.preparingMaxTimeout > ConnectionConfig.preparingMinDuration)
        assertTrue(ConnectionConfig.readinessCheckInterval > 0)
    }

    @Test
    fun testConnectionContext() {
        val ctx = ConnectionConfig.ConnectionContext.DAILY_QUESTION
        assertEquals("daily_question", ctx.rawValue)
        // ⚠️ En prod : ctx.displayName(context.getString(...))
        assertEquals("Question du Jour", ctx.displayName(testStrings))
    }

    @Test
    fun testIntroFlagsKey() {
        val coupleId = "user1_user2"
        val key = ConnectionConfig.introFlagsKey(forCoupleId = coupleId)
        assertEquals("introFlags_user1_user2", key)
    }

    // MARK: - PartnerConnectionSuccessView Mode Tests

    @Test
    fun testSuccessViewModeDisplayName() {
        assertEquals("simple", PartnerConnectionSuccessView.Mode.SIMPLE_DISMISS.displayName)
        assertEquals("wait_services", PartnerConnectionSuccessView.Mode.WAIT_FOR_SERVICES.displayName)
    }
}

// -----------------------------
// Mocks pour les tests (équivalents Swift)
// -----------------------------

class MockAppState : AppState() {
    var mockIntroFlags: IntroFlags = IntroFlags.default
    var mockCurrentUser: AppUser? = null

    override var introFlags: IntroFlags
        get() = mockIntroFlags
        set(value) { mockIntroFlags = value }

    override var currentUser: AppUser?
        get() = mockCurrentUser
        set(value) { mockCurrentUser = value }

    override fun markDailyQuestionIntroAsSeen() {
        mockIntroFlags = mockIntroFlags.copy(dailyQuestion = true)
    }

    override fun markDailyChallengeIntroAsSeen() {
        mockIntroFlags = mockIntroFlags.copy(dailyChallenge = true)
    }
}

class MockAnalyticsService : AnalyticsService() {
    val trackedEvents = mutableListOf<ConnectionConfig.AnalyticsEvent>()
    override fun track(event: ConnectionConfig.AnalyticsEvent) {
        trackedEvents += event
        // ne pas appeler super pour éviter tout effet externe en tests
    }
}
