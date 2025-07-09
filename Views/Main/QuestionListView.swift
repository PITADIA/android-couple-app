import SwiftUI

struct QuestionListView: View {
    let category: QuestionCategory
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var questionCacheManager: QuestionCacheManager
    @EnvironmentObject private var favoritesService: FavoritesService
    @EnvironmentObject private var appState: AppState
    @StateObject private var packProgressService = PackProgressService.shared
    @StateObject private var categoryProgressService = CategoryProgressService.shared
    
    @State private var currentIndex = 0
    @State private var cachedQuestions: [Question] = []
    @State private var accessibleQuestions: [Question] = []
    @State private var isQuestionsLoaded = false
    @State private var dragOffset = CGSize.zero
    
    // États pour les écrans de pack
    @State private var showNewPackReveal = false
    @State private var completedPackNumber = 1
    @State private var showPackCompletionCard = false
    

    
    private var currentQuestionIndex: Int {
        return currentIndex
    }
    
    // NOUVEAU: Vérifier si l'utilisateur peut accéder à la question actuelle
    private var canAccessCurrentQuestion: Bool {
        guard let freemiumManager = appState.freemiumManager else { return true }
        return freemiumManager.canAccessQuestion(at: currentIndex, in: category)
    }
    
    // NOUVEAU: Vérifier si la question suivante est bloquée
    private var isNextQuestionBlocked: Bool {
        guard let freemiumManager = appState.freemiumManager else { return false }
        let nextIndex = currentIndex + 1
        return nextIndex < cachedQuestions.count && !freemiumManager.canAccessQuestion(at: nextIndex, in: category)
    }
    
    // OPTIMISATION: Ne retourner que les questions visibles (3 maximum) + carte de fin si nécessaire
    private var visibleQuestions: [(Int, Question)] {
        guard !accessibleQuestions.isEmpty else { return [] }
        
        let startIndex = max(0, currentIndex - 1)
        let endIndex = min(accessibleQuestions.count - 1, currentIndex + 1)
        
        var result: [(Int, Question)] = []
        for i in startIndex...endIndex {
            result.append((i, accessibleQuestions[i]))
        }
        return result
    }
    
    // Vérifier si on doit afficher la carte de fin de pack
    private var shouldShowCompletionCard: Bool {
        return showPackCompletionCard && currentIndex >= accessibleQuestions.count
    }
    
    // NOUVEAU: Vérifier si on doit afficher la carte de paywall freemium
    private var shouldShowFreemiumPaywall: Bool {
        guard let freemiumManager = appState.freemiumManager else { return false }
        let maxFreeQuestions = freemiumManager.getMaxFreeQuestions(for: category)
        let isUserSubscribed = appState.currentUser?.isSubscribed ?? false
        
        // Afficher le paywall si:
        // 1. L'utilisateur n'est pas abonné
        // 2. Ce n'est pas une catégorie premium (car premium = paywall immédiat)
        // 3. On a atteint la limite de questions gratuites
        // 4. Il y a plus de questions disponibles que la limite gratuite
        return !isUserSubscribed && 
               !category.isPremium && 
               currentIndex >= maxFreeQuestions && 
               cachedQuestions.count > maxFreeQuestions
    }
    
    // Nombre total d'éléments (questions + carte de fin éventuelle + paywall éventuel)
    private var totalItems: Int {
        var count = accessibleQuestions.count
        
        // Ajouter 1 pour la carte de déblocage si on peut débloquer un pack
        if accessibleQuestions.count < cachedQuestions.count {
            count += 1
        }
        
        if showPackCompletionCard { count += 1 }
        if shouldShowFreemiumPaywall { count += 1 }
        
        print("🔍 DEBUG totalItems: accessibleQuestions.count=\(accessibleQuestions.count), cachedQuestions.count=\(cachedQuestions.count), showPackCompletionCard=\(showPackCompletionCard), shouldShowFreemiumPaywall=\(shouldShowFreemiumPaywall), total=\(count)")
        
        return count
    }
    
