plugins {
    id("com.android.application") version "8.10.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
    // Hilt 2.59+ 要求 AGP 9.x（Breaking change），当前 AGP 8.x 需锁定 2.58
    id("com.google.dagger.hilt.android") version "2.58" apply false
    id("androidx.room") version "2.8.4" apply false
}
