buildscript {
    extra.apply {
        set("kotlin_version", "2.0.0")
    }

    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        val kotlinVersion = rootProject.extra.get("kotlin_version") as String
        classpath("com.android.tools.build:gradle:8.5.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
