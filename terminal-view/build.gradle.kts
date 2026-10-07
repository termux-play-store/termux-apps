plugins {
    id("com.android.library")
}

android {
    namespace = "com.termux.view"

    defaultConfig {
        val minSdkVersion = project.property("minSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
        minSdk = minSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.11.0")
    api(project(":terminal-emulator"))

    testImplementation("junit:junit:4.13.2")
}
