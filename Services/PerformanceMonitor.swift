import Foundation
import SwiftUI
import os.log

class PerformanceMonitor: ObservableObject {
    static let shared = PerformanceMonitor()
    
    @Published var isMonitoring = false
    @Published var memoryUsage: Double = 0
    @Published var cpuUsage: Double = 0
    
    private var timer: Timer?
    private let logger = Logger(subsystem: "com.lyes.love2love", category: "Performance")
    
    private init() {}
    
    func startMonitoring() {
        guard !isMonitoring else { return }
        
        isMonitoring = true
        logger.info("Performance monitoring started")
        
        timer = Timer.scheduledTimer(withTimeInterval: 5.0, repeats: true) { [weak self] _ in
            self?.updateMetrics()
        }
    }
    
    func stopMonitoring() {
        timer?.invalidate()
        timer = nil
        isMonitoring = false
        logger.info("Performance monitoring stopped")
    }
    
    private func updateMetrics() {
        // Mesure de l'utilisation mémoire
        var memoryInfo = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size)/4
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &memoryInfo) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_,
                         task_flavor_t(MACH_TASK_BASIC_INFO),
                         $0,
                         &count)
            }
        }
        
        if kerr == KERN_SUCCESS {
            let memoryMB = Double(memoryInfo.resident_size) / 1024.0 / 1024.0
            DispatchQueue.main.async {
                self.memoryUsage = memoryMB
            }
            
            // Alert si utilisation mémoire excessive
            if memoryMB > 200 {
                logger.warning("High memory usage detected: \(memoryMB) MB")
                optimizeMemoryUsage()
            }
        }
    }
    
    private func optimizeMemoryUsage() {
        // Déclencher le nettoyage automatique
        autoreleasepool {
            // Forcer le garbage collection
            DispatchQueue.global(qos: .utility).async {
                // Optimiser les caches
                URLCache.shared.removeAllCachedResponses()
                
                // Notifier les managers de nettoyer
                NotificationCenter.default.post(
                    name: NSNotification.Name("MemoryPressure"),
                    object: nil
                )
            }
        }
    }
    
    // Mesure du temps d'exécution d'une fonction
    func measure<T>(_ name: String, operation: () throws -> T) rethrows -> T {
        let startTime = CFAbsoluteTimeGetCurrent()
        let result = try operation()
        let timeElapsed = CFAbsoluteTimeGetCurrent() - startTime
        
        if timeElapsed > 0.1 { // Plus de 100ms
            logger.warning("Slow operation detected: \(name) took \(timeElapsed * 1000) ms")
        }
        
        return result
    }
    
    // Mesure asynchrone
    func measureAsync<T>(_ name: String, operation: () async throws -> T) async rethrows -> T {
        let startTime = CFAbsoluteTimeGetCurrent()
        let result = try await operation()
        let timeElapsed = CFAbsoluteTimeGetCurrent() - startTime
        
        if timeElapsed > 0.1 {
            logger.warning("Slow async operation: \(name) took \(timeElapsed * 1000) ms")
        }
        
        return result
    }
}

// Extensions pour faciliter l'utilisation
extension View {
    func monitorPerformance(_ name: String) -> some View {
        self.onAppear {
            PerformanceMonitor.shared.measure(name + " onAppear") {
                // Rien - juste mesurer le temps d'apparition
            }
        }
    }
}

// Wrapper pour les opérations coûteuses
@propertyWrapper
struct Measured<T> {
    private var value: T
    private let name: String
    
    init(wrappedValue: T, _ name: String) {
        self.value = wrappedValue
        self.name = name
    }
    
    var wrappedValue: T {
        get {
            return PerformanceMonitor.shared.measure(name) {
                return value
            }
        }
        set {
            value = newValue
        }
    }
} 