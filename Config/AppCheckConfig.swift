import Foundation

/// Configuration App Check pour éviter d'exposer les tokens dans le code principal
struct AppCheckConfig {
    
    /// Debug Token pour Firebase App Check
    /// À récupérer depuis Firebase Console > App Check > Debug tokens
    static let debugToken: String? = {
        #if DEBUG
        // Option 1: Token depuis les variables d'environnement (RECOMMANDÉ)
        if let envToken = ProcessInfo.processInfo.environment["FIREBASE_DEBUG_TOKEN"] {
            return envToken
        }
        
        // Option 2: Token depuis un fichier local (non versionné)
        if let fileToken = loadTokenFromLocalFile() {
            return fileToken
        }
        
        // Option 3: Aucun fallback hardcodé (SÉCURITÉ)
        return nil
        #else
        return nil
        #endif
    }()
    
    /// Vérifie si le Debug Token est configuré
    static var isDebugTokenConfigured: Bool {
        guard let token = debugToken else { return false }
        return !token.isEmpty
    }
    
    /// Charge le token depuis un fichier local (non versionné)
    private static func loadTokenFromLocalFile() -> String? {
        guard let path = Bundle.main.path(forResource: "firebase-debug-token", ofType: "txt") else {
            return nil
        }
        
        do {
            let content = try String(contentsOfFile: path, encoding: .utf8)
            return content.trimmingCharacters(in: .whitespacesAndNewlines)
        } catch {
            print("⚠️ Erreur lecture fichier debug token: \(error)")
            return nil
        }
    }
}
