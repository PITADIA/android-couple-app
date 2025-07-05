import SwiftUI

struct WidgetTutorialView: View {
    @Environment(\.presentationMode) var presentationMode
    @State private var currentPage = 0
    
    private let tutorialSteps = [
        TutorialStep(
            title: "Restez appuyer sur votre écran d'accueil puis cliquez sur le +",
            description: "",
            imageName: "imageA"
        ),
        TutorialStep(
            title: "Cliquez sur Ajouter un Widget",
            description: "",
            imageName: "imageB"
        ),
        TutorialStep(
            title: "Cherchez Love2Love",
            description: "",
            imageName: "imageC"
        ),
        TutorialStep(
            title: "Sélectionnez le Widget que vous souhaitez puis cliquer sur ajouter",
            description: "",
            imageName: "imageD"
        )
    ]
    
    var body: some View {
        VStack(spacing: 0) {
            // Handle pour indiquer que c'est une sheet
            RoundedRectangle(cornerRadius: 2.5)
                .fill(Color.gray.opacity(0.3))
                .frame(width: 40, height: 5)
                .padding(.top, 12)
                .padding(.bottom, 8)
            
            // Titre dynamique de l'étape courante
            Text(tutorialSteps[currentPage].title)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.black)
                .multilineTextAlignment(.center)
                .padding(.top, 20)
                .padding(.horizontal, 16)
                .padding(.bottom, 20)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
                .animation(.easeInOut(duration: 0.3), value: currentPage)
            
            // Contenu du tutoriel avec pagination
            TabView(selection: $currentPage) {
                ForEach(0..<tutorialSteps.count, id: \.self) { index in
                    VStack(spacing: 16) {
                        // Image du tutoriel avec marges et style amélioré (plus large)
                        Image(tutorialSteps[index].imageName)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 350, height: 220)
                            .clipped()
                            .cornerRadius(20)
                            .shadow(color: .black.opacity(0.15), radius: 12, x: 0, y: 6)
                            .padding(.horizontal, 15)
                    }
                    .background(Color.white)
                    .tag(index)
                }
            }
            .background(Color.white)
            .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
            .frame(height: 250)
            
            // Indicateurs de page personnalisés
            HStack(spacing: 8) {
                ForEach(0..<tutorialSteps.count, id: \.self) { index in
                    Circle()
                        .fill(index == currentPage ? Color(hex: "#FD267A") : Color.gray.opacity(0.3))
                        .frame(width: 8, height: 8)
                        .animation(.easeInOut(duration: 0.3), value: currentPage)
                }
            }
            .padding(.top, 16)
            .padding(.bottom, 24)
            
            // Bouton de fermeture
            Button(action: {
                presentationMode.wrappedValue.dismiss()
            }) {
                Text("Compris !")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
                    .background(Color(hex: "#FD267A"))
                    .cornerRadius(25)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
        .background(Color.white)
        .cornerRadius(20)
    }
}

#Preview {
    WidgetTutorialView()
} 