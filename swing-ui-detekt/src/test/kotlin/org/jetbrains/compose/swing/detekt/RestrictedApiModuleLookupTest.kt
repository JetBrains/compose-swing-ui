// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.rules.androidx.LibraryCoordinates
import org.jetbrains.compose.swing.rules.androidx.MavenCoordinate
import org.jetbrains.compose.swing.rules.androidx.ModuleBoundary
import org.jetbrains.compose.swing.rules.androidx.RestrictToLibrary
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.io.path.writeBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestrictedApiModuleLookupTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("lookups")
    fun `selects the binary roots holding the class`(
        label: String,
        roots: List<String>,
        expectedRoots: List<String>,
        @TempDir workspace: Path,
    ) {
        jar(workspace.resolve("app/build/libs/app.jar"), CLASS_FILE)
        jar(workspace.resolve("cache/library.jar"), CLASS_FILE)
        jar(workspace.resolve("app/build/libs/unrelated.jar"), "test/pkg/Other.class")
        val classDirectoryEntry = workspace.resolve("app/build/classes/main/$CLASS_FILE")
        classDirectoryEntry.parent.createDirectories()
        classDirectoryEntry.writeBytes(byteArrayOf())

        val declaringRoots = ModuleBoundary.declaringRoots(roots.map(workspace::resolve), CLASS_FILE)

        assertEquals(expectedRoots.map(workspace::resolve), declaringRoots, label)
    }

    @Test
    fun `places a root by the dependencies the build supplied`() {
        val external = Path.of("/home/.m2/repository/org/c/lib/1.0/lib-1.0.jar")
        val coordinates =
            LibraryCoordinates.parse(
                "org.a:app",
                listOf("/ws/core/build/classes=org.a:core", "/ws/other.jar=org.b:other", "$external=org.c:lib", "bad"),
            )

        assertEquals(MavenCoordinate("org.a", "app"), coordinates.module)
        assertEquals(
            MavenCoordinate("org.a", "core"),
            coordinates.of(listOf(Path.of("/ws/core/build/classes/kotlin/main"))),
        )
        assertEquals(MavenCoordinate("org.b", "other"), coordinates.of(listOf(Path.of("/ws/other.jar"))))
        assertEquals(MavenCoordinate("org.c", "lib"), coordinates.of(listOf(external)))
        assertNull(coordinates.of(listOf(Path.of("/home/.gradle/caches/modules-2/files-2.1/org.d/lib/1.0/h/lib.jar"))))
        assertNull(coordinates.of(listOf(Path.of("/ws/unknown.jar"))))
        assertNull(coordinates.of(emptyList()))
    }

    @Test
    fun `leaves every coordinate unknown when the build supplied none`() {
        val coordinates = LibraryCoordinates.parse("", emptyList())

        assertNull(coordinates.module)
        assertNull(coordinates.of(listOf(Path.of("/ws/core/build/classes"))))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupPrefixes")
    fun `compares group prefixes as upstream does`(
        label: String,
        group1: String,
        group2: String,
        expected: Boolean,
    ) {
        assertEquals(expected, RestrictToLibrary.sameLibraryGroupPrefix(group1, group2), label)
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
                    "a jar of the module",
                    listOf("app/build/libs/app.jar"),
                    listOf("app/build/libs/app.jar"),
                ),
                Arguments.of(
                    "a jar and a class directory",
                    listOf("app/build/libs/app.jar", "app/build/classes/main"),
                    listOf("app/build/libs/app.jar", "app/build/classes/main"),
                ),
                Arguments.of("a jar elsewhere", listOf("cache/library.jar"), listOf("cache/library.jar")),
                Arguments.of(
                    "a jar that does not hold the class",
                    listOf("app/build/libs/unrelated.jar"),
                    emptyList<String>(),
                ),
            )

        @JvmStatic
        fun groupPrefixes(): List<Arguments> =
            listOf(
                Arguments.of("equal groups", "org.a", "org.a", true),
                Arguments.of("groups of one prefix", "org.a", "org.b", true),
                Arguments.of("androidx groups of different depth", "androidx.compose", "androidx.compose.ui.x", true),
                Arguments.of("groups of different prefixes", "org.a", "com.a", false),
                Arguments.of("groups of different depth", "org.a.b", "org.a", false),
                Arguments.of("groups without a dot", "a", "b", false),
            )
    }
}
