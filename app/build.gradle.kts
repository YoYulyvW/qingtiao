import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.autoskip.helper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autoskip.helper"
        minSdk = 26
        targetSdk = 34
        // 版本号可被 CI 覆盖
        versionCode = (System.getenv("APP_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("APP_VERSION_NAME") ?: "1.0.0"
    }

    signingConfigs {
        create("release") {
            val ksB64 = System.getenv("ANDROID_KEYSTORE_BASE64")
            if (ksB64 != null) {
                // CI：从 base64 解码出临时 keystore
                val f = File(System.getProperty("java.io.tmpdir"), "release.p12")
                f.writeBytes(Base64.getDecoder().decode(ksB64))
                storeFile = f
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
                storeType = "pkcs12"
            }
            // ★ 全开 V1/V2/V3 签名（AGP 8.5 默认已全开，此处显式声明防意外）
            enableV1Signing = true   // 兼容 Android 6.0-（旧设备）
            enableV2Signing = true   // Android 7.0+
            enableV3Signing = true   // Android 9+（支持密钥轮换）
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true      // ★ 启用 R8 混淆 + 代码裁剪
            isShrinkResources = true    // ★ 移除未使用资源
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("ANDROID_KEYSTORE_BASE64") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ★ WebRTC（远程控制推流 + DataChannel 触控）
    // io.github.webrtc-sdk 是 org.webrtc 的非官方镜像，发布在 Maven Central
    implementation("io.github.webrtc-sdk:android:125.6422.07")
}
