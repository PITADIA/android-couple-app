import SwiftUI
import UserNotifications

struct DailyQuestionNotificationStepView: View {
    @ObservedObject var viewModel: OnboardingViewModel
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    @State private var isRequestingPermission = false
    @State private var permissionGranted = false
    @State private var showPermissionDeniedAlert = false
    
    var body: some View {
        ZStack {
            // Fond gris clair identique au reste de l'onboarding
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                Spacer()
                
                VStack(spacing: 40) {
                    // Icône et titre principal
                    VStack(spacing: 20) {
                        // Icône animée
                        ZStack {
                            Circle()
                                .fill(
                                    LinearGradient(
                                        gradient: Gradient(colors: [
                                            Color(hex: "#FD267A").opacity(0.2),
                                            Color(hex: "#FF655B").opacity(0.2)
                                        ]),
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                                .frame(width: 120, height: 120)
                            
                            Image(systemName: "bell.fill")
                                .font(.system(size: 50))
                                .foregroundColor(Color(hex: "#FD267A"))
                        }
                        
                        VStack(spacing: 16) {
                            Text("onboarding_daily_question_title")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.black)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 30)
                            
                            Text("onboarding_daily_question_subtitle")
                                .font(.system(size: 18))
                                .foregroundColor(.black.opacity(0.7))
                                .multilineTextAlignment(.center)
                                .lineSpacing(4)
                                .padding(.horizontal, 30)
                        }
                    }
                    
                    // Aperçu de question
                    OnboardingQuestionPreview()
                        .padding(.horizontal, 30)
                    
                    // Description des bénéfices
                    VStack(spacing: 16) {
                        Text("onboarding_daily_question_benefits")
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .padding(.horizontal, 40)
                    }
                }
                
                Spacer()
                
                // Boutons d'action
                VStack(spacing: 16) {
                    // Bouton principal - Activer les notifications
                    Button(action: {
                        requestNotificationPermission()
                    }) {
                        HStack(spacing: 12) {
                            if isRequestingPermission {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: permissionGranted ? "checkmark" : "bell.fill")
                                    .font(.system(size: 18))
                            }
                            
                            Text(permissionGranted ? "notifications_activated" : "activate_notifications_onboarding")
                                .font(.system(size: 18, weight: .semibold))
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: "#FD267A"),
                                    Color(hex: "#FF655B")
                                ]),
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(28)
                    }
                    .disabled(isRequestingPermission)
                    .padding(.horizontal, 20)
                    
                    // Bouton secondaire - Passer cette étape
                    Button(action: {
                        viewModel.nextStep()
                    }) {
                        Text("skip_notifications")
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black.opacity(0.6))
                    }
                    .padding(.horizontal, 20)
                    
                    // Texte explicatif
                    Text("onboarding_notification_explanation")
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.5))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
                .padding(.bottom, 50)
            }
        }
        .alert("notification_permission_denied_title", isPresented: $showPermissionDeniedAlert) {
            Button("settings_button") {
                openAppSettings()
            }
            Button("continue_anyway") {
                viewModel.nextStep()
            }
            Button("cancel_button", role: .cancel) { }
        } message: {
            Text("notification_permission_denied_onboarding_message")
        }
        .onAppear {
            // Le DailyQuestionService est déjà configuré dans AppState
            
            // Vérifier si les notifications sont déjà autorisées
            checkCurrentPermissionStatus()
        }
    }
    
    private func checkCurrentPermissionStatus() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                if settings.authorizationStatus == .authorized {
                    permissionGranted = true
                }
            }
        }
    }
    
    private func requestNotificationPermission() {
        guard !permissionGranted else {
            // Notifications déjà autorisées, continuer
            viewModel.nextStep()
            return
        }
        
        isRequestingPermission = true
        
        Task {
            do {
                let granted = try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])
            
            await MainActor.run {
                isRequestingPermission = false
                permissionGranted = granted
                
                if granted {
                    print("✅ DailyQuestionNotificationStepView: Permission accordée")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                        viewModel.nextStep()
                    }
                } else {
                    print("❌ DailyQuestionNotificationStepView: Permission refusée")
                    showPermissionDeniedAlert = true
                    }
                }
            } catch {
                await MainActor.run {
                    isRequestingPermission = false
                    showPermissionDeniedAlert = true
                    print("❌ DailyQuestionNotificationStepView: Erreur permission - \(error)")
                }
            }
        }
    }
    
    private func openAppSettings() {
        if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(settingsUrl)
        }
    }
}

struct OnboardingQuestionPreview: View {
    var body: some View {
        VStack(spacing: 0) {
            // Header coloré
            VStack(spacing: 8) {
                Text("daily_question_preview_header")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(hex: "#FD267A"),
                        Color(hex: "#FF655B")
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            
            // Corps avec question d'exemple
            VStack(spacing: 20) {
                Spacer()
                
                Text("daily_question_preview_text")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(4)
                    .padding(.horizontal, 20)
                
                Spacer()
                
                // Indicateur de notification
                HStack(spacing: 8) {
                    Image(systemName: "bell.fill")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.8))
                    
                    Text("notification_daily_21h")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                }
                .padding(.bottom, 16)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 160)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.2, green: 0.1, blue: 0.15),
                        Color(red: 0.4, green: 0.2, blue: 0.3)
                    ]),
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
        }
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.15), radius: 8, x: 0, y: 4)
    }
}

#Preview {
    DailyQuestionNotificationStepView(viewModel: OnboardingViewModel())
} 