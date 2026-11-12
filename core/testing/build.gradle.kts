plugins {
    alias(libs.plugins.roteador.kmp.library)
}

// Test doubles shared by every feature's tests. Never a dependency of production code.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:data"))
        }
    }
}
