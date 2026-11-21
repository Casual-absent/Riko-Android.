plugins {
    id("com.android.application")
}

android {
    namespace = "com.casualabsent.riko"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.casualabsent.riko"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
