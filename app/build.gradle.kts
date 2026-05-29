import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 59
        versionName = "20260529"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_static")
            }
        }
    }

    sourceSets {
        // 使用更通用的 getByName("main") 或者直接使用命名方法
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/INDEX.LIST"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    signingConfigs {
        create("neko") {
            val jks = rootProject.file("neko_key.jks")
            check(jks.exists()) {
                "未找到 neko_key.jks，请放在项目根目录：${rootProject.projectDir}/neko_key.jks"
            }
            val propsFile = rootProject.file("keystore.properties")
            check(propsFile.exists()) {
                "未找到 keystore.properties。请复制 keystore.properties.example 为 keystore.properties 并填写 storePassword、keyPassword、keyAlias"
            }
            // load(InputStream) 固定按 ISO-8859-1 解析，含中文等密码会读错 → validateSigningRelease 报密钥错误
            val p = Properties()
            val propsText = propsFile.readText(StandardCharsets.UTF_8).removePrefix("\uFEFF")
            StringReader(propsText).use { p.load(it) }
            storeFile = jks
            storePassword = p.getProperty("storePassword")?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("keystore.properties 缺少或为空: storePassword")
            keyAlias = p.getProperty("keyAlias")?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("keystore.properties 缺少或为空: keyAlias")
            keyPassword = p.getProperty("keyPassword")?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("keystore.properties 缺少或为空: keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("neko")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("neko")
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    lint {
        disable.add("ComposableDestinationInComposeScope")
        disable.add("ComposableNavGraphInComposeScope")
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xcontext-receivers"
            )
        }
    }

    ndkVersion = "29.0.14206865"
    buildToolsVersion = "36.1.0 rc1"
}

dependencies {
    // AndroidX & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.ui.geometry)

    // 液态玻璃（源码：仓库内 AndroidLiquidGlass/backdrop，由 :backdrop-port 模块接入）
    implementation(project(":backdrop-port"))

    // Network (Ktor)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    // Image & Audio
    implementation(libs.coil.core)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:extension-mediasession:2.19.1")

    // ✅ Room 必须使用 KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.accompanist.permissions)

    // 测试相关
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}