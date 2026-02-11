buildscript {
    repositories {
        mavenCentral()
        google()
    }

    dependencies {
        val androidGradlePluginVersion: String by project
        classpath("com.android.tools.build:gradle:$androidGradlePluginVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
