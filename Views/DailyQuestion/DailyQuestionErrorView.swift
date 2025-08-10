import SwiftUI

struct DailyQuestionErrorView: View {
    let message: String
    let onRetry: () -> Void
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair identique à l'app
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea()
                
                VStack(spacing: 30) {
                    Spacer()
                    
                    // Icône d'erreur
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.orange)
                    
                    // Titre
                    Text("Oups, une erreur est survenue")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(.black)
                        .multilineTextAlignment(.center)
                    
                    // Message d'erreur
                    Text(message)
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.7))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 30)
                    
                    Spacer()
                    
                    // Bouton Réessayer
                    Button(action: onRetry) {
                        Text("Réessayer")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 56)
                            .background(Color(hex: "#FD267A"))
                            .clipShape(RoundedRectangle(cornerRadius: 28))
                    }
                    .padding(.horizontal, 30)
                    .padding(.bottom, 40)
                }
            }
        }
        .navigationBarHidden(true)
    }
}

#Preview {
    DailyQuestionErrorView(
        message: "Impossible de charger les questions. Vérifiez votre connexion internet.",
        onRetry: {
            print("Retry tapped")
        }
    )
}