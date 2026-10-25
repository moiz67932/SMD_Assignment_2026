import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val agoraProperties = Properties().apply {
    val config = rootProject.file("agora.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}

// Cloudinary cloud name + unsigned upload preset (see media.properties.example).
val mediaProperties = Properties().apply {
    val config = rootProject.file("media.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}

fun String.asBuildConfigString() = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.shadowdevelopers.i230529_i230723"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.shadowdevelopers.i230529_i230723"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "AGORA_APP_ID", (agoraProperties.getProperty("appId") ?: "").asBuildConfigString())
        buildConfigField("String", "MEDIA_CLOUD_NAME", (mediaProperties.getProperty("cloudName") ?: "").asBuildConfigString())
        buildConfigField("String", "MEDIA_UPLOAD_PRESET", (mediaProperties.getProperty("uploadPreset") ?: "").asBuildConfigString())
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)
    implementation(libs.agora.rtc)
    // Glide: loads Cloudinary image URLs into ImageViews and avatars.
    implementation(libs.glide)
    // ExifInterface: reads photo rotation so uploads are not sideways.
    implementation(libs.androidx.exifinterface)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
