# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# === RÈGLES JETPACK COMPOSE POUR ÉVITER LES CRASHS ===

# Garder les classes Compose UI
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.runtime.** { *; }

# Éviter l'obfuscation des événements hover qui causent le crash
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.ui.platform.AndroidComposeView$sendHoverExitEvent* { *; }

# Garder les callbacks et listeners
-keepclassmembers class * {
    public *** on*Event(...);
    public *** on*(...);
}

# Protection Google Play Billing
-keep class com.android.billingclient.api.** { *; }
-keep class com.love2loveapp.services.billing.** { *; }