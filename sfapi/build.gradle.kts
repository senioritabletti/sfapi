plugins {
}

    android {
    namespace = "com.digihappy.sfapi"

    defaultConfig {
      applicationId = "com.digihappy.sfapi"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
       release {
           isMinifyEnabled = false
           proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
       }
    }
    }

  dependencies {
  }