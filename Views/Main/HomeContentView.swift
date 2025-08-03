import SwiftUI

struct HomeContentView: View {
    @EnvironmentObject var appState: AppState
    @Binding var activeSheet: SheetType?
    
    // Vérifier si un partenaire est connecté
    private var hasConnectedPartner: Bool {
        guard let partnerId = appState.currentUser?.partnerId else { return false }
        return !partnerId.isEmpty
    }
    
    var body: some View {
        NavigationView {
            ZStack {
                // Fond gris clair pour toute la vue
                Color(red: 0.97, green: 0.97, blue: 0.98)
                    .ignoresSafeArea(.all)
                
                // ScrollView avec section distance partenaires et grille des catégories
                ScrollView {
                    VStack(spacing: 30) {
                        // Section distance entre partenaires (remplace le logo)
                        PartnerDistanceView(
                            onPartnerAvatarTap: {
                                activeSheet = .partnerManagement
                            },
                            onDistanceTap: { showPartnerMessageOnly in
                                if showPartnerMessageOnly {
                                    activeSheet = .partnerLocationMessage
                                } else {
                                    activeSheet = .locationPermission
                                }
                            }
                        )
                            .environmentObject(appState)
                            .padding(.top, 100) // Ajouter plus d'espace depuis la status bar
                        
                        // Section invitation partenaire (si pas connecté)
                        if !hasConnectedPartner {
                            PartnerInviteView {
                                activeSheet = .partnerManagement
                            }
                            .padding(.top, -15) // Rapprocher de la section distance
                        }
                        
                        // Liste des catégories (style rectangulaire)
                        VStack(spacing: 20) {
                            // Utiliser toutes les catégories - le FreemiumManager gère l'accès
                            ForEach(Array(QuestionCategory.categories.enumerated()), id: \.element.id) { index, category in
                                CategoryListCardView(category: category) {
                                    print("🔥🔥🔥 HOMECONTENTVIEW CALLBACK: Catégorie sélectionnée: \(category.title)")
                                    activeSheet = .questions(category)
                                    print("🔥🔥🔥 HOMECONTENTVIEW CALLBACK: activeSheet = .questions(\(category.title))")
                                }
                                .environmentObject(appState)
                                
                                // Ajouter le sous-titre premium après la première catégorie (Toi et moi / en-couple)
                                if index == 0 {
                                    if let subtitle = getPremiumCategoriesSubtitle() {
                                        Text(subtitle)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .multilineTextAlignment(.center)
                                            .padding(.horizontal, 20)
                                            .padding(.top, 10)
                                            .padding(.bottom, 10)
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 20)
                        
                        // Section Widgets
                        VStack(alignment: .leading, spacing: 16) {
                            // Titre "Widgets" à gauche
                            HStack {
                                Text(NSLocalizedString("widgets", comment: "Widgets title"))
                                    .font(.system(size: 22, weight: .bold))
                                    .foregroundColor(.black)
                                    .padding(.horizontal, 20)
                                
                                Spacer()
                            }
                            
                            // Carte widget (remplace la section widgets défilants)
                            WidgetPreviewSection(onWidgetTap: {
                                print("📱 HomeContentView: Carte widget tappée, ouverture de la page widgets")
                                activeSheet = .widgets
                            })
                        }
                        
                        // Section Statistiques sur le couple
                        CoupleStatisticsView()
                            .environmentObject(appState)
                            .padding(.top, 30)
                    }
                    .padding(.bottom, 100) // Espace pour le menu du bas
                    .background(
                        // Dégradé rose en arrière-plan du contenu
                        VStack {
                            LinearGradient(
                                gradient: Gradient(colors: [
                                    Color(hex: "#FD267A").opacity(0.3),
                                    Color(hex: "#FD267A").opacity(0.1),
                                    Color.white.opacity(0)
                                ]),
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .frame(height: 350) // Augmenter la hauteur pour couvrir plus d'espace
                            .ignoresSafeArea(edges: .top)
                            
                            Spacer()
                        }
                        , alignment: .top
                    )
                }
                .ignoresSafeArea(edges: .top) // Permettre au ScrollView de remonter jusqu'en haut
            }
        }
        .navigationBarHidden(true)
    }
    
    // MARK: - Premium Categories Subtitle Helper
    
    /// Helper pour obtenir le sous-titre des catégories premium
    private func getPremiumCategoriesSubtitle() -> String? {
        // Seulement si l'utilisateur a un partenaire connecté
        guard hasConnectedPartner else {
            return nil
        }
        
        // Vérifier le statut d'abonnement
        if appState.currentUser?.isSubscribed ?? false {
            return NSLocalizedString("premium_categories_subscribed", comment: "Premium categories subtitle for subscribed users")
        } else {
            return NSLocalizedString("premium_categories_not_subscribed", comment: "Premium categories subtitle for non-subscribed users")
        }
    }
} 