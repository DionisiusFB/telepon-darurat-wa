plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Nomor build dari GitHub Actions dipakai sebagai nomor versi app.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "id.dionisius.teleponwa"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.dionisius.teleponwa"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
