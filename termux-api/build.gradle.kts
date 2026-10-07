plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux.api"

    defaultConfig {
        versionCode = 51
        versionName = "0.$versionCode"

        val minSdkVersion = project.property("minSdkVersion") as String
        val targetSdkVersion = project.property("targetSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.txt")
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.biometric:biometric:1.4.0-alpha07")
    implementation("androidx.media:media:1.8.0")
}

tasks.register("versionName") {
    val versionName = android.defaultConfig.versionName
    doLast {
        print(versionName)
    }
}
