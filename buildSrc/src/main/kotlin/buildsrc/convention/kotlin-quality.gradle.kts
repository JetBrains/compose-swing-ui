package buildsrc.convention

import dev.detekt.gradle.Detekt
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
    // Standalone Android Lint runner (no AGP/Android SDK) hosting the checks below.
    id("com.android.lint")
}

// Precompiled script plugins do not get generated `libs` accessors, so resolve the catalog directly.
private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun requiredVersion(alias: String): String =
    libs
        .findVersion(alias)
        .orElseThrow { IllegalStateException("Missing version '$alias' in gradle/libs.versions.toml") }
        .requiredVersion

ktlint {
    version.set(requiredVersion("ktlint"))
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    ignoreFailures.set(false)
    parallel.set(true)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("21")
}

// The default `detekt` task runs without type resolution, so rules that need the compile classpath
// (UseOrEmpty, UseCheckOrError, ImplicitDefaultLocale, ...) never fire. Wire the type-resolution tasks
// detektMain/detektTest - which compile the sources first - into `check` so those rules are enforced.
tasks.named("check") {
    dependsOn(tasks.named("detektMain"), tasks.named("detektTest"))
}

plugins.withId("java-test-fixtures") {
    tasks.named("check") {
        dependsOn(tasks.named("detektTestFixtures"))
    }
}

// The module carrying this project's own detekt rules; it applies this convention too, and a module
// cannot contribute its rules to itself.
private val detektRuleSet = ":swing-ui-detekt"

dependencies {
    // This project's own rules, and the Compose rules that travel with them - the same artifact a
    // consumer adds.
    if (path != detektRuleSet) {
        "detektPlugins"(project(detektRuleSet))
    }
}

lint {
    warningsAsErrors = true
    lintConfig = rootProject.file("config/lint/lint.xml")
}

dependencies {
    // The Compose runtime publishes the checks for its own contracts as an artifact. They are the
    // authors' checks, kept current with the runtime this project compiles against, so the runtime is
    // held to them here rather than to a reimplementation of them.
    "lintChecks"(
        libs
            .findLibrary("composeRuntimeLint")
            .orElseThrow { IllegalStateException("Missing library 'composeRuntimeLint' in gradle/libs.versions.toml") },
    )
}
