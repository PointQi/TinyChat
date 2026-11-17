// 根工程只做三件事：声明插件（apply false，保证同一 classloader）、统一 JDK、清理任务。
// ⚠️ AGP 9 起内置 Kotlin 支持，子工程不可再应用 org.jetbrains.kotlin.android。
plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    id("org.jetbrains.kotlin.plugin.compose") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