    private func loadQuestions() {
        guard !isQuestionsLoaded else { return }
        
        // SUPPRESSION DES LOGS EXCESSIFS - Garder seulement l'essentiel
        print("QuestionListView: Chargement catégorie '\(category.title)'")
        
        // Utilisation du cache intelligent Realm
        let questions = questionCacheManager.getQuestionsWithSmartCache(for: category.title) {
            // Fallback: mapping vers les clés originales si pas de cache
            let questionKey: String
            switch category.title {
            case "Désirs Inavoués":
                questionKey = "LES PLUS HOTS"
            case "À travers la distance":
                questionKey = "À DISTANCE"
            case "Des questions profondes":
                questionKey = "QUESTIONS PROFONDES"
            case "Tu préfères quoi ?":
                questionKey = "TU PRÉFÈRES ?"

            case "En date":
                questionKey = "POUR UN DATE"
            case "En couple":
                questionKey = "EN COUPLE"
            case "Réparer notre amour":
                questionKey = "MIEUX ENSEMBLE"
            default:
                questionKey = category.title
            }
            
            return Question.sampleQuestions[questionKey] ?? []
        }
        
        cachedQuestions = questions
        
        // Utiliser le système de packs original avec limitation freemium
        accessibleQuestions = packProgressService.getAccessibleQuestions(
            from: questions, 
            categoryTitle: category.title
        )
        

        
        // 🔥 NOUVEAU: Restaurer la position sauvegardée
        let savedIndex = categoryProgressService.getCurrentIndex(for: category.title)
        if savedIndex < accessibleQuestions.count {
            currentIndex = savedIndex
            print("🔥 QuestionListView: Position restaurée à l'index \(savedIndex) pour '\(category.title)'")
        } else {
            currentIndex = 0
            print("🔥 QuestionListView: Position sauvegardée invalide, démarrage à 0")
        }
        
        isQuestionsLoaded = true
        
        let unlockedPacks = packProgressService.getUnlockedPacks(for: category.title)
        print("QuestionListView: \(questions.count) questions totales, \(accessibleQuestions.count) accessibles (Pack \(unlockedPacks))")
        
        // Log freemium
        if let freemiumManager = appState.freemiumManager {
            let maxFreeQuestions = freemiumManager.getMaxFreeQuestions(for: category)
            if maxFreeQuestions < questions.count && !category.isPremium {
                print("🔥🔥🔥 FREEMIUM: Limite à \(maxFreeQuestions) questions pour '\(category.title)' (utilisateurs gratuits)")
            }
        }
    }
    
    var body: some View {
        ZStack {
            // Même fond que la page principale mais avec plus de rouge visible
            Color(red: 0.15, green: 0.03, blue: 0.08)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec navigation
                HStack {
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.white)
                    }
                    
                    Spacer()
                    
                    // Compteur de questions
                    Text("\(currentQuestionIndex + 1) sur \(totalItems)")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                    
                    Spacer()
                    
                    Button(action: {
                        // Action refresh/restart + reset progression
                        currentIndex = 0
                        dragOffset = .zero
                        showPackCompletionCard = false
                        
                        // 🔥 RESET COMPLET pour debug
                        packProgressService.resetProgress(for: category.title)
                        categoryProgressService.saveCurrentIndex(0, for: category.title)
                        
                        // Recharger les questions
                        accessibleQuestions = packProgressService.getAccessibleQuestions(
                            from: cachedQuestions, 
                            categoryTitle: category.title
                        )
                        
                        print("🔄 RESET COMPLET: Progression réinitialisée pour \(category.title)")
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 20, weight: .medium))
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 60)
                .padding(.bottom, 40)
                
