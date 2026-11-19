plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.tinychat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.tinychat"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ⚠️ 仅供本机联调：从 .local/dev-key.txt（已 gitignore）注入开发用 Key。
    // 正式版本走 M8-6 的服务商与 API Key 页，Key 存 Keystore 加密后的 DataStore。
    val devKeyFile = rootProject.file(".local/dev-key.txt")
    val devApiKey = if (devKeyFile.isFile) {
        devKeyFile.readText().trim().replace("\\", "\\\\").replace("\"", "\\\"")
    } else {
        ""
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_API_KEY", "\"$devApiKey\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // 性能对照专用：release 的编译/运行语义（isDebuggable=false，走 ART 优化路径），
        // 但挂 debug 签名并注入开发 Key，这样能直接装到测试机上量帧率。
        // ⚠️ 判断「卡不卡」必须用它，拿 debug 包量出来的是假的（实测 debug 50th 18ms、release 6ms）。
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "DEV_API_KEY", "\"$devApiKey\"")
            matchingFallbacks += listOf("release")
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    debugImplementation(project(":core:markdown"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:sessions"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
}
