import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

val agoraProperties = Properties().apply {
    val config = rootProject.file("agora.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}

// Address of the Kinnect PHP API (see api.properties.example).
val apiProperties = Properties().apply {
    val config = rootProject.file("api.properties")
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
        buildConfigField("String", "API_BASE_URL", (apiProperties.getProperty("baseUrl") ?: "http://10.0.2.2:8088/").asBuildConfigString())
        buildConfigField("String", "AGORA_APP_ID", (agoraProperties.getProperty("appId") ?: "").asBuildConfigString())
        // Only needed when the Agora project has an App Certificate (Secured mode); calls then use a token made on the phone.
        buildConfigField("String", "AGORA_APP_CERTIFICATE", (agoraProperties.getProperty("appCertificate") ?: "").asBuildConfigString())
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
    // Assignment 3: Firebase is used ONLY for push notifications (FCM). Auth and data are the PHP/MySQL web services.
    implementation(libs.firebase.messaging)
    implementation(libs.agora.rtc)
    // Glide: shows pictures (decoded from the database or local files) in ImageViews and avatars.
    implementation(libs.glide)
    // ExifInterface: reads photo rotation so uploads are not sideways.
    implementation(libs.androidx.exifinterface)
    // CameraX: the live camera preview, photo capture and video recording inside Kinnect's Camera screen.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
