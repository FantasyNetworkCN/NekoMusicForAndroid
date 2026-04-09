plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-kapt")
}

android {
    namespace = "com.neko.music"
    compileSdk = 36

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.neko.music"
        minSdk = 23
        targetSdk = 36
        versionCode = 42
        versionName = "20260403"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 启用矢量图支持
        vectorDrawables.useSupportLibrary = true
        
        // 配置ABI过滤，仅包含arm64-v8a和armeabi-v7a
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
        
        // 启用增量编译
        buildConfigField("boolean", "ENABLE_DEBUG", "false")
        // 配置API基础URL
        buildConfigField("String", "BASE_URL", "\"https://music.cnmsb.xin\"")
    }

    signingConfigs {
        create("release") {
            // 配置 v1 v2 v3 v4 签名
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            
            // 启用更严格的优化
            isDebuggable = false
            isJniDebuggable = false
            renderscriptOptimLevel = 3
        }
    }
    
    // R8配置
    buildFeatures {
        compose = true
        buildConfig = true
    }

    
    // 配置打包选项
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
    
    // 配置资源压缩
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xcontext-receivers"
        )
    }
    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.1.0 rc1"
}

dependencies {

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)

    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    

    // Ktor

    implementation(libs.ktor.client.core)

    implementation(libs.ktor.client.okhttp)

    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.ktor.client.logging)

    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.serialization.json)

    

    // Coil for image loading

    implementation(libs.coil.compose)

    

    // ExoPlayer for audio playback (仅使用核心模块)
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    

    // Room database

    implementation(libs.androidx.room.runtime)

    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation.layout)

    kapt(libs.androidx.room.compiler)

    

    // Accompanist Permissions

    implementation(libs.accompanist.permissions)

    

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    kapt("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
}