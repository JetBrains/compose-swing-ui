package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        // JVM default methods without DefaultImpls bridges. Bridges only serve binaries compiled
        // against a previously published DefaultImpls-bearing release - none exist before the first
        // release - and removing them later would be binary-breaking, so bridge-less is the one-way
        // door taken now. The vendored animation fork also requires this mode: upstream's
        // @JvmDefaultWithCompatibility annotations (re-adding bridges per interface where androidx
        // promises them) are only accepted by the compiler under it.
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        // Published binaries and metadata stay consumable from Kotlin 2.1 toolchains, independent of
        // the (newer) Kotlin the build itself runs on.
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

// The reports name which composables are skippable and which parameters are unstable.
composeCompiler {
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // A test driving Swing can wait on the event dispatch thread for a state the thread it is waiting on
    // will never reach. Failing such a test on a deadline reports the hang where it happened, instead of
    // leaving the build to be killed with no result at all. This deadline sits above
    // runComposeSwingTest's own 60s timeout so that gate's uncompleted-coroutine dump - the more
    // informative failure - is what a test written against the harness actually sees; this one is the
    // backstop for a real EDT deadlock, which blocks the thread rather than leaving a coroutine to dump.
    // A case that legitimately needs longer carries its own `Timeout` annotation; the slowest case in
    // the suite today takes about five seconds.
    systemProperty("junit.jupiter.execution.timeout.testable.method.default", "90s")
    // The deadline is only measured after a test method returns unless the method runs on a thread of its
    // own, and a deadlocked method never returns. Running each on its own thread is what lets the deadline
    // interrupt one.
    systemProperty("junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD")
}
