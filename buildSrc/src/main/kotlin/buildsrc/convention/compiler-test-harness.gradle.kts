// Configures in-process Kotlin compiler testing with the Compose compiler plugin.
package buildsrc.convention

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun requiredLibrary(alias: String) =
    libs
        .findLibrary(alias)
        .orElseThrow { IllegalStateException("Missing library '$alias' in gradle/libs.versions.toml") }

private fun requiredVersion(alias: String): String =
    libs
        .findVersion(alias)
        .orElseThrow { IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml") }
        .requiredVersion

// Resolvable configuration holding only the Compose compiler plugin JAR without transitive dependencies.
// Kept off the test classpath so the in-process harness loads it strictly as an -Xplugin argument.
private val composeCompilerPluginClasspath =
    configurations.create("composeCompilerPluginClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }

dependencies {
    "testImplementation"("org.jetbrains.kotlin:kotlin-compiler-embeddable:${requiredVersion("kotlin")}")
    composeCompilerPluginClasspath(requiredLibrary("kotlinComposeCompilerPluginEmbeddable")) {
        isTransitive = false
    }
}

// Aligns embeddable compiler artifacts with the active Kotlin version.
private val embeddableCompilerArtifacts =
    setOf("kotlin-compiler-embeddable", "kotlin-annotation-processing-embeddable")
private val kotlinVersion = requiredVersion("kotlin")

configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name in embeddableCompilerArtifacts) {
            useVersion(kotlinVersion)
        }
    }
}

private class ComposeCompilerPluginArgumentProvider(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    val pluginClasspath: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> =
        listOf("-Dcompose.compiler.plugin.classpath=${pluginClasspath.asPath}")
}

// Passes the plugin JAR path lazily via system property while preserving configuration cache compatibility.
private val pluginClasspath: FileCollection = composeCompilerPluginClasspath
tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(ComposeCompilerPluginArgumentProvider(pluginClasspath))
}
