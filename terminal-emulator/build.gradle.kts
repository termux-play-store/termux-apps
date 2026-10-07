plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.emulator"

    val ndkVersion = project.property("ndkVersion") as String
    this.ndkVersion = ndkVersion

    defaultConfig {
        val minSdkVersion = project.property("minSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
        minSdk = minSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()
        ndk {
            abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.11.0")
    testImplementation("junit:junit:4.13.2")
}
