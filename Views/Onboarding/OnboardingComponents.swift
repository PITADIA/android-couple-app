import SwiftUI

// MARK: - Onboarding Progress Bar
struct OnboardingProgressBar: View {
    let currentStep: Int
    let totalSteps: Int
    
    var progress: Double {
        Double(currentStep) / Double(totalSteps)
    }
    
    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Text(String(format: "step_counter".localized, currentStep, totalSteps))
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.white.opacity(0.8))
                
                Spacer()
            }
            
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    // Background
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.white.opacity(0.2))
                        .frame(height: 8)
                    
                    // Progress
                    RoundedRectangle(cornerRadius: 4)
                        .fill(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: "#FD267A"),
                                    Color(hex: "#FF6B9D")
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: geometry.size.width * progress, height: 8)
                        .animation(.easeInOut(duration: 0.3), value: progress)
                }
            }
            .frame(height: 8)
        }
    }
}

// MARK: - Onboarding Back Button
struct OnboardingBackButton: View {
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                
                                    Text("back".localized)
                    .font(.system(size: 16, weight: .medium))
            }
            .foregroundColor(.white.opacity(0.8))
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 25)
                    .fill(Color.white.opacity(0.1))
            )
        }
    }
}

// MARK: - Onboarding Next Button
struct OnboardingNextButton: View {
    let isEnabled: Bool
    let action: () -> Void
    let title: String
    
    init(isEnabled: Bool = true, title: String = "continue".localized, action: @escaping () -> Void) {
        self.isEnabled = isEnabled
        self.title = title
        self.action = action
    }
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 16, weight: .semibold))
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 16, weight: .semibold))
            }
            .foregroundColor(isEnabled ? .white : .white.opacity(0.5))
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 25)
                    .fill(isEnabled ? Color(hex: "#FD267A") : Color.white.opacity(0.2))
            )
        }
        .disabled(!isEnabled)
    }
}



// MARK: - Preview
#Preview {
    VStack(spacing: 30) {
        OnboardingProgressBar(currentStep: 3, totalSteps: 8)
            .padding()
        
        HStack {
            OnboardingBackButton {
                print("Back pressed")
            }
            
            Spacer()
            
            OnboardingNextButton(isEnabled: true) {
                print("Next pressed")
            }
        }
        .padding()
    }
    .background(
        LinearGradient(
            gradient: Gradient(colors: [
                Color(red: 0.1, green: 0.02, blue: 0.05),
                Color(red: 0.15, green: 0.05, blue: 0.1)
            ]),
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
        .ignoresSafeArea()
    )
} 