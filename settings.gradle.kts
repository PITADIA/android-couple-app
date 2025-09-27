pluginManagement {
    plugins {
        id("com.android.application") version "7.3.1"
        id("org.jetbrains.kotlin.android") version "1.8.22"
        id("org.jetbrains.kotlin.kapt") version "1.8.22"
        id("com.google.gms.google-services") version "4.3.15"
    }
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
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack pour UCrop
        maven { url = uri("https://jitpack.io") }
        // RevenueCat repository
        maven { url = uri("https://maven.revenuecat.com") }
    }
}

rootProject.name = "Love2LoveApp"
include(":App")
