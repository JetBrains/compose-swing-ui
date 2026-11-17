// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import dev.detekt.gradle.Detekt
import org.gradle.api.file.FileTreeElement
import org.gradle.api.specs.Spec
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
}

// `org.jetbrains.compose.swing.animation.core` in the main and test source sets is upstream-derived Compose
// animation-core with documented adoption edits, kept in upstream's format so re-sync diffs stay readable.
// Detekt skips it there, and ktlint skips the package in `.editorconfig`, which reaches an editor as well as
// the build. Compile, test, ABI validation and coverage cover it. The fixtures and testFixtures source sets are
// written here and stay under detekt.
tasks.withType<Detekt>().configureEach {
    exclude(
        Spec<FileTreeElement> { element ->
            val path = element.file.invariantSeparatorsPath
            "/animation/core/" in path && ("/src/main/" in path || "/src/test/" in path)
        },
    )
}

kotlin {
    explicitApi()
    compilerOptions.optIn.addAll(
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
    api(libs.kotlinxCoroutinesCore)
    implementation(libs.androidxCollection)
    // The Swing dispatcher, frame clock and the InfiniteAnimationPolicy the engine honors live in :swing-ui-core.
    implementation(project(":swing-ui"))
    fixturesImplementation(project(":swing-ui"))
    fixturesImplementation(project(":swing-ui")) {
        capabilities { requireFeature("fixtures") }
    }
    testFixturesApi(project(":swing-ui-test"))
    testImplementation(libs.composeRuntimeSaveable)
    testImplementation(libs.junitJupiterParams)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.mockk)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-animation"))
    testImplementation(project(":swing-ui-animation-core")) {
        capabilities { requireFeature("fixtures") }
    }
    testImplementation(project(":swing-ui"))
    testImplementation(testFixtures(project(":swing-ui")))
    // :swing-ui-test takes this module as a testImplementation in turn; not a cycle, as only the test jars meet.
    testImplementation(project(":swing-ui-test"))
}

// The fixtures stand in for Swing services while this project's own tests run; they are not part of what
// the library publishes.
(components["java"] as AdhocComponentWithVariants).run {
    withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
    withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
}

// Regression ratchet: this module's floor tracks the vendored engine's achieved ratio to within about
// a point on each axis - tighter than a module with its own tests would want, because an upstream
// re-sync moves the achieved ratio and the floor together. Re-baseline the floor alongside the
// vendored code on every re-sync rather than chasing noise between them.
jacocoCoverage {
    lineMinimum.set("0.85".toBigDecimal())
    branchMinimum.set("0.70".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui :: swing-ui-animation-core")
            description.set(
                "Compose animation engine for compose-swing-ui, vendored from animation-core " +
                    "(animate*AsState, Animatable, Transition, easing, spring/tween).",
            )
        }
    }
}
