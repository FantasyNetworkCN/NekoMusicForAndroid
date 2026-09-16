import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// CI supplies signing values through environment variables. Local development
// can continue using the ignored keystore.properties + neko_key.jks files.
val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties()
if (signingPropertiesFile.isFile) {
    val signingPropertiesText = signingPropertiesFile
        .readText(StandardCharsets.UTF_8)
        .removePrefix("\uFEFF")
    StringReader(signingPropertiesText).use { signingProperties.load(it) }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.trim()?.takeIf { it.isNotEmpty() }
        ?: signingProperties.getProperty(propertyName)?.trim()?.takeIf { it.isNotEmpty() }

val signingKeystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: "neko_key.jks"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.neko.music"
    compileSdk = 37

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.neko.music"
        minSdk = 23
        targetSdk = 37
        versionCode = 75
        versionName = "20260916"

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
            val jks = rootProject.file(signingKeystorePath)
            check(jks.exists()) {
                "未找到签名 keystore：$signingKeystorePath。请配置 ANDROID_KEYSTORE_FILE 或放置 neko_key.jks"
            }
            storeFile = jks
            storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                ?: error("缺少签名密码：请配置 ANDROID_KEYSTORE_PASSWORD 或 keystore.properties")
            keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                ?: error("缺少签名别名：请配置 ANDROID_KEY_ALIAS 或 keystore.properties")
            keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
                ?: error("缺少密钥密码：请配置 ANDROID_KEY_PASSWORD 或 keystore.properties")
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

    // 液态玻璃
    implementation("io.github.kyant0:backdrop:2.0.1")

    // ✅ Room 必须使用 KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.accompanist.permissions)

    // 扫码登录：CameraX 预览/分析 + zxing 解码
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)

    // 测试相关
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
