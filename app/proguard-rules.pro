# NGBAutoRoad ProGuard Rules
# Keep all app classes (safe approach for first release)
-keep class com.ngbautoroad.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep OSMDroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep Compose
-dontwarn androidx.compose.**
