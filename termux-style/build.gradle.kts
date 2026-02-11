import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.URI

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    namespace = "com.termux.styling"

    defaultConfig {
        versionCode = 35
        versionName = "0.$versionCode"

        val minSdkVersion: String by project
        val targetSdkVersion: String by project
        val compileSdkVersion: String by project
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        compileSdk = compileSdkVersion.toInt()
    }

    androidResources {
        noCompress += "ttf"
        noCompress += "properties"
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.txt"
            )
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    val kotlinVersion: String by project
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
}

tasks.register("versionName") {
    doLast {
        print(android.defaultConfig.versionName)
    }
}


fun downloadFile(downloadTo: File, remoteUrl: String) {
    if (downloadTo.exists()) {
        logger.info("Keeping local file: ${downloadTo.absolutePath}")
        return;
    }

    logger.quiet("Downloading $remoteUrl to $downloadTo ...")
    downloadTo.parentFile.mkdirs()
    val tmpFile = File(downloadTo.absolutePath + ".tmp")
    BufferedOutputStream(FileOutputStream(tmpFile)).use {
        val connection = URI(remoteUrl).toURL().openConnection()
        connection.inputStream.transferTo(it)
    }
    tmpFile.renameTo(downloadTo)
}

tasks {
    getByName<Delete>("clean") {
        doLast {
            val tree = fileTree(File(projectDir, "src/main/assets/fonts"))
            tree.include("**/*.otf")
            tree.include("**/*.ttf")
            tree.forEach { it.delete() }
        }
    }
}

tasks.register("downloadPrebuilt") {
    doLast {
        val nerdFontsVersion = "3.4.0"
        val fonts = mapOf(
            "Adwaita-Mono" to "AdwaitaMonoNerdFont-Regular.ttf",
            "Anonymous-Pro" to "AnonymiceProNerdFont-Regular.ttf",
            "Atkinson-Hyperlegible-Mono" to "AtkynsonMonoNerdFont-Regular.otf",
            "Cascadia-Code" to "CaskaydiaCoveNerdFont-Regular.ttf",
            "D2-Coding" to "D2CodingLigatureNerdFont-Regular.ttf",
            "DejaVu-Sans-Mono" to "DejaVuSansMNerdFont-Regular.ttf",
            "Fantasque-Sans-Mono" to "FantasqueSansMNerdFont-Regular.ttf",
            "Fira-Code" to "FiraCodeNerdFont-Regular.ttf",
            "Fira-Mono" to "FiraMonoNerdFont-Regular.otf",
            "Go-Mono" to "GoMonoNerdFont-Regular.ttf",
            "Hack" to "HackNerdFont-Regular.ttf",
            "Hermit" to "HurmitNerdFont-Regular.otf",
            "Inconsolata" to "InconsolataNerdFont-Regular.ttf",
            "Iosevka" to "IosevkaNerdFont-Regular.ttf",
            "JetBrains-Mono" to "JetBrainsMonoNerdFont-Regular.ttf",
            "Liberation-Mono" to "LiterationMonoNerdFont-Regular.ttf",
            "Meslo" to "MesloLGLNerdFont-Regular.ttf",
            "Monofur" to "MonofurNerdFont-Regular.ttf",
            "Monoid" to "MonoidNerdFont-Regular.ttf",
            "Noto" to "NotoMonoNerdFont-Regular.ttf",
            "OpenDyslexic" to "OpenDyslexicMNerdFont-Regular.otf",
            "Roboto-Mono" to "RobotoMonoNerdFont-Regular.ttf",
            "Source-Code-Pro" to "SauceCodeProNerdFont-Regular.ttf",
            "Terminus" to "TerminessNerdFont-Regular.ttf",
            "Ubuntu-Mono" to "UbuntuMonoNerdFont-Regular.ttf",
            "Victor-Mono" to "VictorMonoNerdFont-Regular.ttf"
        )
        fonts.forEach { (fontName, fontFile) ->
            val fontPack = if (fontName == "Go-Mono") fontName else fontName.replace("-", "")
            val fontUrl =
                "https://github.com/ryanoasis/nerd-fonts/releases/download/v${nerdFontsVersion}/${fontPack}.zip"
            val cacheDir = File(layout.buildDirectory.asFile.get(), "termux-fonts")
            val zipFile = File(cacheDir, "${fontPack}.zip")
            downloadFile(zipFile, fontUrl)
            val destinationDir = "src/main/assets/fonts/"
            val destinationFileName = "${fontName}.ttf"
            copy {
                from(zipTree(zipFile)) {
                    include(fontFile)
                }
                into(destinationDir)
                eachFile {
                    relativePath = RelativePath(true, destinationFileName)
                }
            }
        }
    }
}

afterEvaluate {
    android.applicationVariants.all { variant ->
        variant.javaCompileProvider.dependsOn("downloadPrebuilt")
        true
    }
}
