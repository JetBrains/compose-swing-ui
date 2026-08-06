import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
}

kotlin {
    explicitApi()

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
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

// A resolvable, isolated classpath holding ONLY the Compose compiler plugin jar (no transitive deps).
// The in-process compiler harness loads this jar via -Xplugin so it runs the exact same Compose
// @Composable/target inference as the real Gradle build. Kept out of the test compile/runtime classpath
// on purpose: the harness wants the plugin as a standalone jar, not on the classpath.
val composeCompilerPluginClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
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
    // @MagicConstant typed-constant annotations. CLASS/IDE-only: compileOnly so they warn consumers
    // in-IDE across the jar boundary without leaking org.jetbrains:annotations to the published runtime.
    compileOnly(libs.jetbrainsAnnotations)
    testImplementation(kotlin("test"))
    testImplementation(project(":swing-ui-test"))

    // Official embeddable Kotlin compiler, driven in-process (test-only) to assert Compose compiler
    // diagnostics. Aligned to the Kotlin version we compile with by the resolutionStrategy block below.
    testImplementation(kotlin("compiler-embeddable"))

    composeCompilerPluginClasspath(libs.kotlinComposeCompilerPluginEmbeddable) {
        isTransitive = false
    }
}

// Align the embeddable compiler artifacts to the Kotlin version we compile with so the in-process compiler
// and the Compose plugin jar are the same line and load each other cleanly.
val embeddableCompilerArtifacts =
    setOf("kotlin-compiler-embeddable", "kotlin-annotation-processing-embeddable")
configurations.matching { it.name.startsWith("test") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name in embeddableCompilerArtifacts) {
            useVersion(libs.versions.kotlin.get())
        }
    }
}

jacocoCoverage {
    lineMinimum.set("0.90".toBigDecimal())
    branchMinimum.set("0.75".toBigDecimal())
}

tasks.withType<Test>().configureEach {
    // Hand the resolved Compose compiler plugin jar path to the harness. A FileCollection (not the
    // Configuration itself) is captured so the task stays configuration-cache compatible, and it is read
    // lazily inside the provider so configuration of unrelated tasks never forces resolution.
    val pluginClasspath: FileCollection = composeCompilerPluginClasspath
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dcompose.compiler.plugin.classpath=${pluginClasspath.asPath}")
        },
    )
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
