import SwiftUI

struct CategoriesPreviewStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var visibleCategories: Set<String> = []
    @State private var currentCategoryIndex = 0
    
    private let animationInterval: TimeInterval = 0.4
    
    var body: some View {
        VStack(spacing: 0) {
            // Espace entre barre de progression et titre (harmonisé)
            Spacer()
                .frame(height: 40)
            
            // Titre centré à gauche
            HStack {
                Text("Plus de 2000 questions à poser à votre âme sœur")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                Spacer()
            }
            .padding(.horizontal, 30)
            
            // Espace entre titre et cartes
            Spacer()
                .frame(height: 60)
            
            // Liste des catégories avec animation
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(QuestionCategory.categories) { category in
                        CategoryPreviewCard(category: category)
                            .opacity(visibleCategories.contains(category.id) ? 1 : 0)
                            .scaleEffect(visibleCategories.contains(category.id) ? 1 : 0.8)
                            .animation(
                                .spring(response: 0.6, dampingFraction: 0.8, blendDuration: 0),
                                value: visibleCategories.contains(category.id)
                            )
                    }
                }
                .padding(.horizontal, 20)
            }
            
            // Deuxième Spacer pour pousser la zone bouton vers le bas
            Spacer()
            
            // Zone blanche collée en bas
            VStack(spacing: 0) {
                Button("Continuer") {
                    viewModel.nextStep()
                }
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color(hex: "#FD267A"))
                .cornerRadius(28)
                .padding(.horizontal, 30)
                .padding(.vertical, 30)
            }
            .background(Color.white)
            .shadow(color: .black.opacity(0.1), radius: 10, x: 0, y: -5)
        }
        .onAppear {
            startCategoryAnimation()
        }
    }
    
    private func startCategoryAnimation() {
        // Démarrer l'animation après un petit délai
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            animateNextCategory()
        }
    }
    
    private func animateNextCategory() {
        guard currentCategoryIndex < QuestionCategory.categories.count else { return }
        
        let category = QuestionCategory.categories[currentCategoryIndex]
        visibleCategories.insert(category.id)
        currentCategoryIndex += 1
        
        // Programmer la prochaine animation
        DispatchQueue.main.asyncAfter(deadline: .now() + animationInterval) {
            animateNextCategory()
        }
    }
}

struct CategoryPreviewCard: View {
    let category: QuestionCategory
    
    var body: some View {
        HStack(spacing: 16) {
            // Contenu principal
            VStack(alignment: .leading, spacing: 6) {
                // Titre principal
                Text(category.title)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.black)
                    .multilineTextAlignment(.leading)
                
                // Sous-titre
                Text(category.subtitle)
                    .font(.system(size: 14))
                    .foregroundColor(.gray)
                    .multilineTextAlignment(.leading)
            }
            
            Spacer()
            
            // Emoji à droite
            Text(category.emoji)
                .font(.system(size: 28))
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 20)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white.opacity(0.95))
                .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 2)
        )
    }
}

#Preview {
    CategoriesPreviewStepView(viewModel: OnboardingViewModel())
        .background(Color(red: 0.97, green: 0.97, blue: 0.98))
} 