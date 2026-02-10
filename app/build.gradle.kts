import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.digihappy.sfapi"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.digihappy.sfapi"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val envProps = Properties().apply {
            val envFile = rootProject.file(".env")
            if (envFile.exists()) {
                envFile.inputStream().use { load(it) }
            }
        }

        val apiToken = (
            envProps.getProperty("SCALEFUSION_API_TOKEN")
                ?: System.getenv("SCALEFUSION_API_TOKEN")
                ?: project.findProperty("SCALEFUSION_API_TOKEN") as? String
            )?.replace("\"", "\\\"") ?: ""
        buildConfigField("String", "SCALEFUSION_API_TOKEN", "\"$apiToken\"")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.constraintlayout)
    implementation("com.android.volley:volley:1.2.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}