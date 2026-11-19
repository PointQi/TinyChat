plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.tinychat.audio"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
}

// 语音（M5）：录音、WAV 封装、频谱、TTS 播放。
// 除了 VoiceRecorder / TtsPlayer（要真机音频设备），其余都是纯计算，单测直接跑 JVM。
dependencies {
    testImplementation(libs.junit)
}
