import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    `java-test-fixtures`
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.publishing")
    id("buildsrc.convention.jacoco-coverage")
    id("buildsrc.convention.window-system-lock")
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
    testFixturesImplementation(kotlin("test"))
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
