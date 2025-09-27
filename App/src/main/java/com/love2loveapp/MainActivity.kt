package com.love2loveapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.love2loveapp.views.ContentView

/**
 * MainActivity principale de Love2Love
 * Point d'entrée de l'application Android
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d(TAG, "🚀 DEBUT onCreate MainActivity")
            Log.d(TAG, "📱 Process: ${android.os.Process.myPid()}")
            Log.d(TAG, "🔌 Thread: ${Thread.currentThread().name}")
            Log.d(TAG, "📦 Package: ${packageName}")
            Log.d(TAG, "🆔 Task ID: ${taskId}")
            
            // 🛡️ Protection contre les crashes Compose hover (bug connu)
            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                when {
                    exception.message?.contains("ACTION_HOVER_EXIT event was not cleared") == true -> {
                        Log.w(TAG, "🛡️ Crash Compose hover intercepté et ignoré")
                        // Ne pas crash l'app pour ce bug connu de Compose
                        return@setDefaultUncaughtExceptionHandler
                    }
                    exception is IllegalStateException && 
                    exception.stackTrace.any { it.className.contains("AndroidComposeView") } -> {
                        Log.w(TAG, "🛡️ Crash AndroidComposeView intercepté: ${exception.message}")
                        return@setDefaultUncaughtExceptionHandler
                    }
                    else -> {
                        // Pour les autres erreurs, comportement normal
                        Log.e(TAG, "💥 Exception non interceptée", exception)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            }
            
            // Vérifier que AppDelegate est prêt
            val app = application as? AppDelegate
            if (app != null) {
                Log.d(TAG, "✅ AppDelegate trouvé")
                Log.d(TAG, "🔍 AppDelegate ready: ${app.isAppReady()}")
            } else {
                Log.e(TAG, "❌ AppDelegate pas trouvé ou mauvais type!")
            }
            
            Log.d(TAG, "📞 Appel super.onCreate...")
            super.onCreate(savedInstanceState)
            Log.d(TAG, "✅ super.onCreate terminé")
            
            Log.d(TAG, "🎨 Configuration du contenu Compose...")
            setContent {
                Log.d(TAG, "🖼️ Dans setContent block")
                MaterialTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Log.d(TAG, "📱 Chargement ContentView...")
                        ContentView()
                    }
                }
            }
            Log.d(TAG, "✅ setContent configuré")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onCreate MainActivity", e)
            Log.e(TAG, "❌ Exception: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Message: ${e.message}")
            Log.e(TAG, "❌ Cause: ${e.cause}")
            e.printStackTrace()
            throw e // Re-throw pour que le système puisse gérer
        } finally {
            Log.d(TAG, "🏁 FIN onCreate MainActivity")
        }
    }
    
    override fun onStart() {
        try {
            Log.d(TAG, "🎬 DEBUT onStart")
            super.onStart()
            Log.d(TAG, "✅ FIN onStart")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onStart", e)
            throw e
        }
    }
    
    override fun onResume() {
        try {
            Log.d(TAG, "▶️ DEBUT onResume")
            super.onResume()
            Log.d(TAG, "✅ FIN onResume")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onResume", e)
            throw e
        }
    }
    
    override fun onPause() {
        try {
            Log.d(TAG, "⏸️ DEBUT onPause")
            super.onPause()
            Log.d(TAG, "✅ FIN onPause")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onPause", e)
            throw e
        }
    }
    
    override fun onStop() {
        try {
            Log.d(TAG, "⏹️ DEBUT onStop")
            super.onStop()
            Log.d(TAG, "✅ FIN onStop")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onStop", e)
            throw e
        }
    }
    
    override fun onDestroy() {
        try {
            Log.d(TAG, "💀 DEBUT onDestroy")
            super.onDestroy()
            Log.d(TAG, "✅ FIN onDestroy")
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRASH DANS onDestroy", e)
            throw e
        }
    }
}
