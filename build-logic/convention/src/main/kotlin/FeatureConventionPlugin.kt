import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * A feature = one screen + its ViewModel + a Koin module.
 *
 * Features only see `core:model`, `core:data` and `core:designsystem`. They never
 * talk to a router driver directly and never depend on each other, so a new tab
 * is a new module that plugs into `composeApp` and nothing else changes.
 */
class FeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("roteador.cmp.library")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.getByName("commonMain").dependencies {
                implementation(project(":core:model"))
                implementation(project(":core:data"))
                implementation(project(":core:designsystem"))

                implementation(libs.library("androidx-lifecycle-viewmodel-compose"))
                implementation(libs.library("androidx-lifecycle-runtime-compose"))
                implementation(project.dependencies.platform(libs.library("koin-bom")))
                implementation(libs.library("koin-core"))
                implementation(libs.library("koin-compose-viewmodel"))
            }
            sourceSets.getByName("commonTest").dependencies {
                implementation(project(":core:testing"))
            }
        }
    }
}
