import SwiftUI

struct HomeScreenWidgetTutorialView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var currentStep = 0
    
    private let steps = [
        TutorialStep(
            title: "Appuie et maintiens n'importe où sur ton écran d'accueil",
            description: "Maintiens ton doigt appuyé sur un espace vide de l'écran d'accueil pour entrer en mode édition.",
            imageName: "etape5"
        ),
        TutorialStep(
            title: "Appuie sur le bouton +",
            description: "Touche le bouton + en haut à gauche pour ajouter un nouveau widget.",
            imageName: "etape6"
        ),
        TutorialStep(
            title: "Recherche Love2Love",
            description: "Recherche Love2Love dans la barre de recherche puis choisis le widget que tu souhaite.",
            imageName: "etape7"
        )
    ]
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond complètement blanc
                Color.white
                    .ignoresSafeArea(.all)
                
                VStack(spacing: 0) {
                    // Header sans bouton retour
                    VStack(spacing: 0) {
                        Text("Widget Écran d'Accueil")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                            .padding(.top, 40)
                    }
                    
                    // Indicateur de progression
                    HStack(spacing: 8) {
                        ForEach(0..<steps.count, id: \.self) { index in
                            Circle()
                                .fill(index <= currentStep ? Color(hex: "#FD267A") : Color.gray.opacity(0.3))
                                .frame(width: 8, height: 8)
                                .animation(.easeInOut(duration: 0.3), value: currentStep)
                        }
                    }
                    .padding(.top, 30)
                    
                    // Contenu principal
                    TabView(selection: $currentStep) {
                        ForEach(0..<steps.count, id: \.self) { index in
                            TutorialStepView(step: steps[index])
                                .tag(index)
                        }
                    }
                    .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                    .animation(.easeInOut(duration: 0.3), value: currentStep)
                    
                    // Boutons de navigation
                    HStack(spacing: 16) {
                        if currentStep > 0 {
                            Button(action: {
                                withAnimation {
                                    currentStep -= 1
                                }
                            }) {
                                Text("Précédent")
                                    .font(.system(size: 16, weight: .medium))
                                    .foregroundColor(Color(hex: "#FD267A"))
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 50)
                                    .background(Color.white)
                                    .cornerRadius(25)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 25)
                                            .stroke(Color(hex: "#FD267A"), lineWidth: 2)
                                    )
                            }
                        }
                        
                        Button(action: {
                            if currentStep < steps.count - 1 {
                                withAnimation {
                                    currentStep += 1
                                }
                            } else {
                                dismiss()
                            }
                        }) {
                            Text(currentStep < steps.count - 1 ? "Continuer" : "Terminer")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(hex: "#FD267A"),
                                            Color(hex: "#FF6B9D")
                                        ]),
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .cornerRadius(25)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationBarHidden(true)
    }
}

struct HomeScreenWidgetTutorialView_Previews: PreviewProvider {
    static var previews: some View {
        HomeScreenWidgetTutorialView()
    }
} 