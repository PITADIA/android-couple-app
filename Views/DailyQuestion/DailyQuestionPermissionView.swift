import SwiftUI
import UserNotifications

struct DailyQuestionPermissionView: View {
    @EnvironmentObject var appState: AppState
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    @Environment(\.dismiss) private var dismiss
    
    @State private var isRequestingPermission = false
    @State private var showPermissionDeniedAlert = false
    
    var onPermissionGranted: (() -> Void)?
    var onContinueWithoutPermissions: (() -> Void)?
    
    var body: some View {
        ZStack {
            // Fond gris clair identique au reste de l'app
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header avec croix de fermeture
                HStack {
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 18, weight: .medium))
                            .foregroundColor(.black)
                    }
                    .padding(.leading, 20)
                    
                    Spacer()
                }
                .padding(.top, 10)
                
                Spacer()
                
                VStack(spacing: 40) {
                    // Titre principal
                    VStack(spacing: 16) {
                        Text(NSLocalizedString("daily_question_permission_title", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 28, weight: .bold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                        
                        Text(NSLocalizedString("daily_question_permission_subtitle", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                    }
                    
                    // Carte d'exemple de question
                    ExampleQuestionCard()
                        .padding(.horizontal, 20)
                    
                    // Description des bénéfices
                    VStack(spacing: 16) {
                        Text(NSLocalizedString("daily_question_benefits_title", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.black)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 30)
                        
                        Text(NSLocalizedString("daily_question_benefits_description", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.7))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .padding(.horizontal, 30)
                    }
                }
                
                Spacer()
                
                // Boutons d'action
                VStack(spacing: 16) {
                    // Bouton principal d'activation des notifications
                    Button(action: {
                        requestNotificationPermission()
                    }) {
                        HStack(spacing: 12) {
                            if isRequestingPermission {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                    .scaleEffect(0.8)
                            } else {
                                Image(systemName: "bell.fill")
                                    .font(.system(size: 18))
                            }
                            
                            Text(NSLocalizedString("activate_notifications_button", tableName: "DailyQuestions", comment: ""))
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
                    
                    // 🎯 NOUVEAU : Bouton "Continuer sans notifications" (conforme Apple)
                    Button(action: {
                        print("✅ DailyQuestionPermissionView: Utilisateur continue sans notifications")
                        onContinueWithoutPermissions?()
                        dismiss()
                    }) {
                        Text(NSLocalizedString("continue_without_notifications_button", tableName: "DailyQuestions", comment: ""))
                            .font(.system(size: 16, weight: .medium))
                            .foregroundColor(.black.opacity(0.7))
                            .underline()
                    }
                    .padding(.horizontal, 20)
                    
                    // Texte explicatif petit
                    Text(NSLocalizedString("notification_permission_explanation", tableName: "DailyQuestions", comment: ""))
                        .font(.system(size: 14))
                        .foregroundColor(.black.opacity(0.5))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
                .padding(.bottom, 50)
            }
        }
        .navigationBarHidden(true)
        .alert(NSLocalizedString("notification_permission_denied_title", tableName: "DailyQuestions", comment: ""), isPresented: $showPermissionDeniedAlert) {
            Button(NSLocalizedString("settings_button", tableName: "DailyQuestions", comment: "")) {
                openAppSettings()
            }
            Button(NSLocalizedString("cancel_button", tableName: "DailyQuestions", comment: ""), role: .cancel) { }
        } message: {
            Text(NSLocalizedString("notification_permission_denied_message", tableName: "DailyQuestions", comment: ""))
        }
        .onAppear {
            // Le service est déjà configuré dans AppState
            print("✅ DailyQuestionPermissionView: Vue apparue")
        }
    }
    
    private func requestNotificationPermission() {
        isRequestingPermission = true
        
        Task {
            do {
                let granted = try await UNUserNotificationCenter.current().requestAuthorization(
                    options: [.alert, .sound, .badge]
                )
            
            await MainActor.run {
                isRequestingPermission = false
                
                if granted {
                    print("✅ DailyQuestionPermissionView: Permission accordée")
                    FCMService.shared.requestTokenAndSave()
                    onPermissionGranted?()
                } else {
                    print("❌ DailyQuestionPermissionView: Permission refusée")
                        showPermissionDeniedAlert = true
                    }
                }
            } catch {
                await MainActor.run {
                    isRequestingPermission = false
                    showPermissionDeniedAlert = true
                    print("❌ DailyQuestionPermissionView: Erreur permission - \(error)")
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

struct ExampleQuestionCard: View {
    var body: some View {
        VStack(spacing: 0) {
            // Header de la carte
            VStack(spacing: 8) {
                Text(NSLocalizedString("daily_question_example_header", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
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
            
            // Corps de la carte avec la question d'exemple
            VStack(spacing: 30) {
                Spacer()
                
                Text(NSLocalizedString("daily_question_example_text", tableName: "DailyQuestions", comment: ""))
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .lineSpacing(6)
                    .padding(.horizontal, 30)
                
                Spacer()
                
                // Logo/Branding en bas
                HStack(spacing: 8) {
                    Image("leetchi2")
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
        .frame(height: 300)
        .cornerRadius(20)
        .shadow(color: Color.black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
}

#Preview {
    DailyQuestionPermissionView()
        .environmentObject(AppState())
} 