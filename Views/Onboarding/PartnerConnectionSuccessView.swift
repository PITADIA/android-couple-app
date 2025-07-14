import SwiftUI

struct PartnerConnectionSuccessView: View {
    let partnerName: String
    let onContinue: () -> Void
    @State private var showAnimation = false
    
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
                    
                    Text(String(format: "congratulations_connection".localized, partnerName))
                        .font(.system(size: 16))
                        .foregroundColor(.black.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)
                        .opacity(showAnimation ? 1.0 : 0.0)
                        .animation(.easeInOut(duration: 1.0).delay(1.0), value: showAnimation)
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
                }
                .padding(.horizontal, 20)
                .opacity(showAnimation ? 1.0 : 0.0)
                .animation(.easeInOut(duration: 1.0).delay(1.5), value: showAnimation)
                
                Spacer()
                
                // Bouton Continuer - Style identique au tutoriel
                Button(action: {
                    print("🎉 PartnerConnectionSuccessView: Bouton Continuer pressé")
                    onContinue()
                }) {
                    Text("continue".localized)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(Color(hex: "#FD267A"))
                        .clipShape(RoundedRectangle(cornerRadius: 28))
                }
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
} 