// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    // Only puts Kotlin 2.4 on the build classpath; AGP 9 built-in Kotlin then compiles with it.
    // Needed because Firebase BoM 35 libraries ship Kotlin 2.4 metadata.
    alias(libs.plugins.kotlin.android) apply false
}