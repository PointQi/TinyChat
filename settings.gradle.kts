pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        // ⚠️ 版本集中在这里（Spike 实测：分散声明会导致 KGP/AGP classloader 冲突）
        id("com.android.application") version "9.5.1"
        id("com.android.library") version "9.5.1"
        id("org.jetbrains.kotlin.jvm") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TinyChat"

include(":app")

include(":core:model")
include(":core:designsystem")
include(":core:ui")

include(":feature:chat")
include(":feature:sessions")
include(":feature:settings")
