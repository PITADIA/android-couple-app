package com.love2loveapp.services.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import android.os.Debug
import android.os.SystemClock
import com.love2loveapp.model.AppConstants

/**
 * Moniteur de performance applicatif.
 * - Memory usage (MB)
 * - CPU usage (%) du process
 * - Mesure de durée d’opérations (sync et suspend)
 *
 * Usage:
 *   PerformanceMonitor.start(appContext)
 *   PerformanceMonitor.memoryUsageMb.collect { ... }
 *   val r = PerformanceMonitor.measure("calcul") { doWork() }
 *   val r = PerformanceMonitor.measureAsync("io") { repo.load() }
 */
object PerformanceMonitor {

    private const val TAG = "PerformanceMonitor"
    private const val DEFAULT_INTERVAL_MS = AppConstants.Performance.DEFAULT_MONITOR_INTERVAL_MS
    private const val HIGH_MEMORY_THRESHOLD_MB = AppConstants.Performance.HIGH_MEMORY_THRESHOLD_MB

    // État observable (équivalent @Published)
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring

    private val _memoryUsageMb = MutableStateFlow(0.0)
    val memoryUsageMb: StateFlow<Double> = _memoryUsageMb

    private val _cpuUsagePercent = MutableStateFlow(0.0)
    val cpuUsagePercent: StateFlow<Double> = _cpuUsagePercent

    // Signal interne pour "memory pressure" (à écouter par vos caches/managers)
    private val _memoryPressure = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val memoryPressure: SharedFlow<Unit> = _memoryPressure

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /**
     * Démarre la collecte périodique.
     * @param context Optionnel (meilleures métriques mémoire si fourni)
     */
    fun start(context: Context? = null, intervalMs: Long = DEFAULT_INTERVAL_MS) {
        if (_isMonitoring.value) return
        _isMonitoring.value = true
        Log.i(TAG, "Performance monitoring started")

        job = scope.launch {
            var prevTotalCpu = readTotalCpuTime()
            var prevProcCpu = readAppCpuTime()

            while (isActive) {
                val mem = readMemoryMb(context)
                _memoryUsageMb.value = mem

                val totalCpu = readTotalCpuTime()
                val procCpu = readAppCpuTime()
                _cpuUsagePercent.value = computeCpuPercent(prevTotalCpu, totalCpu, prevProcCpu, procCpu)

                if (mem > HIGH_MEMORY_THRESHOLD_MB) {
                    Log.w(TAG, "High memory usage detected: ${"%.1f".format(mem)} MB")
                    optimizeMemoryUsage(context)
                }

                prevTotalCpu = totalCpu
                prevProcCpu = procCpu
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _isMonitoring.value = false
        Log.i(TAG, "Performance monitoring stopped")
    }

    // -------- Mesures de durée --------

    inline fun <T> measure(name: String, block: () -> T): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        if (elapsedMs > AppConstants.Performance.SLOW_OPERATION_THRESHOLD_MS) {
            Log.w(TAG, "Slow operation detected: $name took ${"%.1f".format(elapsedMs)} ms")
        }
        return result
    }

    suspend inline fun <T> measureAsync(name: String, crossinline block: suspend () -> T): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        if (elapsedMs > AppConstants.Performance.SLOW_OPERATION_THRESHOLD_MS) {
            Log.w(TAG, "Slow async operation: $name took ${"%.1f".format(elapsedMs)} ms")
        }
        return result
    }

    // -------- Impl. mémoire/CPU --------

    private fun readMemoryMb(context: Context?): Double {
        return try {
            // Mesure la plus fiable avec ActivityManager (totalPss en KB)
            if (context != null) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0]
                info.totalPss / 1024.0 // → MB
            } else {
                // Fallback JVM
                val rt = Runtime.getRuntime()
                val used = (rt.totalMemory() - rt.freeMemory()).toDouble()
                used / (1024.0 * 1024.0)
            }
        } catch (_: Throwable) {
            0.0
        }
    }

    private fun readTotalCpuTime(): Long {
        return try {
            RandomAccessFile("/proc/stat", "r").use { raf ->
                val first = raf.readLine() ?: return 0L
                // "cpu  user nice system idle iowait irq softirq steal guest guest_nice"
                val toks = first.split("\\s+".toRegex())
                    .drop(1) // drop "cpu"
                    .mapNotNull { it.toLongOrNull() }
                toks.sum()
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun readAppCpuTime(): Long {
        return try {
            RandomAccessFile("/proc/${Process.myPid()}/stat", "r").use { raf ->
                val parts = raf.readLine().split("\\s+".toRegex())
                val utime = parts[13].toLongOrNull() ?: 0L
                val stime = parts[14].toLongOrNull() ?: 0L
                utime + stime
            }
        } catch (_: Throwable) {
            0L
        }
    }

    private fun computeCpuPercent(prevTotal: Long, total: Long, prevProc: Long, proc: Long): Double {
        val dTotal = max(1L, total - prevTotal)
        val dProc = max(0L, proc - prevProc)
        return dProc * 100.0 / dTotal
    }

    /**
     * Allège la pression mémoire:
     * - Vide le cache app basique
     * - Émet un signal `memoryPressure` pour que tes services (LruCache, images, etc.)
     *   se vident proprement (pattern pub/sub).
     */
    private fun optimizeMemoryUsage(context: Context?) {
        scope.launch {
            try {
                // Vide les fichiers cache "faciles" (sans casser les données utilisateur)
                context?.cacheDir?.listFiles()?.forEach { file ->
                    if (file.isFile) runCatching { file.delete() }
                }
            } catch (_: Throwable) {
                // ignorer
            }

            // Notifie les listeners (ex: tes services écoutent memoryPressure.collect { … })
            _memoryPressure.tryEmit(Unit)

            // Demande au GC de passer (hint only)
            runCatching { Debug.getNativeHeapAllocatedSize() } // “touch” VM stats
            System.gc()
        }
    }
}

/**
 * Délégué de propriété pour mesurer des accès coûteux.
 *
 * Exemple:
 *   var data by Measured(loadOnce(), "data-cache")
 *   val x = data // déclenche une mesure sur le get
 */
class Measured<T>(
    private var value: T,
    private val name: String
) : ReadWriteProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return PerformanceMonitor.measure(name) { value }
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }
}
