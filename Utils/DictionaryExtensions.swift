import Foundation

// MARK: - Extensions pour faciliter les transformations de dictionnaires

extension Dictionary where Key == String {
    
    /// Transformer les clés d'un dictionnaire
    func mapKeys<NewKey: Hashable>(_ transform: (Key) -> NewKey) -> [NewKey: Value] {
        var result: [NewKey: Value] = [:]
        for (key, value) in self {
            let newKey = transform(key)
            result[newKey] = value
        }
        return result
    }
    
    /// Transformer les clés en gardant le même type String
    func mapKeys(_ transform: (String) -> String) -> [String: Value] {
        var result: [String: Value] = [:]
        for (key, value) in self {
            let newKey = transform(key)
            result[newKey] = value
        }
        return result
    }
}