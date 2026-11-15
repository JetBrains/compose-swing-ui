// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/**
 * A Gradle module is one library. The analyzed classpath arrives as a single compiled module, so the
 * module holding the reference file is recognized by where the declaration's own class file lies.
 */
internal object ModuleBoundary {
    /**
     * Whether every one of [declaringRoots] lies under the build directory of the module that holds
     * [referenceFile], the nearest ancestor directory holding a `build.gradle.kts` or a `build.gradle`.
     * The build directory is taken to be `build` under that module root: a custom `buildDirectory` cannot be
     * detected from a path, so a module that relocates it is not recognized.
     */
    fun isWithinModule(
        referenceFile: Path,
        declaringRoots: Collection<Path>,
    ): Boolean {
        val markers = listOf("build.gradle.kts", "build.gradle")
        val moduleRoot =
            generateSequence(referenceFile.parent) { it.parent }
                .firstOrNull { root -> markers.any { root.resolve(it).isRegularFile() } }
        val buildDirectory = moduleRoot?.resolve("build")
        return buildDirectory != null && declaringRoots.isNotEmpty() &&
            declaringRoots.all { it.startsWith(buildDirectory) }
    }

    /**
     * The class directories and JAR files among [binaryRoots] that hold the compiled [classFile], a path
     * relative to a root.
     */
    fun declaringRoots(
        binaryRoots: Collection<Path>,
        classFile: String,
    ): List<Path> = binaryRoots.filter { holds(it, classFile) }

    private fun holds(
        root: Path,
        classFile: String,
    ): Boolean =
        when {
            root.isDirectory() -> root.resolve(classFile).exists()
            root.isRegularFile() -> jarHolds(root, classFile)
            else -> false
        }

    private fun jarHolds(
        jar: Path,
        classFile: String,
    ): Boolean =
        try {
            ZipFile(jar.toFile()).use { it.getEntry(classFile) != null }
        } catch (ignored: IOException) {
            false
        }
}
