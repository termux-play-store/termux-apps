plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux.tasker"

    defaultConfig {
        versionCode = 7
        versionName = "0.$versionCode"

        val minSdkVersion = project.property("minSdkVersion") as String
        val targetSdkVersion = project.property("targetSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()

        //manifestPlaceholders.TERMUX_PACKAGE_NAME = "com.termux"
        //manifestPlaceholders.TERMUX_APP_NAME = "Termux"
        //manifestPlaceholders.TERMUX_TASKER_APP_NAME = "Termux:Tasker"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("com.google.android.material:material:1.14.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}

tasks.register("versionName") {
    val versionName = android.defaultConfig.versionName
    doLast {
        print(versionName)
    }
}
