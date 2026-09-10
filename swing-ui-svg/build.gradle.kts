// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
    id("buildsrc.convention.window-system-lock")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}

    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi")
    }
}

dependencies {
    api(libs.composeRuntime)
    api(libs.jsvg)
    api(project(":swing-ui-core"))
    implementation(libs.kotlinxCoroutinesCore)

    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-components"))
    testImplementation(project(":swing-ui-test"))
    testImplementation(testFixtures(project(":swing-ui-core")))
}

jacocoCoverage {
    lineMinimum.set("0.95".toBigDecimal())
    branchMinimum.set("0.85".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui :: swing-ui-svg")
            description.set(
                "Declarative SVG rendering for the Compose-over-Swing runtime, backed by JSVG.",
            )
        }
    }
}
