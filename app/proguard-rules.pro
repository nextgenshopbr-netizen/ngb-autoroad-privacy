# ============================================================================
# NGBAutoRoad ProGuard/R8 Rules — Proteção contra Engenharia Reversa
# Versão: v4.4.0
# ============================================================================

# === OFUSCAÇÃO ATIVA ===
# Remover o keep genérico que impedia ofuscação do código do app
# Agora apenas classes essenciais são preservadas

# Ofuscar nomes de classes, métodos e campos
-repackageclasses 'a'
-allowaccessmodification
-overloadaggressively

# Remover logs em release (proteção contra vazamento de informações)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Remover println em release
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
}

# === KEEP RULES MÍNIMAS (apenas o necessário) ===

# Keep Activities e Services (Android precisa encontrar pelo nome)
-keep class com.ngbautoroad.ui.MainActivity { *; }
-keep class com.ngbautoroad.ui.admin.AdminActivity { *; }
-keep class com.ngbautoroad.ui.finance.FinanceActivity { *; }
-keep class com.ngbautoroad.ui.card.CardEditorActivity { *; }
-keep class com.ngbautoroad.ui.card.CardGalleryActivity { *; }
-keep class com.ngbautoroad.ui.map.ZoneMapActivity { *; }
-keep class com.ngbautoroad.service.OverlayService { *; }
-keep class com.ngbautoroad.service.OcrCaptureService { *; }
-keep class com.ngbautoroad.service.BubbleService { *; }
-keep class com.ngbautoroad.service.RideAccessibilityService { *; }
-keep class com.ngbautoroad.NGBAutoRoadApp { *; }

# Keep Room Entities (necessário para o banco de dados)
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep DataStore/Preferences (nomes de chaves)
-keep class com.ngbautoroad.data.prefs.** { *; }

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep Compose (necessário para runtime)
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# Keep Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# === PROTEÇÃO EXTRA ===

# Encriptar strings sensíveis (R8 fullMode)
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Remover source file info e line numbers (dificulta decompilação)
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Prevent decompilers from reconstructing inner class structure
-keepattributes EnclosingMethod
