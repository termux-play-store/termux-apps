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
        implementation("androidx.core:core:1.17.0")
        implementation("androidx.drawerlayout:drawerlayout:1.2.0")
        implementation("androidx.viewpager:viewpager:1.1.0")
        implementation("com.google.android.material:material:1.13.0")

        implementation(project(":terminal-view"))
    }

    defaultConfig {
        versionCode = 139
        versionName = "googleplay.2026.01.07"

        val minSdkVersion: String by project
        val targetSdkVersion: String by project
        val compileSdkVersion: String by project
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
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
    testImplementation("org.robolectric:robolectric:4.16")
}

tasks.register("versionName") {
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
            val tree = fileTree(File(projectDir, "src/main"))
            tree.include("**/bootstrap-*.zip")
            tree.include("**/libproot-*.so")
            tree.forEach { it.delete() }
        }
    }
}

tasks.register("downloadPrebuilt") {
    doLast {
        val bootstrapVersion = "2026.01.06-r1"
        val arches = mapOf(
            "aarch64" to "a895ac47b537580812f8a9b91316abb599cdc2132d5a8de35f7f3e2efaf05ff4",
            "arm" to "000373f77ec9a1a6c850d4bf9b2516c3561db35d4205e924f0aa6fb7adcb3d86",
            "x86_64" to "bf89369aed73cdef23e48d0f6e12e3e68f563c5e178a9ae52ded102226cbb938"
        )
        arches.forEach { (arch, checksum) ->
            val downloadTo = "src/main/cpp/bootstrap-${arch}.zip"
            val url = "https://github.com/termux-play-store/termux-packages/releases/download/bootstrap-${bootstrapVersion}/bootstrap-${arch}.zip"
            downloadFile(downloadTo, url, checksum)
        }

        val prootTag = "proot-2026.01.04-r1"
        val prootVersion = "5.1.107-68"
        var prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader-ARCH-${prootVersion}.so"
        downloadFile("src/main/jniLibs/armeabi-v7a/libproot-loader.so", prootUrl.replace("ARCH", "arm"), "09729047155df0c1a6b55c265ff4e272107775961d7efaff06bdd7cf37904050")
        downloadFile("src/main/jniLibs/arm64-v8a/libproot-loader.so", prootUrl.replace("ARCH", "aarch64"), "f7e3211e4c210c2a39a1f22b7f38666d99aee172fd009c0d19b84108cf20bb42")
        downloadFile("src/main/jniLibs/x86_64/libproot-loader.so", prootUrl.replace("ARCH", "x86_64"), "86e22d456255417e1d4ee874986571578ff26675ae2e372458e0d87f26454c63")
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
