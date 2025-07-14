import SwiftUI

struct HomeScreenWidgetTutorialView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var currentStep = 0
    
    private let steps = [
        TutorialStep(
            title: "hold_home_screen".localized,
            description: "hold_description".localized,
            imageName: "etape5"
        ),
        TutorialStep(
            title: "tap_plus_button".localized,
            description: "plus_description".localized,
            imageName: "etape6"
        ),
        TutorialStep(
            title: "search_love2love_home".localized,
            description: "search_home_description".localized,
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
                        Text(ui: "home_screen_widget", comment: "Home screen widget title")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
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
                                Text("previous".localized)
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
                            Text(currentStep < steps.count - 1 ? "continue".localized : "done".localized)
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