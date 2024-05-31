import com.android.build.gradle.internal.tasks.factory.dependsOn
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux"

    dependencies {
        implementation("androidx.annotation:annotation:1.8.0")
        implementation("androidx.core:core:1.13.1")
        implementation("androidx.drawerlayout:drawerlayout:1.2.0")
        implementation("androidx.viewpager:viewpager:1.0.0")
        implementation("com.google.android.material:material:1.12.0")

        implementation(project(":terminal-view"))
    }

    defaultConfig {
        versionCode = 118
        versionName = "0.$versionCode"

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
    testImplementation("org.robolectric:robolectric:4.12.1")
}

task("versionName") {
    doLast {
        print(android.defaultConfig.versionName)
    }
}

fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = MessageDigest.getInstance("SHA-256")

    val localUrl = "src/main/cpp/bootstrap-$arch.zip"
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

    val remoteUrl = "https://bootstrap.termux.net/bootstrap-$arch-v$version.zip"
    logger.quiet("Downloading $remoteUrl ...")

    file.parentFile.mkdirs()
    val out = BufferedOutputStream(FileOutputStream(file))

    val connection = URL(remoteUrl).openConnection()
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

task("downloadBootstraps") {
    doLast {
        val version = "4"
        downloadBootstrap("aarch64", "8a4e5380ac98481f8bc544e12b6b58c0cdb43aed648888d5f5c199c0105e5533", version)
        downloadBootstrap("x86_64", "7e9a414aefd827201d867cc7db3e4e84baf5d6d4312c8b3608fa70dab8122783", version)
        // downloadBootstrap("arm", "TODO", version)
        // downloadBootstrap("i686", "TODO", version)
    }
}

afterEvaluate {
    android.applicationVariants.all { variant ->
        variant.javaCompileProvider.dependsOn("downloadBootstraps")
        //variant.javaCompileProvider.dependsOn(downloadBootstraps)
        true
    }
}

// https://stackoverflow.com/questions/75274720/a-failure-occurred-while-executing-appcheckdebugduplicateclasses/
configurations.implementation {
   exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}
