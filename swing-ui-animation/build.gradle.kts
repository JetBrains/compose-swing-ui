import dev.detekt.gradle.Detekt
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
}

// This module holds two bodies of code. `org.jetbrains.compose.swing.animation` is hand-written project
// code under the full quality gates. `org.jetbrains.compose.swing.animation.core` is upstream-derived
// Compose animation-core with documented adoption edits, kept in upstream's format so re-sync diffs stay
// readable. Each source-quality gate excludes that package where configured: detekt below, Android Lint
// in the lint.xml beside the engine, and ktlint in `.editorconfig`, which reaches an editor as well as the
// build. Compile, test, ABI validation and coverage cover both bodies.
tasks.withType<Detekt>().configureEach {
    exclude("**/animation/core/**")
}

kotlin {
    explicitApi()
    compilerOptions.optIn.add("org.jetbrains.compose.swing.animation.ExperimentalAnimationApi")

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
    // The Swing dispatcher and frame clock live in :swing-ui, which also owns the
    // InfiniteAnimationPolicy this engine honors - as upstream animation-core takes its policy type
    // from compose-ui. SwingModifier and Alignment appear in the animated containers' signatures.
    api(project(":swing-ui"))
    api(project(":swing-ui-foundation"))
    implementation(libs.kotlinxCoroutinesCore)

    // The ported Transition suite hosts its composition in the shipped harness rather than a
    // second one. :swing-ui-test takes this module as a testImplementation in turn, which is not a
    // cycle: each test compilation needs only the other's main jar, and neither main compilation
    // depends on the other.
    testImplementation(project(":swing-ui-test"))
    testImplementation(testFixtures(project(":swing-ui")))
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.composeRuntimeSaveable)
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
                "Compose animation APIs and animated Swing containers, backed by the vendored " +
                    "animation-core engine (animate*AsState, Animatable, Transition, easing, spring/tween).",
            )
        }
    }
}
