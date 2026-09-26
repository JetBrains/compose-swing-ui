import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    `java-test-fixtures`
    id("buildsrc.convention.kotlin-jvm")
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
    }
}

dependencies {
    api(libs.composeRuntime)
    // Mounted content reads its LifecycleOwner through androidx.lifecycle.compose.LocalLifecycleOwner,
    // so the Lifecycle vocabulary belongs on consumers' compile classpath.
    api(libs.androidxLifecycleRuntimeCompose)
    // DisposableHandle appears in public signatures (setContent returns it), so the common coroutine
    // types must be on consumers' compile classpath; the Swing dispatcher stays an implementation detail.
    api(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCoroutinesSwing)
    implementation(libs.androidxTracing)
    // @MagicConstant typed-constant and @Nls human-readable-string annotations. CLASS/IDE-only:
    // compileOnly so they warn consumers in-IDE across the jar boundary without leaking
    // org.jetbrains:annotations to the published runtime.
    compileOnly(libs.jetbrainsAnnotations)
    // The JUnit 5 flavor, whose Tag the ExclusiveWindowSystem annotation carries.
    testFixturesImplementation(kotlin("test-junit5"))
    testFixturesImplementation(project(":swing-ui-test"))
    testImplementation(kotlin("test"))
    testImplementation(libs.mockk)
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
            name.set("compose-swing-ui")
            description.set(
                "Compose runtime over Swing: declarative composable wrappers and modifiers for Swing components.",
            )
        }
    }
}
