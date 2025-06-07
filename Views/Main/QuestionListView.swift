import SwiftUI

struct QuestionListView: View {
    let category: QuestionCategory
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var questionCacheManager: QuestionCacheManager
    @EnvironmentObject private var favoritesService: FavoritesService
    @StateObject private var packProgressService = PackProgressService.shared
    
    @State private var currentIndex = 0
    @State private var cachedQuestions: [Question] = []
    @State private var accessibleQuestions: [Question] = []
    @State private var isQuestionsLoaded = false
    @State private var dragOffset = CGSize.zero
    
    // États pour les écrans de pack
    @State private var showPackCompletion = false
    @State private var showNewPackReveal = false
    @State private var completedPackNumber = 1
    
    private var currentQuestionIndex: Int {
        return currentIndex
    }
    
    // OPTIMISATION: Ne retourner que les questions visibles (3 maximum)
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
            case "En famille":
                questionKey = "EN FAMILLE"
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
        
        // Filtrer les questions accessibles selon la progression
        accessibleQuestions = packProgressService.getAccessibleQuestions(
            from: questions, 
            categoryTitle: category.title
        )
        
        isQuestionsLoaded = true
        
        let unlockedPacks = packProgressService.getUnlockedPacks(for: category.title)
        print("QuestionListView: \(questions.count) questions totales, \(accessibleQuestions.count) accessibles (Pack \(unlockedPacks))")
    }
    
    var body: some View {
        ZStack {
            // Fond dégradé sombre identique à l'app
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(red: 0.15, green: 0.05, blue: 0.2),
                    Color(red: 0.25, green: 0.1, blue: 0.3)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
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
                    
                    // Compteur de questions avec pack
                    VStack(spacing: 4) {
                        Text("\(currentQuestionIndex + 1) sur \(accessibleQuestions.count)")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                        
                        let currentPack = packProgressService.getCurrentPack(for: currentIndex)
                        let unlockedPacks = packProgressService.getUnlockedPacks(for: category.title)
                        Text("Pack \(currentPack)/\(unlockedPacks)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        // Action refresh/restart
                        currentIndex = 0
                        dragOffset = .zero
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
                        let cardSpacing: CGFloat = 20
                        
                        ZStack {
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
                                .scaleEffect(index == currentQuestionIndex ? 1.0 : 0.9)
                                .opacity(index == currentQuestionIndex ? 1.0 : 0.7)
                                .animation(.spring(response: 0.6, dampingFraction: 0.8), value: currentQuestionIndex)
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
                                            }
                                        } else if value.translation.width < -threshold || velocity < -500 {
                                            // Swipe vers la gauche - question suivante
                                            if currentQuestionIndex < accessibleQuestions.count - 1 {
                                                currentIndex += 1
                                                
                                                // Vérifier si on a terminé un pack
                                                checkForPackCompletion()
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
                
                // Boutons du bas
                HStack(spacing: 20) {
                    // Bouton Partager la carte
                    Button(action: {
                        // Action partager
                    }) {
                        Text("Partager la carte")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        Color(red: 1.0, green: 0.4, blue: 0.2),
                                        Color(red: 1.0, green: 0.6, blue: 0.0)
                                    ]),
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(28)
                    }
                    
                    // Bouton Favoris
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
                        
                        Image(systemName: isCurrentlyFavorite ? "heart.fill" : "heart")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                            .frame(width: 56, height: 56)
                            .background(
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        isCurrentlyFavorite ? Color.red : Color(red: 0.6, green: 0.2, blue: 0.4),
                                        isCurrentlyFavorite ? Color.pink : Color(red: 0.8, green: 0.3, blue: 0.5)
                                    ]),
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(28)
                            .scaleEffect(isCurrentlyFavorite ? 1.1 : 1.0)
                            .animation(.easeInOut(duration: 0.2), value: isCurrentlyFavorite)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 50)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            // SUPPRESSION DES LOGS EXCESSIFS
            print("QuestionListView: Affichage catégorie '\(category.title)'")
            loadQuestions()
        }
        .sheet(isPresented: $showPackCompletion) {
            PackCompletionView(packNumber: completedPackNumber) {
                showPackCompletion = false
                showNewPackReveal = true
            }
        }
        .sheet(isPresented: $showNewPackReveal) {
            NewPackRevealView(packNumber: completedPackNumber + 1) {
                showNewPackReveal = false
                unlockNextPack()
            }
        }
    }
    
    // MARK: - Helper Methods
    
    private func checkForPackCompletion() {
        if packProgressService.checkPackCompletion(categoryTitle: category.title, currentIndex: currentIndex) {
            completedPackNumber = packProgressService.getCurrentPack(for: currentIndex)
            showPackCompletion = true
            print("🎉 Pack \(completedPackNumber) terminé pour \(category.title)!")
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
                
                Text("Love2Love")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white.opacity(0.8))
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
                    .font(.system(size: isBackground ? 18 : 22, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 30)
                
                Spacer()
                
                // Logo/Branding en bas
                HStack(spacing: 8) {
                    Image("LogoMain")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: isBackground ? 20 : 24, height: isBackground ? 20 : 24)
                    
                    Text("Cray Cray")
                        .font(.system(size: isBackground ? 14 : 16, weight: .semibold))
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