// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.rules.androidx.ModuleBoundary
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestrictedApiModuleLookupTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("lookups")
    fun `selects the binary roots holding the class and relates them to the module of the reference`(
        label: String,
        roots: List<String>,
        expectedRoots: List<String>,
        expectedWithinModule: Boolean,
        @TempDir workspace: Path,
    ) {
        val module = workspace.resolve("app")
        module.createDirectories()
        module.resolve("build.gradle.kts").createFile()
        jar(workspace.resolve("app/build/libs/app.jar"), CLASS_FILE)
        jar(workspace.resolve("cache/library.jar"), CLASS_FILE)
        jar(workspace.resolve("app/build/libs/unrelated.jar"), "test/pkg/Other.class")
        val classDirectoryEntry = workspace.resolve("app/build/classes/main/$CLASS_FILE")
        classDirectoryEntry.parent.createDirectories()
        classDirectoryEntry.writeBytes(byteArrayOf())

        val declaringRoots = ModuleBoundary.declaringRoots(roots.map(workspace::resolve), CLASS_FILE)
        val reference = workspace.resolve("app/src/main/kotlin/Use.kt")
        val withinModule = ModuleBoundary.isWithinModule(reference, declaringRoots)

        assertEquals(expectedRoots.map(workspace::resolve), declaringRoots, label)
        assertEquals(expectedWithinModule, withinModule, label)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["build.gradle.kts", "build.gradle"])
    fun `finds the module root by either Gradle build script`(
        buildScript: String,
        @TempDir workspace: Path,
    ) {
        workspace.resolve("app").createDirectories()
        workspace.resolve("app/$buildScript").createFile()
        val library = workspace.resolve("app/build/libs/app.jar")
        jar(library, CLASS_FILE)

        val reference = workspace.resolve("app/src/main/kotlin/Use.kt")

        assertTrue(ModuleBoundary.isWithinModule(reference, listOf(library)))
    }

    private fun jar(
        path: Path,
        entry: String,
    ) {
        path.parent.createDirectories()
        JarOutputStream(path.outputStream()).use { output ->
            output.putNextEntry(JarEntry(entry))
            output.closeEntry()
        }
    }

    companion object {
        private const val CLASS_FILE = "test/pkg/Lib.class"

        @JvmStatic
        fun lookups(): List<Arguments> =
            listOf(
                Arguments.of(
                    "a jar in the build libs of the module holding the reference",
                    listOf("app/build/libs/app.jar"),
                    listOf("app/build/libs/app.jar"),
                    true,
                ),
                Arguments.of(
                    "a jar and a class directory in the build directory of the module",
                    listOf("app/build/libs/app.jar", "app/build/classes/main"),
                    listOf("app/build/libs/app.jar", "app/build/classes/main"),
                    true,
                ),
                Arguments.of(
                    "a jar outside any build directory",
                    listOf("cache/library.jar"),
                    listOf("cache/library.jar"),
                    false,
                ),
                Arguments.of(
                    "one jar of the module and one outside it",
                    listOf("app/build/libs/app.jar", "cache/library.jar"),
                    listOf("app/build/libs/app.jar", "cache/library.jar"),
                    false,
                ),
                Arguments.of(
                    "a jar of the module that does not hold the class",
                    listOf("app/build/libs/unrelated.jar"),
                    emptyList<String>(),
                    false,
                ),
            )
    }
}
