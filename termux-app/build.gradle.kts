import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux"

    val ndkVersion: String by project
    this.ndkVersion = ndkVersion

    dependencies {
        implementation("androidx.annotation:annotation:1.9.1")
        implementation("androidx.core:core:1.15.0")
        implementation("androidx.drawerlayout:drawerlayout:1.2.0")
        implementation("androidx.viewpager:viewpager:1.1.0")
        implementation("com.google.android.material:material:1.12.0")

        implementation(project(":terminal-view"))
    }

    defaultConfig {
        versionCode = 136
        versionName = "googleplay.2024.10.30"

        val minSdkVersion: String by project
        val targetSdkVersion: String by project
        val compileSdkVersion: String by project
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            ndkBuild {
                cFlags += listOf("-std=c11", "-Wall", "-Wextra", "-Werror", "-Os", "-fno-stack-protector", "-Wl,--gc-sections")
            }
        }
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
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/cpp/Android.mk")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        warningsAsErrors = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}

task("versionName") {
    doLast {
        print(android.defaultConfig.versionName)
    }
}

fun downloadFile(localUrl: String, remoteUrl: String, expectedChecksum: String) {
    val digest = MessageDigest.getInstance("SHA-256")

    val file = File(projectDir, localUrl)
    if (file.exists()) {
        val buffer = ByteArray(8192)
        val input = FileInputStream(file)
        while (true) {
            val readBytes = input.read(buffer)
            if (readBytes < 0) break
            digest.update(buffer, 0, readBytes)
        }
        var checksum = BigInteger(1, digest.digest()).toString(16)
        while (checksum.length < 64) { checksum = "0$checksum" }
        if (checksum == expectedChecksum) {
            return
        } else {
            logger.warn("Deleting old local file with wrong hash: $localUrl: expected: $expectedChecksum, actual: $checksum")
            file.delete()
        }
    }

    logger.quiet("Downloading $remoteUrl ...")

    file.parentFile.mkdirs()
    val out = BufferedOutputStream(FileOutputStream(file))

    val connection = URI(remoteUrl).toURL().openConnection()
    val digestStream = DigestInputStream(connection.inputStream, digest)
    digestStream.transferTo(out)
    out.close()

    var checksum = BigInteger(1, digest.digest()).toString(16)
    while (checksum.length < 64) { checksum = "0$checksum" }
    if (checksum != expectedChecksum) {
        file.delete()
        throw GradleException("Wrong checksum for $remoteUrl:\n Expected: $expectedChecksum\n Actual:   $checksum")
    }
}

tasks {
    getByName<Delete>("clean") {
        doLast {
            val tree = fileTree(File(projectDir, "src/main/cpp"))
            tree.include("bootstrap-*.zip")
            tree.forEach { it.delete() }
        }
    }
}

task("downloadPrebuilt") {
    doLast {
        val bootstrapVersion = "2024.10.30-r1"
        val arches = mapOf(
            "aarch64" to "49f4f6a92037061d6a4c67332bbe46ca217c7761ce1170ab2be3ef500ff4afc3",
            "x86_64" to "7492fbbc9d1f6fc9e8a853698d78d4a3f2d168e1ea0a6d49bd52c4fdae9de4e2"
        )
        arches.forEach { (arch, checksum) ->
            val downloadTo = "src/main/cpp/bootstrap-${arch}.zip"
            val url = "https://github.com/termux-play-store/termux-packages/releases/download/bootstrap-${bootstrapVersion}/bootstrap-${arch}.zip"
            downloadFile(downloadTo, url, checksum)
        }

        val prootVersion = "5.1.107-65"
        downloadFile("src/main/jniLibs/arm64-v8a/libproot-loader.so", "https://bootstrap.termux.net/libproot-loader-aarch64-$prootVersion.so", "23cbee2320ed6f55ec4c47d50573a3ee59163f84c6e1bfaf7fb67049f71a2b59")
        downloadFile("src/main/jniLibs/x86_64/libproot-loader.so", "https://bootstrap.termux.net/libproot-loader-x86_64-$prootVersion.so", "bf69995d5c9d35591197ce61f6046e80116858334109bf9b6ea9d787ca2fe256")
        downloadFile("src/main/jniLibs/arm64-v8a/libproot-loader32.so", "https://bootstrap.termux.net/libproot-loader32-aarch64-$prootVersion.so", "8e0d7fbdc5937886c272a3ef7c2c1d1b62ab51dcfc542b938675bf05baf4a83a")
        downloadFile("src/main/jniLibs/x86_64/libproot-loader32.so", "https://bootstrap.termux.net/libproot-loader32-x86_64-$prootVersion.so", "b33f2ef262ec0458504e6327d84c7d7b87cc504c0ea53cf3eccc60a3746241b2")
    }
}

afterEvaluate {
    android.applicationVariants.all { variant ->
        variant.javaCompileProvider.dependsOn("downloadPrebuilt")
        true
    }
}

// https://stackoverflow.com/questions/75274720/a-failure-occurred-while-executing-appcheckdebugduplicateclasses/
configurations.implementation {
   exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}
