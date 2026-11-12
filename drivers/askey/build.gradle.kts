plugins {
    alias(libs.plugins.roteador.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:router"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
        // Live check against a real router (skipped without ROUTER_PASSWORD).
        getByName("androidHostTest").dependencies {
            implementation(project(":core:data"))
        }
    }
}
