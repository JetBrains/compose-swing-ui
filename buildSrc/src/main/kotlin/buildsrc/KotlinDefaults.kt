package buildsrc

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val publishedJvmTarget = JvmTarget.JVM_11

/**
 * The compiler and test settings every module in this repository is built under.
 *
 * A convention plugin cannot apply another from the same `buildSrc`, so a caller takes these settings
 * by calling this rather than by applying a plugin.
 */
public fun Project.kotlinDefaults() {
    extensions.configure<KotlinJvmProjectExtension>("kotlin") {
        compilerOptions {
            allWarningsAsErrors.set(true)
            // JVM default methods without DefaultImpls bridges. Bridges only serve binaries compiled
            // against a previously published DefaultImpls-bearing release - none exist before the first
            // release - and removing them later would be binary-breaking, so bridge-less is the one-way
            // door taken now. The vendored animation fork also requires this mode: upstream's
            // @JvmDefaultWithCompatibility annotations (re-adding bridges per interface where androidx
            // promises them) are only accepted by the compiler under it.
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            // Published binaries and metadata stay consumable from Kotlin 2.2 toolchains, independent of
            // the (newer) Kotlin the build itself runs on. 2.2 is the floor the compiler allows: it
            // rejects 2.1 as deprecated, and this build turns that warning into an error.
            languageVersion.set(KotlinVersion.KOTLIN_2_2)
            apiVersion.set(KotlinVersion.KOTLIN_2_2)
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(publishedJvmTarget)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(publishedJvmTarget.target.toInt())
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
        // The screenshot harness reads its switches, such as the one putting it in record mode, from system
        // properties of the JVM it runs in. Requesting one on the Gradle command line sets it on the daemon
        // instead, so the whole prefixed family is forwarded to the forked test JVM. Forwarding by prefix
        // leaves the individual switch names owned by the harness that defines them. The values are registered
        // as a task input, so changing one reruns the tests.
        val screenshotProperties = providers.systemPropertiesPrefixedBy("SCREENSHOT_TEST_")
        inputs.property("screenshotTestProperties", screenshotProperties)
        jvmArgumentProviders.add(
            CommandLineArgumentProvider {
                screenshotProperties.get().map { (name, value) -> "-D$name=$value" }
            },
        )
    }
}
