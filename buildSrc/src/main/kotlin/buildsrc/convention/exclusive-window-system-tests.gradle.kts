package buildsrc.convention

plugins {
    java
}

// The tag carried by org.jetbrains.compose.swing.ExclusiveWindowSystem, whose KDoc says what the split
// separates and why. Splitting on a tag rather than on package or name keeps the requirement stated at
// the test that has it.
private val exclusiveWindowSystemTag = "exclusive-window-system"

tasks.test {
    useJUnitPlatform { excludeTags(exclusiveWindowSystemTag) }
    // These tests show real windows too, but assert nothing about which window the window system is
    // attending to, so several can run at once. The parallelism has to come from forked JVMs: every test
    // body runs on the event dispatch thread, one thread per JVM, so running them as concurrent threads
    // of a single JVM would serialize on that thread anyway. Half the cores leaves the Gradle daemon and
    // the tasks running alongside room of their own.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}

private val exclusiveWindowSystemTest =
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
