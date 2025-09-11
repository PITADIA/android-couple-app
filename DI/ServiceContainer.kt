package com.love2loveapp.di

import android.content.Context
import com.love2loveapp.core.services.auth.AuthenticationService
import com.love2loveapp.core.services.firebase.FirebaseAuthService
import com.love2loveapp.core.services.firebase.FirebaseUserService
import com.love2loveapp.core.services.firebase.FirebaseFunctionsService
import com.love2loveapp.core.services.firebase.FirebaseCoordinator
import com.love2loveapp.core.services.location.LocationService
import com.love2loveapp.core.services.cache.UserCacheManager
import com.love2loveapp.core.services.performance.PerformanceMonitor
import com.love2loveapp.core.services.DailyQuestionService
import com.love2loveapp.core.services.WidgetService
import com.love2loveapp.data.repository.UserRepositoryImpl
import com.love2loveapp.data.repository.LocationRepositoryImpl
import com.love2loveapp.domain.repository.UserRepository
import com.love2loveapp.domain.repository.LocationRepository
import com.love2loveapp.core.viewmodels.AppState
import com.love2loveapp.core.viewmodels.DailyChallengeViewModel
import com.love2loveapp.core.viewmodels.WidgetsViewModel
import com.love2loveapp.core.viewmodels.OnboardingViewModel
import com.love2loveapp.core.viewmodels.freemium.FreemiumManager

/**
 * 🏗️ ServiceContainer - Point Central de Dependency Injection
 * 
 * Responsabilités :
 * - Gestion centralisée de tous les services (singletons + factories)
 * - Configuration Firebase coordonnée
 * - Injection de dépendances pour ViewModels
 * - Cycle de vie des services
 * 
 * Pattern : Service Locator + Factory
 */
object ServiceContainer {
    
    // === Context Android ===
    private var appContext: Context? = null
    
    /**
     * Initialisation obligatoire depuis Application.onCreate()
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        initializeFirebaseServices()
    }
    
    // === Core Services (Singletons) ===
    
    val performanceMonitor: PerformanceMonitor by lazy {
        PerformanceMonitor.getInstance()
    }
    
    val userCacheManager: UserCacheManager by lazy {
        UserCacheManager.getInstance()
    }
    
    val questionCacheManager: QuestionCacheManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        QuestionCacheManager.getInstance(appContext)
    }
    
    // === Firebase Services (Coordonnés) ===
    
    val firebaseCoordinator: FirebaseCoordinator by lazy {
        FirebaseCoordinator.getInstance()
    }
    
    val firebaseAuthService: FirebaseAuthService by lazy {
        FirebaseAuthService().apply {
            configure(firebaseCoordinator)
        }
    }
    
    val firebaseUserService: FirebaseUserService by lazy {
        FirebaseUserService().apply {
            configure(firebaseCoordinator, userCacheManager)
        }
    }
    
    val firebaseFunctionsService: FirebaseFunctionsService by lazy {
        FirebaseFunctionsService
    }
    
    // === Business Services ===
    
    val authenticationService: AuthenticationService by lazy {
        AuthenticationService().apply {
            configure(firebaseAuthService, userCacheManager)
        }
    }
    
    val locationService: LocationService by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        LocationService.getInstance(appContext).apply {
            configure(firebaseUserService)
        }
    }
    
    val dailyQuestionService: DailyQuestionService by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        DailyQuestionService.getInstance(appContext)
    }
    
    val questionDataManager: QuestionDataManager by lazy {
        QuestionDataManager.getInstance().apply {
            configure(questionCacheManager, firebaseUserService, performanceMonitor)
        }
    }
    
    val widgetService: WidgetService by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        WidgetService.getInstance(appContext).apply {
            configure(firebaseUserService, locationService)
        }
    }
    
    // === Navigation Service ===
    
    private var navigationManager: NavigationManager? = null
    
    /**
     * NavigationManager - Nécessite AppState, donc factory method
     */
    fun createNavigationManager(appState: IntegratedAppState): NavigationManager {
        return navigationManager ?: NavigationManager(context, appState).also {
            navigationManager = it
        }
    }
    
    // === Service Managers (Domaines Métier) ===
    
