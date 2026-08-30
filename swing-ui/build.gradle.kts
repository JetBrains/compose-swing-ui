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
val composeCompilerPluginClasspath =
    configurations.create("composeCompilerPluginClasspath") {
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
    implementation(libs.androidxTracing)
    // @MagicConstant typed-constant and @Nls human-readable-string annotations. CLASS/IDE-only:
    // compileOnly so they warn consumers in-IDE across the jar boundary without leaking
    // org.jetbrains:annotations to the published runtime.
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
    lineMinimum.set("0.95".toBigDecimal())
    branchMinimum.set("0.85".toBigDecimal())
}

// The tag carried by org.jetbrains.compose.swing.ExclusiveWindowSystem, whose KDoc says what the split
// separates and why. Splitting on a tag rather than on package or name keeps the requirement stated at
// the test that has it.
val exclusiveWindowSystemTag = "exclusive-window-system"

tasks.test {
    useJUnitPlatform { excludeTags(exclusiveWindowSystemTag) }
    // These tests show real windows too, but assert nothing about which window the window system is
    // attending to, so several can run at once. The parallelism has to come from forked JVMs: every test
    // body runs on the event dispatch thread, one thread per JVM, so running them as concurrent threads
    // of a single JVM would serialize on that thread anyway. Half the cores leaves the Gradle daemon and
    // the tasks running alongside room of their own.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

val exclusiveWindowSystemTest =
    tasks.register<Test>("exclusiveWindowSystemTest") {
        description = "Runs the tests that need the window system's undivided attention, one at a time."
        group = LifecycleBasePlugin.VERIFICATION_GROUP
        val testSourceSet = sourceSets.test.get()
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        useJUnitPlatform { includeTags(exclusiveWindowSystemTag) }
        // Keeping the two apart is the window-system lock's doing; this only settles the order, so the fast
        // parallel task is done showing windows before this one starts asserting on which window is focused.
        shouldRunAfter(tasks.test)
    }

tasks.check {
    dependsOn(exclusiveWindowSystemTest)
}

tasks.withType<Test>().configureEach {
    // The tag the tests are split on, handed to the tests themselves so one of them can assert that the
    // two spellings still agree. A tag only this file knew would silently stop matching any test: the
    // task filtering on it would run nothing, which passes.
    systemProperty("compose.swing.test.exclusiveWindowSystemTag", exclusiveWindowSystemTag)
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
