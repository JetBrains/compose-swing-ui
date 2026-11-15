// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.swing.plaf.metal.MetalLookAndFeel

// The harness calls JDK 21 APIs, so its class files target JDK 21 instead of the library's JVM 11.
tasks.withType<KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(JvmTarget.JVM_21) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

private val swingProperties =
    mapOf(
        "java.awt.headless" to "true",
        "sun.java2d.uiScale" to "1",
        "swing.defaultlaf" to MetalLookAndFeel::class.java.name,
    )

tasks.withType<Test>().configureEach { systemProperties(swingProperties) }
tasks.withType<JavaExec>().configureEach { systemProperties(swingProperties) }
