plugins {
    `kotlin-dsl`
}

group = "com.roteador.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = libs.plugins.roteador.kmp.library.get().pluginId
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = libs.plugins.roteador.cmp.library.get().pluginId
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("feature") {
            id = libs.plugins.roteador.feature.get().pluginId
            implementationClass = "FeatureConventionPlugin"
        }
    }
}
