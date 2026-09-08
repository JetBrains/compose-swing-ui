import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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
    abiValidation {}
}

dependencies {
    // withFrameNanos / @Composable / snapshot state appear in public signatures.
    api(libs.composeRuntime)
    // Range / nullability annotations (@FloatRange, @IntRange, @RestrictTo) on the vendored engine's
    // public declarations, mirroring upstream animation-core's api dependency.
    api(libs.androidxAnnotation)
    implementation(libs.androidxCollection)
    // The Swing dispatcher and frame clock live in :swing-ui, which also owns the InfiniteAnimationPolicy
    // this engine honors, as upstream animation-core takes its policy type from compose-ui.
    api(project(":swing-ui"))
    implementation(libs.kotlinxCoroutinesCore)

    // The ported Transition suite hosts its composition in the shipped harness rather than a
    // second one. :swing-ui-test takes this module as a testImplementation in turn, which is not a
    // cycle: each test compilation needs only the other's main jar, and neither main compilation
    // depends on the other.
    testImplementation(project(":swing-ui-test"))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

// Upstream's tests use these markers without opting in because they live in the declaring module.
tasks.named<KotlinCompile>("compileTestKotlin") {
    compilerOptions.optIn.addAll(
        "org.jetbrains.compose.swing.animation.core.ExperimentalTransitionApi",
        "org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi",
        "org.jetbrains.compose.swing.animation.core.ExperimentalAnimationSpecApi",
        "org.jetbrains.compose.swing.animation.core.InternalAnimationApi",
    )
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
