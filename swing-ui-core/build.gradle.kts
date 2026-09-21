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
    api(libs.androidxAnnotation)
    // fastDistinctBy inlines MutableScatterSet into its callers.
    api(libs.androidxCollection)
    // Mounted content reads LocalLifecycleOwner, so the Lifecycle vocabulary belongs on consumers' compile classpath.
    api(libs.androidxLifecycleRuntimeCompose)
    api(libs.composeRuntime)
    api(libs.kotlinxCoroutinesCore)
    implementation(libs.androidxTracing)
    implementation(libs.kotlinxCoroutinesSwing)
    compileOnly(libs.jetbrainsAnnotations)
    // Some core fixtures compose catalog widgets as the hosts of the modifiers they exercise.
    fixturesImplementation(project(":swing-ui-components"))
    testFixturesApi(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
    testFixturesImplementation(libs.androidxTracing)
    testFixturesImplementation(libs.mockk)
    testFixturesImplementation(kotlin("test-junit5"))
    testFixturesImplementation(project(":swing-ui-components"))
    testFixturesImplementation(project(":swing-ui-test"))
    // The compiler-test-harness convention supplies the compiler at test runtime.
    testFixturesCompileOnly(kotlin("compiler-embeddable"))
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-components"))
    testImplementation(testFixtures(project(":swing-ui-components")))
    testImplementation(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
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
            name.set("compose-swing-ui-core")
            description.set(
                "Core runtime for compose-swing-ui: nodes, composition, windows, layout, generic modifiers and styles.",
            )
        }
    }
}
