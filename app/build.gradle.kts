plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.example.aichat"
    // compileSdk = 36：安卓 16 — AGP 8.7.x 官方支持
    // （Android 16 / API 36 需要 AGP 8.7+；覆盖 MagicOS 10.0+ 全机型）
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aichat"
        // minSdk = 29：安卓 10，覆盖荣耀 90GT（出厂 MagicOS 7.2 / Android 13）
        minSdk = 29
        // targetSdk = 36：安卓 16 — MagicOS 10.0+ 强要求 targetSdk ≥ 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        vectorDrawables { useSupportLibrary = true }

        // 仅打包 arm 架构 so 库 —— Android 10+ 真机均为 64 位或 32 位 ARM
        // armeabi-v7a 兼容老机型，arm64-v8a 为荣耀 90GT 主流架构
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    // release 签名配置：优先从 gradle.properties（或 CI 通过 -P 注入）读取，
    // 未配置时回退到 AGP 自动生成的 debug keystore（所有 Android 环境都会产生）。
    //    · KEYSTORE_FILE       （相对路径或绝对路径）
    //    · KEYSTORE_PASSWORD   （密钥库密码）
    //    · KEY_ALIAS           （密钥别名）
    //    · KEY_PASSWORD        （密钥密码）
    signingConfigs {
        create("release") {
            val keystoreFromProps = (project.properties["KEYSTORE_FILE"] as? String)?.takeIf { it.isNotBlank() }
            if (keystoreFromProps != null && java.io.File(keystoreFromProps).exists()) {
                // —— 显式提供了 keystore —— 用用户提供的签名信息
                storeFile = java.io.File(keystoreFromProps)
                storePassword = (project.properties["KEYSTORE_PASSWORD"] as? String) ?: ""
                keyAlias = (project.properties["KEY_ALIAS"] as? String) ?: ""
                keyPassword = (project.properties["KEY_PASSWORD"] as? String) ?: ""
            } else {
                // —— fallback：AGP 自动生成的 debug keystore（任何 Android SDK 环境都存在）
                val debugStore = java.io.File("${System.getenv("HOME")}/.android/debug.keystore")
                if (debugStore.exists()) {
                    storeFile = debugStore
                    storePassword = "android"
                    keyAlias = "androiddebugkey"
                    keyPassword = "android"
                } else {
                    // 终极 fallback：让 release 用 debug signingConfig，AGP 会处理
                    println("[signing] 未找到 release keystore，将使用 debug 签名")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // 首选 release 签名；如未找到，则回退到 debug 签名（避免 CI 上因缺密钥而失败）
            signingConfig = signingConfigs.findByName("release")
                ?.takeIf { it.storeFile?.exists() == true }
                ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Kotlin 2.2+ 由 kotlin-gradle-plugin 内置 Compose Compiler，无需显式声明
    // composeOptions { kotlinCompilerExtensionVersion = "..." } 可省略
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.paging:paging-runtime-ktx:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")

    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-android-compiler:2.55")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    implementation("androidx.security:security-crypto:1.0.0")

    // Coil —— Compose 图片加载库（用于 content:// URI / http URL / data URI）
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
