import XCTest
@testable import CoupleApp

/// Tests unitaires pour les nouvelles fonctionnalités de connexion partenaire
final class PartnerConnectionTests: XCTestCase {
    
    // MARK: - IntroFlags Tests
    
    func testIntroFlagsDefault() {
        let flags = IntroFlags.default
        XCTAssertFalse(flags.dailyQuestion, "Daily question intro should be false by default")
        XCTAssertFalse(flags.dailyChallenge, "Daily challenge intro should be false by default")
    }
    
    func testIntroFlagsCodable() throws {
        let originalFlags = IntroFlags(dailyQuestion: true, dailyChallenge: false)
        
        // Encode
        let data = try JSONEncoder().encode(originalFlags)
        
        // Decode
        let decodedFlags = try JSONDecoder().decode(IntroFlags.self, from: data)
        
        XCTAssertEqual(originalFlags.dailyQuestion, decodedFlags.dailyQuestion)
        XCTAssertEqual(originalFlags.dailyChallenge, decodedFlags.dailyChallenge)
    }
    
    // MARK: - DailyContentRoute Tests
    
    func testDailyContentRouteEquality() {
        // Test equality
        XCTAssertEqual(DailyContentRoute.main, DailyContentRoute.main)
        XCTAssertEqual(DailyContentRoute.loading, DailyContentRoute.loading)
        XCTAssertEqual(DailyContentRoute.intro(showConnect: true), DailyContentRoute.intro(showConnect: true))
        XCTAssertEqual(DailyContentRoute.paywall(day: 5), DailyContentRoute.paywall(day: 5))
        XCTAssertEqual(DailyContentRoute.error("test"), DailyContentRoute.error("test"))
        
        // Test inequality
        XCTAssertNotEqual(DailyContentRoute.intro(showConnect: true), DailyContentRoute.intro(showConnect: false))
        XCTAssertNotEqual(DailyContentRoute.paywall(day: 5), DailyContentRoute.paywall(day: 3))
        XCTAssertNotEqual(DailyContentRoute.error("test1"), DailyContentRoute.error("test2"))
    }
    
    func testDailyContentRouteProperties() {
        // Test computed properties
        XCTAssertTrue(DailyContentRoute.intro(showConnect: true).isIntro)
        XCTAssertFalse(DailyContentRoute.main.isIntro)
        
        XCTAssertTrue(DailyContentRoute.paywall(day: 1).isPaywall)
        XCTAssertFalse(DailyContentRoute.main.isPaywall)
        
        XCTAssertTrue(DailyContentRoute.main.isMain)
        XCTAssertFalse(DailyContentRoute.loading.isMain)
        
        XCTAssertTrue(DailyContentRoute.error("test").isError)
        XCTAssertTrue(DailyContentRoute.loading.isLoading)
        XCTAssertFalse(DailyContentRoute.main.isError)
    }
    
    // MARK: - DailyContentRouteCalculator Tests
    
    func testRouteCalculatorNoPartner() {
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: false,
            hasSeenIntro: false,
            shouldShowPaywall: false,
            paywallDay: 1,
            serviceHasError: false,
            serviceErrorMessage: nil,
            serviceIsLoading: false
        )
        
        XCTAssertEqual(route, .intro(showConnect: true), "Should show intro with connect button when no partner")
    }
    
    func testRouteCalculatorPartnerConnectedButIntroNotSeen() {
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: true,
            hasSeenIntro: false,
            shouldShowPaywall: false,
            paywallDay: 1,
            serviceHasError: false,
            serviceErrorMessage: nil,
            serviceIsLoading: false
        )
        
        XCTAssertEqual(route, .intro(showConnect: false), "Should show intro without connect button when partner connected but intro not seen")
    }
    
    func testRouteCalculatorPaywall() {
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: true,
            hasSeenIntro: true,
            shouldShowPaywall: true,
            paywallDay: 5,
            serviceHasError: false,
            serviceErrorMessage: nil,
            serviceIsLoading: false
        )
        
        XCTAssertEqual(route, .paywall(day: 5), "Should show paywall when conditions are met")
    }
    
    func testRouteCalculatorMain() {
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: true,
            hasSeenIntro: true,
            shouldShowPaywall: false,
            paywallDay: 1,
            serviceHasError: false,
            serviceErrorMessage: nil,
            serviceIsLoading: false
        )
        
        XCTAssertEqual(route, .main, "Should show main view when all conditions are met")
    }
    
    func testRouteCalculatorError() {
        let errorMessage = "Test error"
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: true,
            hasSeenIntro: true,
            shouldShowPaywall: false,
            paywallDay: 1,
            serviceHasError: true,
            serviceErrorMessage: errorMessage,
            serviceIsLoading: false
        )
        
        XCTAssertEqual(route, .error(errorMessage), "Should show error when service has error")
    }
    
    func testRouteCalculatorLoading() {
        let route = DailyContentRouteCalculator.calculateRoute(
            for: .dailyQuestion,
            hasConnectedPartner: true,
            hasSeenIntro: true,
            shouldShowPaywall: false,
            paywallDay: 1,
            serviceHasError: false,
            serviceErrorMessage: nil,
            serviceIsLoading: true
        )
        
        XCTAssertEqual(route, .loading, "Should show loading when service is loading")
    }
    
    // MARK: - ConnectionConfig Tests
    
    func testConnectionConfigConstants() {
        XCTAssertGreaterThan(ConnectionConfig.preparingMinDuration, 0)
        XCTAssertGreaterThan(ConnectionConfig.preparingMaxTimeout, ConnectionConfig.preparingMinDuration)
        XCTAssertGreaterThan(ConnectionConfig.readinessCheckInterval, 0)
    }
    
    func testConnectionContext() {
        let context = ConnectionConfig.ConnectionContext.dailyQuestion
        XCTAssertEqual(context.rawValue, "daily_question")
        XCTAssertEqual(context.displayName, "Question du Jour")
    }
    
    func testIntroFlagsKey() {
        let coupleId = "user1_user2"
        let key = ConnectionConfig.introFlagsKey(for: coupleId)
        XCTAssertEqual(key, "introFlags_user1_user2")
    }
    
    // MARK: - PartnerConnectionSuccessView Mode Tests
    
    func testSuccessViewModeDisplayName() {
        XCTAssertEqual(PartnerConnectionSuccessView.Mode.simpleDismiss.displayName, "simple")
        XCTAssertEqual(PartnerConnectionSuccessView.Mode.waitForServices.displayName, "wait_services")
    }
}

// MARK: - Mock Classes for Testing

/// Mock AppState pour les tests
class MockAppState: AppState {
    var mockIntroFlags = IntroFlags.default
    var mockCurrentUser: AppUser?
    
    override var introFlags: IntroFlags {
        get { mockIntroFlags }
        set { mockIntroFlags = newValue }
    }
    
    override var currentUser: AppUser? {
        get { mockCurrentUser }
        set { mockCurrentUser = newValue }
    }
    
    override func markDailyQuestionIntroAsSeen() {
        mockIntroFlags.dailyQuestion = true
    }
    
    override func markDailyChallengeIntroAsSeen() {
        mockIntroFlags.dailyChallenge = true
    }
}

/// Mock AnalyticsService pour les tests
class MockAnalyticsService: AnalyticsService {
    var trackedEvents: [ConnectionConfig.AnalyticsEvent] = []
    
    override func track(_ event: ConnectionConfig.AnalyticsEvent) {
        trackedEvents.append(event)
        // Ne pas appeler super pour éviter les appels Firebase en test
    }
}