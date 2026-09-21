// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    `java-test-fixtures`
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.composable-fixtures")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
    id("buildsrc.convention.window-system-lock")
    id("buildsrc.convention.exclusive-window-system-tests")
    id("buildsrc.convention.compiler-test-harness")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
            }
        }
    }

    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi")
    }
}

dependencies {
    // ScrollState.animateScrollTo and animateScrollBy take the engine's AnimationSpec.
    api(project(":swing-ui-animation-core"))
    api(project(":swing-ui-core"))
    compileOnly(libs.jetbrainsAnnotations)
    fixturesImplementation(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
    testFixturesApi(project(":swing-ui-components")) {
        capabilities { requireFeature("fixtures") }
    }
    testFixturesApi(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
    testFixturesImplementation(kotlin("test"))
    testFixturesImplementation(project(":swing-ui-test"))
    testFixturesImplementation(testFixtures(project(":swing-ui-core")))
    testImplementation(libs.androidxTracing)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-components")) {
        capabilities { requireFeature("fixtures") }
    }
    testImplementation(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
    testImplementation(testFixtures(project(":swing-ui-core")))
    testImplementation(project(":swing-ui-test"))
}

// The fixtures stand in for Swing services while this project's own tests run; they are not part of what
// the library publishes.
(components["java"] as AdhocComponentWithVariants).run {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

jacocoCoverage {
    lineMinimum.set("0.95".toBigDecimal())
    branchMinimum.set("0.80".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui-components")
            description.set(
                "Swing widget catalog and widget-specific modifiers for compose-swing-ui.",
            )
        }
    }
}