    val partnerServiceManager: PartnerServiceManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        PartnerServiceManager(appContext, firebaseUserService)
    }
    
    val contentServiceManager: ContentServiceManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        ContentServiceManager(appContext, firebaseUserService, firebaseFunctionsService)
    }
    
    val systemServiceManager: SystemServiceManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        SystemServiceManager(appContext, firebaseUserService)
    }
    
    // === Additional Managers (Tests, UI, Utils, ViewModels) ===
    
    val testManager: TestManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        TestManager.getInstance(appContext)
    }
    
    val utilsManager: UtilsManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        UtilsManager(appContext, firebaseUserService, firebaseFunctionsService)
    }
    
    val viewModelManager: ViewModelManager by lazy {
        requireNotNull(appContext) { "ServiceContainer not initialized" }
        ViewModelManager.getInstance(appContext)
    }
    
    private var uiManager: UIManager? = null
    
    /**
     * UIManager - Nécessite IntegratedAppState, donc factory method
     */
    fun createUIManager(appState: IntegratedAppState): UIManager {
        return uiManager ?: UIManager(context, appState).also {
            uiManager = it
        }
    }
    
    private var viewManagerOrchestrator: ViewManagerOrchestrator? = null
    
    /**
     * ViewManagerOrchestrator - Orchestrateur central de toutes les vues
     */
    fun createViewManagerOrchestrator(appState: IntegratedAppState): ViewManagerOrchestrator {
        return viewManagerOrchestrator ?: ViewManagerOrchestrator(
            context = context,
            integratedAppState = appState,
            uiManager = createUIManager(appState)
        ).also {
            viewManagerOrchestrator = it
        }
    }
    
    // === Repository Layer ===
    
    val userRepository: UserRepository by lazy {
        UserRepositoryImpl(
            firebaseAuthService = firebaseAuthService,
            firebaseUserService = firebaseUserService,
            userCacheManager = userCacheManager
        )
    }
    
    val locationRepository: LocationRepository by lazy {
        LocationRepositoryImpl(
            locationService = locationService,
            firebaseUserService = firebaseUserService
        )
    }
    
    // === AppState Central ===
    
    /**
     * Factory method pour AppState - Point central de l'app
     */
    fun createAppState(): AppState {
        return AppState(
            userRepository = userRepository,
            locationRepository = locationRepository,
            authenticationService = authenticationService,
            performanceMonitor = performanceMonitor,
            firebaseCoordinator = firebaseCoordinator
        )
    }
    
    // === ViewModel Factories ===
    
    /**
     * Factory pour DailyChallengeViewModel avec toutes ses dépendances
     */
    fun createDailyChallengeViewModel(): ConnectedDailyChallengeViewModel {
        return ConnectedDailyChallengeViewModel(
            userRepository = userRepository,
            dailyQuestionService = dailyQuestionService,
            questionDataManager = questionDataManager,
            firebaseFunctionsService = firebaseFunctionsService
        )
    }
    
    /**
     * Factory pour WidgetsViewModel avec toutes ses dépendances
     */
    fun createWidgetsViewModel(): WidgetsViewModel {
        return WidgetsViewModel(
            userRepository = userRepository,
            locationRepository = locationRepository,
            widgetService = widgetService
        )
    }
    
    // === Service Manager Factories ===
    
    /**
     * Factory pour PartnerViewModel connecté
     */
    fun createPartnerViewModel(): PartnerViewModel {
        return PartnerViewModel(
            partnerServiceManager = partnerServiceManager,
            analyticsService = systemServiceManager.getAnalyticsService()
        )
    }
    
    /**
     * Factory pour ContentViewModel connecté
     */
    fun createContentViewModel(): ContentViewModel {
        return ContentViewModel(
            contentServiceManager = contentServiceManager,
            analyticsService = systemServiceManager.getAnalyticsService()
        )
    }
    
    /**
     * Factory pour SystemViewModel connecté
     */
    fun createSystemViewModel(): SystemViewModel {
        return SystemViewModel(
            systemServiceManager = systemServiceManager
        )
    }
    
    /**
     * Factory pour OnboardingViewModel avec toutes ses dépendances
     */
    fun createOnboardingViewModel(): OnboardingViewModel {
        return OnboardingViewModel(
            userRepository = userRepository,
            authenticationService = authenticationService,
            performanceMonitor = performanceMonitor
        )
    }
    
    /**
     * Factory pour FreemiumManager avec toutes ses dépendances
     */
    fun createFreemiumManager(appState: AppState): FreemiumManager {
        return FreemiumManager(
            appState = appState,
            userRepository = userRepository
        )
    }
    
    // === Configuration Privée ===
    
    /**
     * Configuration coordonnée des services Firebase
     */
    private fun initializeFirebaseServices() {
        // 1. Firebase Coordinator (central)
        firebaseCoordinator.initialize()
        
        // 2. Services Firebase (ordre important)
        firebaseAuthService // lazy init
        firebaseUserService // lazy init
        
        // 3. Performance monitoring
        performanceMonitor.startMonitoring()
        
        // 4. Cache utilisateur
        userCacheManager.initialize()
    }
    
    // === Lifecycle Management ===
    
    /**
     * Nettoyage des ressources (appelé depuis Application.onTerminate)
     */
    fun cleanup() {
        performanceMonitor.stopMonitoring()
        userCacheManager.cleanup()
        locationService.stopLocationUpdates()
        firebaseCoordinator.cleanup()
    }
    
    // === Debug & Testing ===
    
    /**
     * Vérification de l'état des services (debug)
     */
    fun getServicesStatus(): Map<String, String> {
        return mapOf(
            "appContext" to if (appContext != null) "✅ Initialized" else "❌ Not initialized",
            "firebaseCoordinator" to if (firebaseCoordinator.isInitialized()) "✅ Ready" else "❌ Not ready",
            "authenticationService" to if (authenticationService.isConfigured()) "✅ Configured" else "❌ Not configured",
            "locationService" to if (locationService.isConfigured()) "✅ Configured" else "❌ Not configured",
            "userRepository" to "✅ Available",
            "locationRepository" to "✅ Available"
        )
    }
}

/**
 * Extension pour vérifier l'initialisation
 */
val ServiceContainer.isInitialized: Boolean
    get() = appContext != null

/**
 * Extension pour obtenir le contexte (safe)
 */
val ServiceContainer.context: Context
    get() = requireNotNull(appContext) { "ServiceContainer not initialized. Call initialize() first." }
