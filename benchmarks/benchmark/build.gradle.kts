// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.gradle.jvm.tasks.Jar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.kotlin-quality")
    id("buildsrc.convention.benchmark")
    alias(libs.plugins.kotlinxBenchmark)
}

dependencies {
    implementation(project(":benchmarks:benchmark-utils"))
    implementation(testFixtures(project(":swing-ui")))
    implementation(libs.kotlinxBenchmarkRuntime)
    listOf(
        "swing-ui",
        "swing-ui-foundation",
        "swing-ui-animation",
    ).forEach { module ->
        implementation(project(":$module")) {
            capabilities { requireFeature("fixtures") }
        }
    }
    testImplementation(kotlin("test"))
}

kotlin {
    sourceSets.named("test") {
        languageSettings.optIn("org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi")
    }
}

benchmark {
    targets { register("main") }
}

val benchmarkIds = layout.buildDirectory.file("benchmark-ids.txt")
val benchmarkParameters =
    tasks.register<JavaExec>("benchmarkParameters") {
        description = "Lists the supported fixture changes and arms."
        group = "benchmark"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("org.jetbrains.compose.swing.benchmark.BenchmarkParametersKt")
        val idsFile = benchmarkIds
        outputs.file(idsFile)
        argumentProviders.add(CommandLineArgumentProvider { listOf(idsFile.get().asFile.absolutePath) })
    }

val jmhArgs = providers.gradleProperty("jmhArgs").orElse("")
tasks.register<JavaExec>("run") {
    description = "Runs stock JMH over every supported fixture change and arm; accepts -PjmhArgs."
    group = "benchmark"
    val benchmarkJar = tasks.named<Jar>("mainBenchmarkJar")
    dependsOn(benchmarkParameters, benchmarkJar)
    classpath = files(benchmarkJar.flatMap { it.archiveFile }) + sourceSets.main.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    val resultFile = layout.buildDirectory.file("results/jmh.json")
    val parameters = providers.fileContents(benchmarkIds).asText
    val options = jmhArgs
    doFirst {
        resultFile
            .get()
            .asFile.parentFile
            .mkdirs()
        if ("fixtureChange=" !in options.get()) {
            args("-p", "fixtureChange=${parameters.get().lineSequence().filter { it.isNotBlank() }.joinToString(",")}")
        }
        args("-rf", "json", "-rff", resultFile.get().asFile.absolutePath)
        args(
            options
                .get()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() },
        )
    }
}
