import org.gradle.api.internal.file.FileOperations
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

    val ndkVersion = project.property("ndkVersion") as String
    this.ndkVersion = ndkVersion

    defaultConfig {
        versionCode = 141
        versionName = "googleplay.2026.06.21"

        val minSdkVersion = project.property("minSdkVersion") as String
        val targetSdkVersion = project.property("targetSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    implementation("androidx.annotation:annotation:1.11.0")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation(project(":terminal-view"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

tasks.register("versionName") {
    val versionName = android.defaultConfig.versionName
    doLast {
        print(versionName)
    }
}

abstract class CleanBootstrapsTask : DefaultTask() {
    @get:InputDirectory abstract val projectDir: DirectoryProperty
    @get:Inject abstract val fileOperations: FileOperations

    @TaskAction
    fun action() {
        val tree = fileOperations.fileTree(File(projectDir.asFile.get(), "src/main/cpp"))
        tree.include("**/bootstrap-*.zip")
        tree.include("**/libproot-*.so")
        tree.forEach { it.delete() }
    }
}

tasks.register<CleanBootstrapsTask>("cleanBootstraps") {
    projectDir = layout.projectDirectory
}

tasks.named("clean") {
    dependsOn("cleanBootstraps")
}

tasks.register("downloadPrebuilt") {
    fun downloadFile(projectDir: Directory, localUrl: String, remoteUrl: String, expectedChecksum: String) {
        val digest = MessageDigest.getInstance("SHA-256")

        val file = File(projectDir.asFile, localUrl)
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

    val projectDir = layout.projectDirectory

    doLast {
        val bootstrapVersion = "2026.10.04-r1"
        val arches = mapOf(
            "aarch64" to "75a71ea34d5a0acef2cf15849b31b11d5c293fc34df5c74f4f905438202dad0d",
            "arm" to "2e053f50d36b01d00c330b509001fd630661a44a54998db503311033147d4ac6",
            "x86_64" to "3a2cd78d8546dad406a52b0458299b625ab059174503c70b4cd1c7093ae6069e"
        )
        arches.forEach { (arch, checksum) ->
            val downloadTo = "src/main/cpp/bootstrap-${arch}.zip"
            val url = "https://github.com/termux-play-store/termux-packages/releases/download/bootstrap-${bootstrapVersion}/bootstrap-${arch}.zip"
            downloadFile(projectDir, downloadTo, url, checksum)
        }

        val prootTag = "proot-2026.06.21-r1"
        val prootVersion = "5.1.107.80-0"
        var prootUrl = "https://github.com/termux-play-store/termux-packages/releases/download/${prootTag}/libproot-loader-ARCH-${prootVersion}.so"
        downloadFile(projectDir, "src/main/jniLibs/armeabi-v7a/libproot-loader.so", prootUrl.replace("ARCH", "arm"), "09729047155df0c1a6b55c265ff4e272107775961d7efaff06bdd7cf37904050")
        downloadFile(projectDir, "src/main/jniLibs/arm64-v8a/libproot-loader.so", prootUrl.replace("ARCH", "aarch64"), "f7e3211e4c210c2a39a1f22b7f38666d99aee172fd009c0d19b84108cf20bb42")
        downloadFile(projectDir, "src/main/jniLibs/x86_64/libproot-loader.so", prootUrl.replace("ARCH", "x86_64"), "86e22d456255417e1d4ee874986571578ff26675ae2e372458e0d87f26454c63")
    }
}

tasks.named("preBuild") {
    dependsOn("downloadPrebuilt")
}
