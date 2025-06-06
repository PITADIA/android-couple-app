import SwiftUI

struct BirthDateStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MM-yyyy"
        return formatter
    }
    
    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            
            // Contenu centré
            VStack(spacing: 40) {
                // Titre
                VStack(spacing: 10) {
                    Text("COUCOU \(viewModel.userName.uppercased()),")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                    
                    Text("QUEL ÂGE AS-TU ?")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 30)
                
                // Sélecteur de date
                VStack(spacing: 30) {
                    DatePicker("", selection: $viewModel.birthDate, displayedComponents: .date)
                        .datePickerStyle(WheelDatePickerStyle())
                        .labelsHidden()
                        .colorScheme(.dark)
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(12)
                        .environment(\.locale, Locale(identifier: "fr_FR"))
                }
                .padding(.horizontal, 30)
                
                // Message de confidentialité
                VStack(spacing: 15) {
                    Text("Toutes tes informations resteront confidentielles et seront utilisées uniquement pour personnaliser ton expérience.")
                        .font(.system(size: 14))
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
            }
            
            Spacer()
            
            // Bouton Continuer collé en bas
            Button(action: {
                viewModel.nextStep()
            }) {
                Text("Continuer")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A"))
                    .cornerRadius(28)
            }
            .padding(.horizontal, 30)
            .padding(.bottom, 50)
        }
    }
} 