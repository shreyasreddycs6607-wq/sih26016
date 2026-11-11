# FocusForce release shrinker rules.
# Hilt, Compose, Navigation and kotlinx.serialization ship their own consumer rules.
# Keep the accessibility service entry point (referenced from the manifest by name).
-keep class com.focusforce.app.service.FocusAccessibilityService { *; }
# Keep line numbers for readable crash reports, hide the original file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
