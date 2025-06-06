import SwiftUI

struct BirthDateStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @State private var showDatePicker = false
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MM-yyyy"
        return formatter
    }
    
    var body: some View {
        VStack(spacing: 40) {
            // Titre
            VStack(spacing: 10) {
                Text("COUCOU CHKAF,")
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
                Button(action: {
                    showDatePicker.toggle()
                }) {
                    Text(dateFormatter.string(from: viewModel.birthDate))
                        .font(.system(size: 18))
                        .foregroundColor(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color.white.opacity(0.2))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.white.opacity(0.3), lineWidth: 1)
                                )
                        )
                        .overlay(
                            HStack {
                                Spacer()
                                Image(systemName: "calendar")
                                    .foregroundColor(.white)
                                    .padding(.trailing, 20)
                            }
                        )
                }
                
                if showDatePicker {
                    DatePicker("", selection: $viewModel.birthDate, displayedComponents: .date)
                        .datePickerStyle(WheelDatePickerStyle())
                        .labelsHidden()
                        .colorScheme(.dark)
                        .background(Color.white.opacity(0.1))
                        .cornerRadius(12)
                    
                    Button("OK") {
                        showDatePicker = false
                    }
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 100, height: 44)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: [
                                Color.orange,
                                Color.red
                            ]),
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(22)
                }
            }
            .padding(.horizontal, 30)
            
            // Message de confidentialité
            VStack(spacing: 15) {
                Text("🔥")
                    .font(.system(size: 40))
                
                Text("Tes réponses sont pour toi seul,\net elles resteront complètement confidentielles\npour personnaliser ton expérience.")
                    .font(.system(size: 14))
                    .foregroundColor(.white.opacity(0.8))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 40)
            }
            
            // Bouton Continuer
            if !showDatePicker {
                Button(action: {
                    viewModel.nextStep()
                }) {
                    Text("Continuer")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color.orange,
                                    Color.red
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(28)
                }
                .padding(.horizontal, 30)
            }
        }
    }
} 