// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import dev.detekt.gradle.Detekt
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.TaskProvider
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
    // Android Lint names the composable fixtures of a library module after the library itself, so in test sources
    // it reads the fixtures in place of the library and misjudges the library's composables. Lint checks main
    // sources only.
    ignoreTestSources = true
}

private val staticAnalysis: TaskProvider<Task> =
    tasks.register("staticAnalysis") {
        group = "verification"
        description = "Runs ktlint, Android Lint, and detekt with type resolution on every source set."
        dependsOn("ktlintCheck", "lint")
        dependsOn(
            tasks.withType<Detekt>().matching { task ->
                task.name != "detekt" && !task.name.endsWith("SourceSet")
            },
        )
    }

// The ktlint, detekt and Android Lint plugins each wire their task into `check`. Rewiring them behind
// staticAnalysis lets `-x staticAnalysis` drop every static check from `check`.
private fun Task.isStaticCheck(): Boolean = name == "lint" || name == "detekt" || name.startsWith("ktlint")

afterEvaluate {
    tasks.named("check") {
        setDependsOn(
            dependsOn.filterNot { dependency ->
                dependency is TaskProvider<*> && dependency.get().isStaticCheck()
            },
        )
        dependsOn(staticAnalysis)
    }
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
