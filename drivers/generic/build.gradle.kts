plugins {
    alias(libs.plugins.roteador.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:router"))
            implementation(libs.ktor.network)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.ktor.client.okhttp)
        }
    }
}
