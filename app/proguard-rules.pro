# Cairn R8 rules. Library consumer rules cover most things; these are explicit safety nets for the
# reflection-based paths we rely on. Keep them narrow so the release stays small.

# kotlinx.serialization: generated serializers are looked up via companion objects / $$serializer.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class app.cairn.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.cairn.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.cairn.**$$serializer { *; }

# WorkManager instantiates workers by class name through reflection (two-argument constructor).
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance: receivers and activities are named in manifests (kept by AAPT); keep any ActionCallback,
# which Glance instantiates by class name when a widget action runs.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }

# Widget tap trampolines resolve app classes by string name (see Intents.kt).
-keep class app.cairn.MainActivity
-keep class app.cairn.OpenCorosActivity

# Room: generated *_Impl classes are loaded by name; entities/DAOs are referenced by generated code.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class app.cairn.**
-keep @androidx.room.Dao interface app.cairn.**

# Health Connect client: permission/record types are matched by KClass and by name.
-keep class androidx.health.connect.client.** { *; }
-dontwarn androidx.health.connect.client.**

# Crash stack traces stay readable with the mapping file.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
