plugins {
    id("com.android.application")
    id("com.google.gms.google-services") // Added for Firebase
}

android {
    namespace = "com.example.tts_tool"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.tts_tool"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.gson)

    // Added for DocumentFile operations
    implementation(libs.documentfile)
    implementation (libs.activity.ktx)

    // Firebase SDKs
    // Using the Firebase BOM to manage versions
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth) // Corrected Kotlin DSL syntax
    implementation(libs.firebase.firestore) // Corrected Kotlin DSL syntax
}
