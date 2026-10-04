plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blackjack21"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blackjack21"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0" // 交付版本 blackjack21-v1.0（总方案第 25 节）
    }

    // 阶段10：本地个人项目，签名密钥随工程存放（keystore/blackjack21.jks，不入公开仓库）
    signingConfigs {
        if (rootProject.file("keystore/blackjack21.jks").exists()) {
            create("release") {
                storeFile = rootProject.file("keystore/blackjack21.jks")
                storePassword = "blackjack21-release"
                keyAlias = "blackjack21"
                keyPassword = "blackjack21-release"
            }
        }
    }

    buildTypes {
        release {
            // 阶段10：R8 压缩 + 资源压缩，目标 APK < 10MB（总方案第 23 节）
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.findByName("release") != null) {
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

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // 游戏逻辑零依赖；仅单元测试用 JUnit4（总方案第 14、15 节要求自动测试）
    testImplementation("junit:junit:4.13.2")
}
