rootProject.name = "Roteador"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// Apps
include(":androidApp")
include(":composeApp")

// Core
include(":core:model")
include(":core:router")
include(":core:platform")
include(":core:data")
include(":core:designsystem")
include(":core:testing")

// Router drivers (one module per router family)
include(":drivers:askey")

// Features (one module per tab/screen; features never depend on each other)
include(":feature:login")
include(":feature:dashboard")
include(":feature:devices")
include(":feature:wifi")
include(":feature:signal")
include(":feature:settings")
