plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux.widget"

    defaultConfig {
        versionCode = 14
        versionName = "0.$versionCode"

        val minSdkVersion: String by project
        val targetSdkVersion: String by project
        val compileSdkVersion: String by project
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        getByName("release") {
           isMinifyEnabled = true
           isShrinkResources = true
           proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // required for TermuxWidgetControlsProviderService
    implementation("org.reactivestreams:reactive-streams:1.0.4")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    implementation("androidx.annotation:annotation:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
}

task("versionName") {
    doLast {
        print(android.defaultConfig.versionName)
    }
}
