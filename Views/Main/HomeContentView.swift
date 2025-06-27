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
                            ForEach(QuestionCategory.categories) { category in
                                CategoryListCardView(category: category) {
                                    print("🔥🔥🔥 HOMECONTENTVIEW CALLBACK: Catégorie sélectionnée: \(category.title)")
                                    activeSheet = .questions(category)
                                    print("🔥🔥🔥 HOMECONTENTVIEW CALLBACK: activeSheet = .questions(\(category.title))")
                                }
                                .environmentObject(appState)
                            }
                        }
                        .padding(.horizontal, 20)
                        
                        // Section widgets défilants (en bas des catégories)
                        WidgetPreviewSection(
                            onWidgetTap: {
                                activeSheet = .widgetTutorial
                            }
                        )
                        .environmentObject(appState)
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
} 