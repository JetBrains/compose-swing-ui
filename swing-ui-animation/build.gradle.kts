import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
}

// A verbatim AOSP fork of Compose animation-core, so it is intentionally exempt from the shared
// ktlint/detekt/lint quality gates; compile, test, and ABI validation still run via kotlin-jvm + abiValidation.

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

dependencies {
    // withFrameNanos / @Composable / snapshot state appear in public signatures.
    api(libs.composeRuntime)
    // Range / nullability annotations (@FloatRange, @IntRange, @RestrictTo) on the vendored engine's
    // public declarations, mirroring upstream animation-core's api dependency.
    api(libs.androidxAnnotation)
    implementation(libs.androidxCollection)
    // The Swing dispatcher and frame clock live in :swing-ui; the engine only needs the common
    // coroutine primitives.
    implementation(libs.kotlinxCoroutinesCore)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

// Regression ratchet: this module's floor tracks the vendored engine's achieved ratio to within about
// a point on each axis - tighter than a module with its own tests would want, because an upstream
// re-sync moves the achieved ratio and the floor together. Re-baseline the floor alongside the
// vendored code on every re-sync rather than chasing noise between them.
jacocoCoverage {
    lineMinimum.set("0.55".toBigDecimal())
    branchMinimum.set("0.40".toBigDecimal())
}

publishing {
    publications.named<MavenPublication>("maven") {
        pom {
            name.set("compose-swing-ui :: swing-ui-animation")
            description.set(
                "Vendored Compose animation-core engine (animate*AsState, Animatable, Transition, " +
                    "easing, spring/tween) for the Compose-over-Swing runtime.",
            )
        }
    }
}
