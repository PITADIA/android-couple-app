import SwiftUI

struct PartnerConnectionSuccessView: View {
    let partnerName: String
    let onContinue: () -> Void
    @State private var showAnimation = false
    @State private var isLoading = false
    @StateObject private var dailyQuestionService = DailyQuestionService.shared
    
    var body: some View {
        ZStack {
            // Fond gris clair identique à l'app avec dégradé rose doux en arrière-plan
            Color(red: 0.97, green: 0.97, blue: 0.98)
                .ignoresSafeArea(.all)
            
            // Dégradé rose très doux en arrière-plan
            LinearGradient(
                gradient: Gradient(colors: [
                    Color(hex: "#FD267A").opacity(0.03),
                    Color(hex: "#FF655B").opacity(0.02)
                ]),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
            
            VStack(spacing: 30) {
                VStack(spacing: 16) {
                    Text("connection_successful".localized)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundColor(.black)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(0.5), value: showAnimation)
                }
                .padding(.top, 60)
                
                Spacer().frame(height: 50)
                
                // Carte avec connexion partenaire - Style sophistiqué identique au tutoriel
                HStack(spacing: 16) {
                    Image(systemName: "heart.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.white)
                        .frame(width: 50, height: 50)
                        .background(Color(hex: "#FD267A"))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    
                    Text(String(format: "connected_with".localized, partnerName))
                        .font(.system(size: 18, weight: .medium))
                        .foregroundColor(.black)
                    
                    Spacer()
                    
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.green)
                }
                .padding(20)
                .background(
                    RoundedRectangle(cornerRadius: 20)
                        .fill(Color.white)
                        .shadow(color: Color.black.opacity(0.08), radius: 8, x: 0, y: 4)
                        .shadow(color: Color.black.opacity(0.04), radius: 1, x: 0, y: 1)
                )
                .padding(.horizontal, 20)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.2), value: showAnimation)
                
                Spacer().frame(height: 40)
                
                // Instructions sur les nouvelles fonctionnalités
                VStack(alignment: .leading, spacing: 12) {
                    Text("new_features_unlocked".localized)
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundColor(.black)
                        .padding(.bottom, 16)
                    
                    HStack(spacing: 12) {
                        Text("💕")
                            .font(.system(size: 24))
                        
                        Text("shared_favorite_questions".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("📍")
                            .font(.system(size: 24))
                        
                        Text("real_time_distance".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("⭐")
                            .font(.system(size: 24))
                        
                        Text("shared_journal".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                    
                    HStack(spacing: 12) {
                        Text("📱")
                            .font(.system(size: 24))
                        
                        Text("widgets_available".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black)
                    }
                    
                    HStack(spacing: 12) {
                        Text("💬")
                            .font(.system(size: 24))
                        
                        Text("daily_questions_available".localized)
                            .font(.system(size: 16))
                            .foregroundColor(.black.opacity(0.8))
                    }
                }
                .padding(.horizontal, 20)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
                
                Spacer()
                
                // Bouton Continuer - Style identique au tutoriel
                Button(action: {
                    Task {
                        await handleContinue()
                    }
                }) {
                    HStack(spacing: 12) {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .scaleEffect(0.9)
                        }
                        
                        Text(isLoading ? "preparation_in_progress".localized : "continue".localized)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundColor(.white)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(Color(hex: "#FD267A").opacity(isLoading ? 0.7 : 1.0))
                    .clipShape(RoundedRectangle(cornerRadius: 28))
                }
                .disabled(isLoading)
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(2.0), value: showAnimation)
            }
        }
        .onAppear {
            print("🎉 PartnerConnectionSuccessView: Vue apparue pour partenaire: \(partnerName)")
            showAnimation = true
        }
    }
    
    /// Gère le clic sur "Continuer" avec attente que le service soit prêt
    private func handleContinue() async {
        print("🎉 PartnerConnectionSuccessView: Bouton Continuer pressé")
        isLoading = true
        
        // Délai minimum pour montrer l'effet de chargement (1.5 secondes)
        let minimumLoadingTime: TimeInterval = 1.5
        let loadingStart = Date()
        
        // Attendre le délai minimum ET que le service soit prêt
        while Date().timeIntervalSince(loadingStart) < minimumLoadingTime {
            try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
        }
        
        // Attendre que le service soit dans un état stable (max 8 secondes supplémentaires)
        let maxAdditionalWait: TimeInterval = 8.0
        let serviceWaitStart = Date()
        
        while Date().timeIntervalSince(serviceWaitStart) < maxAdditionalWait {
            let isServiceReady = !dailyQuestionService.isLoading && 
                                !dailyQuestionService.isOptimizing &&
                                (dailyQuestionService.currentQuestion != nil || dailyQuestionService.allQuestionsExhausted)
            
            if isServiceReady {
                print("🎉 PartnerConnectionSuccessView: Service prêt après \(Date().timeIntervalSince(loadingStart))s")
                break
            }
            
            try? await Task.sleep(nanoseconds: 100_000_000) // 100ms
        }
        
        print("🎉 PartnerConnectionSuccessView: Préparation terminée - Fermeture de la vue")
        
        // Fermer la vue (garder isLoading=true jusqu'à l'appel d'onContinue)
        await MainActor.run {
            onContinue()
            // Note: isLoading reste true jusqu'à ce que la vue se ferme
        }
    }
} 