                // LOGIQUE CONDITIONNELLE CORRIGÉE
                if accessibleQuestions.isEmpty {
                    // Message si vraiment pas de questions
                    VStack(spacing: 20) {
                        Text("🔒")
                            .font(.system(size: 60))
                        
                        Text("Contenu Premium")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("Abonnez-vous pour accéder à toutes les questions")
                            .font(.system(size: 16))
                            .foregroundColor(.white.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 40)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    // RENDU OPTIMISÉ: Seulement 3 cartes maximum
                    GeometryReader { geometry in
                        let cardWidth = geometry.size.width - 40
                        let cardSpacing: CGFloat = 30
                        
                        ZStack {
                            // Afficher les questions normales
                            ForEach(visibleQuestions, id: \.0) { indexAndQuestion in
                                let (index, question) = indexAndQuestion
                                let offset = CGFloat(index - currentQuestionIndex)
                                let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width
                                
                                QuestionCardView(
                                    question: question,
                                    category: category,
                                    isBackground: index != currentQuestionIndex
                                )
                                .frame(width: cardWidth)
                                .offset(x: xPosition)
                                .scaleEffect(index == currentQuestionIndex ? 1.0 : 0.95)
                                .opacity(index == currentQuestionIndex ? 1.0 : 0.8)
                            }
                            
                            // NOUVEAU: Afficher la carte de paywall freemium si nécessaire
                            if shouldShowFreemiumPaywall {
                                let paywallIndex = accessibleQuestions.count
                                let offset = CGFloat(paywallIndex - currentQuestionIndex)
                                let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width
                                
                                FreemiumPaywallCardView(
                                    category: category,
                                    questionsUnlocked: accessibleQuestions.count,
                                    totalQuestions: cachedQuestions.count
                                ) {
                                    // Action quand on tape sur la carte paywall
                                    handlePaywallTap()
                                }
                                .frame(width: cardWidth)
                                .offset(x: xPosition)
                                .scaleEffect(currentQuestionIndex == paywallIndex ? 1.0 : 0.95)
                                .opacity(currentQuestionIndex == paywallIndex ? 1.0 : 0.8)
                            }
                            
                            // Afficher la carte de fin de pack si elle doit être visible
                            if showPackCompletionCard {
                                let completionCardIndex = accessibleQuestions.count
                                let offset = CGFloat(completionCardIndex - currentQuestionIndex)
                                let xPosition = offset * (cardWidth + cardSpacing) + dragOffset.width
                                
                                PackCompletionCardView(
                                    category: category,
                                    packNumber: completedPackNumber
                                ) {
                                    // Action quand on tape sur la carte
                                    showNewPackReveal = true
                                }
                                .frame(width: cardWidth)
                                .offset(x: xPosition)
                                .scaleEffect(currentQuestionIndex == completionCardIndex ? 1.0 : 0.95)
                                .opacity(currentQuestionIndex == completionCardIndex ? 1.0 : 0.8)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .position(x: geometry.size.width / 2, y: geometry.size.height / 2)
                        .gesture(
                            DragGesture()
                                .onChanged { value in
                                    dragOffset = value.translation
                                }
                                .onEnded { value in
                                    let threshold: CGFloat = 80
                                    let velocity = value.predictedEndTranslation.width - value.translation.width
                                    
                                    withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                                        if value.translation.width > threshold || velocity > 500 {
                                            // Swipe vers la droite - question précédente
                                            if currentQuestionIndex > 0 {
                                                currentIndex -= 1
                                                // 🔥 NOUVEAU: Sauvegarder la position actuelle
                                                categoryProgressService.saveCurrentIndex(currentIndex, for: category.title)
                                            }
                                        } else if value.translation.width < -threshold || velocity < -500 {
                                            // Swipe vers la gauche - question suivante
                                            print("🔍 SWIPE GAUCHE: currentIndex=\(currentIndex), totalItems=\(totalItems)")
                                            
                                            if currentQuestionIndex < totalItems - 1 {
                                                // NOUVEAU: Vérifier l'accès freemium avant de passer à la question suivante
                                                let nextIndex = currentIndex + 1
                                                print("🔍 SWIPE GAUCHE: nextIndex=\(nextIndex), accessibleQuestions.count=\(accessibleQuestions.count)")
                                                
                                                currentIndex = nextIndex
                                                // 🔥 NOUVEAU: Sauvegarder la position actuelle
                                                categoryProgressService.saveCurrentIndex(currentIndex, for: category.title)
                                                
                                                print("🔍 SWIPE GAUCHE: Après mise à jour currentIndex=\(currentIndex)")
                                                
                                                // Vérifier si on a terminé un pack (mais ne pas afficher automatiquement)
                                                checkForPackCompletionCard()
                                                
                                                                                // NOUVEAU: Vérifier si on doit afficher le paywall freemium
                                if let freemiumManager = appState.freemiumManager {
                                    let maxFreeQuestions = freemiumManager.getMaxFreeQuestions(for: category)
                                    if nextIndex >= maxFreeQuestions && !category.isPremium && !(appState.currentUser?.isSubscribed ?? false) {
                                        print("🔥🔥🔥 FREEMIUM: Limite atteinte à la question \(nextIndex + 1), carte paywall disponible")
                                        // Ne pas déclencher automatiquement le paywall, juste le rendre disponible
                                        // L'utilisateur devra cliquer sur la carte pour l'ouvrir
                                    }
                                }
                                            } else {
                                                print("🔍 SWIPE GAUCHE: Limite atteinte, pas de navigation")
                                            }
                                        }
                                        
                                        // Remettre la carte en place
                                        dragOffset = .zero
                                    }
                                }
                        )
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal, 20)
                }
                
                // Bouton Ajouter en favoris (seulement pour les questions, pas pour la carte de fin)
                if !shouldShowCompletionCard && !shouldShowFreemiumPaywall {
                    Button(action: {
                        if currentQuestionIndex < accessibleQuestions.count {
                            let currentQuestion = accessibleQuestions[currentQuestionIndex]
                            Task { @MainActor in
                                favoritesService.toggleFavorite(question: currentQuestion, category: category)
                                print("🔥 QuestionListView: Toggle favori pour: \(currentQuestion.text.prefix(50))...")
                            }
                        }
                    }) {
                        let currentQuestion = currentQuestionIndex < accessibleQuestions.count ? accessibleQuestions[currentQuestionIndex] : nil
                        let isCurrentlyFavorite = currentQuestion != nil ? favoritesService.isFavorite(questionId: currentQuestion!.id) : false
                        
                        HStack(spacing: 12) {
                            Text(isCurrentlyFavorite ? "Retirer des favoris" : "Ajouter en favoris")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundColor(.white)
                            
                            Image(systemName: isCurrentlyFavorite ? "heart.fill" : "heart")
                                .font(.system(size: 20, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            // Même couleur que le header des cartes
                            Color(red: 1.0, green: 0.4, blue: 0.6)
                        )
                        .cornerRadius(28)
                        .scaleEffect(isCurrentlyFavorite ? 1.02 : 1.0)
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 50)
                } else {
                    // Espace pour maintenir la mise en page quand la carte de fin est affichée
                    Spacer()
                        .frame(height: 106) // 56 + 50 de padding
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            loadQuestions()
        }
        .sheet(isPresented: $showNewPackReveal) {
            NewPackRevealView(packNumber: completedPackNumber + 1) {
                // Action quand l'utilisateur clique "C'est parti !"
                showNewPackReveal = false
                unlockNextPack()
                // Masquer la carte de fin et aller aux nouvelles questions
                showPackCompletionCard = false
            }
        }
    }
    
    // NOUVEAU: Gérer le tap sur la carte paywall
    private func handlePaywallTap() {
        print("🔥🔥🔥 FREEMIUM PAYWALL: Tap sur carte paywall")
        appState.freemiumManager?.handleQuestionAccess(at: currentIndex, in: category) {
            // Cette closure ne devrait jamais être appelée ici car l'accès est bloqué
            print("🔥🔥🔥 FREEMIUM PAYWALL: Accès autorisé inattendu")
        }
    }
    
    // MARK: - Helper Methods
    
        private func checkForPackCompletionCard() {
        print("🔍 DEBUG checkForPackCompletionCard: currentIndex=\(currentIndex), accessibleQuestions.count=\(accessibleQuestions.count)")
        print("🔍 DEBUG checkForPackCompletionCard: cachedQuestions.count=\(cachedQuestions.count), showPackCompletionCard=\(showPackCompletionCard)")
        
        // NOUVELLE LOGIQUE: Vérifier d'abord si on a atteint la limite freemium
        if let freemiumManager = appState.freemiumManager {
            let maxFreeQuestions = freemiumManager.getMaxFreeQuestions(for: category)
            let isSubscribed = appState.currentUser?.isSubscribed ?? false
            
            // Si on a atteint la limite freemium ET qu'on n'est pas abonné, ne pas afficher la carte de déblocage
            if accessibleQuestions.count >= maxFreeQuestions && !category.isPremium && !isSubscribed {
                print("🔥🔥🔥 FREEMIUM: Limite atteinte (\(maxFreeQuestions) questions), pas de carte de déblocage pour utilisateur non-payant")
                return
            }
        }
        
        // LOGIQUE AMÉLIORÉE: Afficher la carte de déblocage à l'avant-dernière question du pack
        // pour que l'utilisateur la voie et comprenne qu'il peut swiper
        if currentIndex == accessibleQuestions.count - 1 && 
           accessibleQuestions.count < cachedQuestions.count &&
           !showPackCompletionCard {
            
            // Calculer quel pack vient d'être terminé
            let questionsPerPack = 32
            completedPackNumber = (accessibleQuestions.count - 1) / questionsPerPack + 1
            showPackCompletionCard = true
            
            print("🎉 Pack \(completedPackNumber) bientôt terminé pour \(category.title)! Affichage de la carte de déblocage.")
            print("🔍 DEBUG: currentIndex=\(currentIndex), accessibleQuestions.count=\(accessibleQuestions.count), cachedQuestions.count=\(cachedQuestions.count)")
            return
        }
        
        // Logique de fallback si on atteint vraiment la fin
        if currentIndex >= accessibleQuestions.count && 
           accessibleQuestions.count < cachedQuestions.count &&
           !showPackCompletionCard {
            
            let questionsPerPack = 32
            completedPackNumber = (accessibleQuestions.count - 1) / questionsPerPack + 1
            showPackCompletionCard = true
            
            print("🎉 Pack \(completedPackNumber) terminé pour \(category.title)! Affichage de la carte de fin (fallback).")
            print("🔍 DEBUG: currentIndex=\(currentIndex), accessibleQuestions.count=\(accessibleQuestions.count), cachedQuestions.count=\(cachedQuestions.count)")
        }
    }
     
     private func unlockNextPack() {
         packProgressService.unlockNextPack(for: category.title)
         
         // Recharger les questions accessibles
         accessibleQuestions = packProgressService.getAccessibleQuestions(
             from: cachedQuestions, 
             categoryTitle: category.title
         )
         
         print("🔓 Nouveau pack débloqué ! \(accessibleQuestions.count) questions maintenant disponibles")
     }

}

struct PackCompletionCardView: View {
    let category: QuestionCategory
    let packNumber: Int
    let onTap: () -> Void
    
    @State private var flameAnimation = false
    
    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 30) {
                Spacer()
                
                VStack(spacing: 20) {
                    Text("Félicitation !")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("Tu as terminé le pack.")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    // Flamme avec animation améliorée
                    Text("🔥")
                        .font(.system(size: 60))
                        .scaleEffect(flameAnimation ? 1.3 : 0.9)
                        .rotationEffect(.degrees(flameAnimation ? 15 : -15))
                        .offset(y: flameAnimation ? -5 : 5)
                        .shadow(color: .orange, radius: flameAnimation ? 10 : 5)
                        .onAppear {
                            withAnimation(
                                Animation.easeInOut(duration: 0.6)
                                    .repeatForever(autoreverses: true)
                            ) {
                                flameAnimation = true
                            }
                        }
                    
                    Text("Tape sur moi pour débloquer une surprise")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                }
                
                Spacer()
                
                // Logo/Branding en bas
                HStack(spacing: 8) {
                    Image("leetchi2")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 24, height: 24)
                    
                    Text("Love2Love")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
                }
                .padding(.bottom, 30)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.2, green: 0.1, blue: 0.15),
                        Color(red: 0.4, green: 0.2, blue: 0.3),
                        Color(red: 0.6, green: 0.3, blue: 0.2)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .frame(maxWidth: .infinity)
            .frame(height: 500)
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.white, lineWidth: 3)
                    .shadow(color: .white.opacity(0.5), radius: 8, x: 0, y: 0)
            )
            .shadow(color: .black.opacity(0.3), radius: 10, x: 0, y: 5)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct QuestionCardView: View {
    let question: Question
    let category: QuestionCategory
    let isBackground: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            // Header de la carte avec nom de catégorie
            VStack(spacing: 8) {
                Text(category.title)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 1.0, green: 0.4, blue: 0.6),
                        Color(red: 1.0, green: 0.6, blue: 0.8)
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            
            // Corps de la carte avec la question
            VStack(spacing: 30) {
                Spacer()
                
                Text(question.text)
                    .font(.system(size: 22, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 30)
                
                Spacer()
                
                // Logo/Branding en bas
                HStack(spacing: 8) {
                    Image("leetchi2")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 24, height: 24)
                    
                    Text("Love2Love")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
                }
                .padding(.bottom, 30)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.2, green: 0.1, blue: 0.15),
                        Color(red: 0.4, green: 0.2, blue: 0.3),
                        Color(red: 0.6, green: 0.3, blue: 0.2)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
        .frame(maxWidth: .infinity)
        .frame(height: 500)
        .cornerRadius(20)
        .shadow(color: .black.opacity(isBackground ? 0.1 : 0.3), radius: isBackground ? 5 : 10, x: 0, y: isBackground ? 2 : 5)
    }
} 