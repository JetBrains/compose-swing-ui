// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
plugins {
    `java-library`
    `jvm-test-suite`
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.benchmark")
}

dependencies {
    api(project(":swing-ui"))
    api(project(":swing-ui")) {
        capabilities { requireFeature("fixtures") }
    }
    api(libs.composeRuntime)
    api(kotlin("test-junit5"))
    implementation(libs.androidxTracing)
    implementation(testFixtures(project(":swing-ui")))

    testImplementation(libs.kotlinxCoroutinesSwing)
}

tasks.withType<Test>().configureEach {
    // The heap census reads the serial collector's full collections.
    jvmArgs("-XX:+UseSerialGC")
}

// The memory suite runs the fixtures of every library module, which the harness itself does not depend on.
testing.suites.register<JvmTestSuite>("memoryTest") {
    useJUnitJupiter()
    dependencies {
        implementation(project())
        listOf(
            "swing-ui",
        ).forEach { module ->
            implementation(project(":$module")) {
                capabilities { requireFeature("fixtures") }
            }
        }
        implementation(testFixtures(project(":swing-ui")))
        implementation(libs.kotlinxCoroutinesSwing)
    }
    targets.configureEach {
        testTask.configure {
            minHeapSize = "1g"
            maxHeapSize = "1g"
            // Thread-local allocation buffers leave filler arrays that the census counts as live. A full collection
            // keeps softly reachable objects, and the JDK's soft painter image caches hold the painted component and
            // with it the whole disposed tree, so the JVM clears each soft reference not used since the previous
            // collection.
            jvmArgs("-XX:-UseTLAB", "-XX:SoftRefLRUPolicyMSPerMB=0")
            systemProperty("user.timezone", "UTC")
            providers.gradleProperty("benchmarkFixtures").orNull?.let { systemProperty("benchmark.fixtures", it) }
            plugins.withId("jacoco") {
                extensions.getByType<JacocoTaskExtension>().isEnabled = false
            }
        }
    }
}

kotlin {
    sourceSets.configureEach {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.InternalSwingUiApi")
    }
    target.compilations.named("memoryTest") {
        associateWith(target.compilations.getByName("main"))
    }
}
