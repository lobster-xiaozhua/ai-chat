buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.10.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.0")
        classpath("org.jetbrains.kotlin:kotlin-serialization:2.3.0")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.58")
        classpath("androidx.room:room-gradle-plugin:2.8.4")
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
