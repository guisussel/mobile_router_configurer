plugins {
    alias(libs.plugins.roteador.cmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Framework consumed by the Xcode project in iosApp/.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:designsystem"))
            implementation(project(":core:data"))
            implementation(project(":core:platform"))
            implementation(project(":drivers:askey"))
            implementation(project(":feature:login"))
            implementation(project(":feature:dashboard"))
            implementation(project(":feature:devices"))
            implementation(project(":feature:wifi"))
            implementation(project(":feature:signal"))
            implementation(project(":feature:settings"))

            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.core)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(project(":core:router"))
            implementation(libs.koin.test)
        }
    }
}
