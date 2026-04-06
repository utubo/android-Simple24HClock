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

##########################
# Add by Simple24HClock

# The commons-suncalc library uses FindBugs annotations for static analysis.
# These annotations are not needed at runtime, so we can safely ignore the missing class warnings.
-dontwarn edu.umd.cs.findbugs.annotations.**

# Ensure the sunrise/sunset calculation library is not removed or obfuscated by R8.
-keep class org.shredzone.commons.suncalc.** { *; }

# Keep the JSON classes used for timezone coordinate lookups.
-keep class org.json.** { *; }

# AboutLibraries
-keep class com.mikepenz.aboutlibraries.** { *; }
