// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Composable fixtures pair each public composable with the raw Swing it must match. Tests, the benchmark
// suites and stories reach them as the `fixtures` feature of the library module, so they see the library's
// main code and nothing from the test classpath. The feature is never published.

plugins {
    `java-library`
}

private val fixtures: SourceSet = sourceSets.create("fixtures")

java.registerFeature("fixtures") {
    usingSourceSet(fixtures)
}

// The Kotlin plugin's explicitApi() covers only the main compilation.
tasks.withType<KotlinCompile>().matching { it.name == "compileFixturesKotlin" }.configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
}

dependencies {
    add(fixtures.implementationConfigurationName, project)
}

(components["java"] as AdhocComponentWithVariants).run {
    withVariantsFromConfiguration(configurations[fixtures.apiElementsConfigurationName]) { skip() }
    withVariantsFromConfiguration(configurations[fixtures.runtimeElementsConfigurationName]) { skip() }
}
