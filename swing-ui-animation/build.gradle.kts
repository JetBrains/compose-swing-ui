// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.composable-fixtures")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
    id("buildsrc.convention.window-system-lock")
    id("buildsrc.convention.exclusive-window-system-tests")
}

kotlin {
    explicitApi()
    compilerOptions.optIn.addAll(
        "org.jetbrains.compose.swing.animation.ExperimentalAnimationApi",
        "org.jetbrains.compose.swing.animation.core.ExperimentalAnimationSpecApi",
        "org.jetbrains.compose.swing.animation.core.ExperimentalTransitionApi",
        "org.jetbrains.compose.swing.animation.core.InternalAnimationApi",
        "org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi",
    )

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {}
}

dependencies {
    api(libs.androidxAnnotation)
    api(libs.composeRuntime)
    api(project(":swing-ui-animation-core"))
    api(project(":swing-ui-core"))
    api(project(":swing-ui-foundation"))
    implementation(libs.androidxCollection)
    implementation(libs.kotlinxCoroutinesCore)
    fixturesImplementation(project(":swing-ui-components"))
    fixturesImplementation(project(":swing-ui-core")) {
        capabilities { requireFeature("fixtures") }
    }
    fixturesImplementation(project(":swing-ui-foundation")) {
        capabilities { requireFeature("fixtures") }
    }
    testImplementation(libs.composeRuntimeSaveable)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-animation")) {
        capabilities { requireFeature("fixtures") }
    }
    testImplementation(testFixtures(project(":swing-ui-animation-core")))
    testImplementation(project(":swing-ui-components"))
    testImplementation(testFixtures(project(":swing-ui-core")))
    testImplementation(testFixtures(project(":swing-ui-foundation")))
    testImplementation(project(":swing-ui-test"))
}

// Regression ratchet over code forked from androidx animation: re-baseline the floor with every upstream re-sync.
jacocoCoverage {
    lineMinimum.set("0.90".toBigDecimal())
    branchMinimum.set("0.85".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui :: swing-ui-animation")
            description.set(
                "Animated Swing containers for compose-swing-ui: AnimatedVisibility, AnimatedContent, Crossfade " +
                    "and animateContentSize, built on the animation-core engine.",
            )
        }
    }
}
