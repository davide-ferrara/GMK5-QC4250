plugins {
    id("com.android.application")
}

android {
    namespace = "com.golfv.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.golfv.radio"
        minSdk = 30
        targetSdk = 30
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
