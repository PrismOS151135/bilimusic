plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.prismOS.bilimusic"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.prismOS.bilimusic"
        minSdk = 19
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 26
        versionCode = 2
        versionName = "2.4"
        multiDexEnabled = true
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
    implementation("androidx.media3:media3-exoplayer:1.1.1")
    implementation("androidx.media3:media3-common:1.1.1")
    implementation("androidx.multidex:multidex:2.0.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}