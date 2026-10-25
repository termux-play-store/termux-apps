import org.gradle.api.internal.file.FileOperations
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.net.URI

plugins {
    id("com.android.application")
}

android {
    namespace = "com.termux.styling"

    defaultConfig {
        versionCode = 35
        versionName = "0.$versionCode"

        val minSdkVersion = project.property("minSdkVersion") as String
        val targetSdkVersion = project.property("targetSdkVersion") as String
        val compileSdkVersion = project.property("compileSdkVersion") as String
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
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

tasks.register("versionName") {
    val versionName = android.defaultConfig.versionName
    doLast {
        print(versionName)
    }
}

abstract class CleanFontsTask : DefaultTask() {
    @get:InputDirectory abstract val projectDir: DirectoryProperty
    @get:Inject abstract val fileOperations: FileOperations

    @TaskAction
    fun action() {
        val tree = fileOperations.fileTree(File(projectDir.asFile.get(), "src/main/assets/fonts"))
        tree.include("**/*.otf")
        tree.include("**/*.ttf")
        tree.forEach { it.delete() }
    }
}

tasks.register<CleanFontsTask>("cleanFonts") {
    projectDir = layout.projectDirectory
}

tasks.named("clean") {
    dependsOn("cleanFonts")
}

abstract class DownloadFontsTask : DefaultTask() {
    @get:InputFiles abstract val buildDirectory: DirectoryProperty
    @get:Inject abstract val fs: FileSystemOperations
    @get:Inject abstract val fileOperations: FileOperations

    @TaskAction
    fun action() {
        val nerdFontsVersion = "3.5.1"
        // Use Nerd Font Mono variants so patched icons fit a single terminal cell.
        // "Mono" in the original family name alone does not select this variant.
        val fonts = mapOf(
            "Adwaita-Mono" to "AdwaitaMonoNerdFontMono-Regular.ttf",
            "Anonymous-Pro" to "AnonymiceProNerdFontMono-Regular.ttf",
            "Atkinson-Hyperlegible-Mono" to "AtkynsonMonoNerdFontMono-Regular.otf",
            "Cascadia-Code" to "CaskaydiaCoveNerdFontMono-Regular.ttf",
            "D2-Coding" to "D2KodingLigatureNerdFontMono-Regular.ttf",
            "DejaVu-Sans-Mono" to "DejaVuSansMNerdFontMono-Regular.ttf",
            "Fantasque-Sans-Mono" to "FantasqueSansMNerdFontMono-Regular.ttf",
            "Fira-Code" to "FiraCodeNerdFontMono-Regular.ttf",
            "Fira-Mono" to "FiraMonoNerdFontMono-Regular.otf",
            "Go-Mono" to "GoMonoNerdFontMono-Regular.ttf",
            "Hack" to "HackNerdFontMono-Regular.ttf",
            "Hermit" to "HurmitNerdFontMono-Regular.otf",
            "Inconsolata" to "InconsolataNerdFontMono-Regular.ttf",
            "Iosevka" to "IosevkaNerdFontMono-Regular.ttf",
            "JetBrains-Mono" to "JetBrainsMonoNerdFontMono-Regular.ttf",
            "Liberation-Mono" to "LiterationMonoNerdFontMono-Regular.ttf",
            "Meslo" to "MesloLGLNerdFontMono-Regular.ttf",
            "Monofur" to "MonofurNerdFontMono-Regular.ttf",
            "Monoid" to "MonoidNerdFontMono-Regular.ttf",
            "Noto" to "NotoMonoNerdFontMono-Regular.ttf",
            "OpenDyslexic" to "OpenDyslexicMNerdFontMono-Regular.otf",
            "Roboto-Mono" to "RobotoMonoNerdFontMono-Regular.ttf",
            "Source-Code-Pro" to "SauceCodeProNerdFontMono-Regular.ttf",
            "Terminus" to "TerminessNerdFontMono-Regular.ttf",
            "Ubuntu-Mono" to "UbuntuMonoNerdFontMono-Regular.ttf",
            "Victor-Mono" to "VictorMonoNerdFontMono-Regular.ttf"
        )
        fonts.forEach { (fontName, fontFile) ->
            val fontPack = if (fontName == "Go-Mono") fontName else fontName.replace("-", "")
            val fontUrl =
                "https://github.com/ryanoasis/nerd-fonts/releases/download/v${nerdFontsVersion}/${fontPack}.zip"
            val cacheDir = File(buildDirectory.asFile.get(), "termux-fonts/$nerdFontsVersion")
            val zipFile = File(cacheDir, "${fontPack}.zip")
            downloadFile(zipFile, fontUrl)
            val destinationDir = "src/main/assets/fonts/"
            val destinationFileName = "${fontName}.ttf"
            fs.copy {
                from(fileOperations.zipTree(zipFile)) { include(fontFile) }
                into(destinationDir)
                eachFile { relativePath = RelativePath(true, destinationFileName) }
            }
        }

        // Monaspace's official Nerd Fonts builds already size icons to a single cell.
        val monaspaceVersion = "1.400"
        val monaspaceArchive = "monaspace-nerdfonts-v$monaspaceVersion.zip"
        val monaspaceZip = File(
            buildDirectory.asFile.get(),
            "termux-fonts/monaspace/$monaspaceVersion/$monaspaceArchive"
        )
        downloadFile(
            monaspaceZip,
            "https://github.com/githubnext/monaspace/releases/download/v$monaspaceVersion/$monaspaceArchive"
        )
        val monaspaceFonts = fileOperations.zipTree(monaspaceZip)
        val neonFont = "NerdFonts/Monaspace Neon/MonaspaceNeonNF-Regular.otf"
        fs.copy {
            from(monaspaceFonts.matching { include(neonFont) }.singleFile)
            into("src/main/assets/fonts/")
            rename { "Monaspace-Neon.ttf" }
        }
    }

    fun downloadFile(downloadTo: File, remoteUrl: String) {
        if (downloadTo.exists()) {
            logger.info("Keeping local file: ${downloadTo.absolutePath}")
            return
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
}

tasks.register<DownloadFontsTask>("downloadFonts") {
    buildDirectory = project.layout.buildDirectory
}

tasks.named("preBuild") {
    dependsOn("downloadFonts")
}
