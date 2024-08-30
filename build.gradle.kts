buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        val androidGradlePluginVersion: String by project
        val kotlinVersion: String by project
        classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
