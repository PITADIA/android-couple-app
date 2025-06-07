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
    @State private var showNewPackReveal = false
    @State private var completedPackNumber = 1
    @State private var showPackCompletionCard = false
    
    private var currentQuestionIndex: Int {
        return currentIndex
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
        return showPackCompletionCard && currentIndex == accessibleQuestions.count
    }
    
    // Nombre total d'éléments (questions + carte de fin éventuelle)
    private var totalItems: Int {
        return accessibleQuestions.count + (showPackCompletionCard ? 1 : 0)
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
                                .animation(.spring(response: 0.6, dampingFraction: 0.8), value: currentQuestionIndex)
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
                                            if currentQuestionIndex < totalItems - 1 {
                                                currentIndex += 1
                                                
                                                // Vérifier si on a terminé un pack (mais ne pas afficher automatiquement)
                                                checkForPackCompletionCard()
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
                if !shouldShowCompletionCard {
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
                        .animation(.easeInOut(duration: 0.2), value: isCurrentlyFavorite)
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
            // SUPPRESSION DES LOGS EXCESSIFS
            print("QuestionListView: Affichage catégorie '\(category.title)'")
            loadQuestions()
        }
        .onChange(of: currentIndex) { newIndex in
            print("🔍 DEBUG: currentIndex changed to \(newIndex), accessibleQuestions.count=\(accessibleQuestions.count)")
            print("🔍 DEBUG: showPackCompletionCard=\(showPackCompletionCard), shouldShowCompletionCard=\(shouldShowCompletionCard)")
            print("🔍 DEBUG: totalItems=\(totalItems)")
        }
        .sheet(isPresented: $showNewPackReveal) {
            NewPackRevealView(packNumber: completedPackNumber + 1) {
                showNewPackReveal = false
                // Débloquer le nouveau pack
                unlockNextPack()
                // Masquer la carte de fin et aller aux nouvelles questions
                showPackCompletionCard = false
                // Aller à la première question du nouveau pack (pas au début)
                currentIndex = completedPackNumber * 32 // Ex: Pack 1 terminé -> index 32 (première du pack 2)
            }
        }
    }
    
    // MARK: - Helper Methods
    
    private func checkForPackCompletionCard() {
        print("🔍 DEBUG checkForPackCompletionCard: currentIndex=\(currentIndex), accessibleQuestions.count=\(accessibleQuestions.count)")
        
        // TEST TEMPORAIRE: Forcer l'affichage de la carte quand on arrive à l'avant-dernière question
        if currentIndex == accessibleQuestions.count - 2 && !showPackCompletionCard {
            completedPackNumber = 1
            showPackCompletionCard = true
            print("🔍 TEST: Affichage forcé de la carte de fin pour debug!")
            return
        }
        
        // Vérifier si on vient de terminer un pack (on était à la dernière question d'un pack)
        let previousIndex = currentIndex - 1
        if packProgressService.checkPackCompletion(categoryTitle: category.title, currentIndex: previousIndex) {
            completedPackNumber = packProgressService.getCurrentPack(for: previousIndex)
            showPackCompletionCard = true
            print("🎉 Pack \(completedPackNumber) terminé pour \(category.title)! Affichage de la carte de fin.")
            print("🔍 DEBUG: currentIndex=\(currentIndex), previousIndex=\(previousIndex), accessibleQuestions.count=\(accessibleQuestions.count)")
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
                        .animation(
                            Animation.easeInOut(duration: 0.6)
                                .repeatForever(autoreverses: true),
                            value: flameAnimation
                        )
                        .onAppear {
                            flameAnimation = true
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
                    Image("Leetchi")
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
                    Image("Leetchi")
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