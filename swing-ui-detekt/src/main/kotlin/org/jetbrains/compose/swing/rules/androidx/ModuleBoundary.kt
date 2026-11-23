// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules.androidx

import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import java.io.IOException
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

/** A library's Maven group id and artifact id. */
internal data class MavenCoordinate(
    val groupId: String,
    val artifactId: String,
)

/**
 * The Maven coordinates of the analyzed module and of its project dependencies, which only the build knows.
 * The build writes them into the rule's configuration: `moduleCoordinates` holds the module's `group:name`, and
 * `libraryCoordinates` holds one `<class directory or jar>=group:name` entry per resolved dependency, project or
 * external. Anything else has unknown coordinates, which is upstream's `null`.
 */
internal class LibraryCoordinates(
    val module: MavenCoordinate?,
    private val libraries: Map<Path, MavenCoordinate>,
) {
    /** The coordinates of the library that holds [declaringRoots], or null when none of them is known. */
    fun of(declaringRoots: Collection<Path>): MavenCoordinate? =
        declaringRoots.firstNotNullOfOrNull { root ->
            libraries.entries.firstOrNull { root.startsWith(it.key) }?.value
        }

    companion object {
        fun from(config: ComposeSwingRuleConfig): LibraryCoordinates =
            parse(
                config.valueOrDefault("moduleCoordinates", ""),
                config.valueOrDefault("libraryCoordinates", emptyList()),
            )

        /**
         * The coordinates named by the text of `moduleCoordinates` and the entries of `libraryCoordinates`; an
         * entry that does not name a coordinate is ignored.
         */
        fun parse(
            module: String,
            libraries: List<String>,
        ): LibraryCoordinates =
            LibraryCoordinates(
                module = coordinate(module),
                libraries =
                    libraries
                        .mapNotNull { entry ->
                            coordinate(entry.substringAfterLast('='))?.let {
                                Path.of(entry.substringBeforeLast('=')).toAbsolutePath().normalize() to it
                            }
                        }.toMap(),
            )

        private fun coordinate(text: String): MavenCoordinate? {
            val groupId = text.substringBefore(':')
            val artifactId = text.substringAfter(':', "")
            return if (groupId.isEmpty() || artifactId.isEmpty()) null else MavenCoordinate(groupId, artifactId)
        }
    }
}

/** Finds the compiled roots that hold a declaration and the Maven coordinates a root implies. */
internal object ModuleBoundary {
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

/**
 * The comparisons of androidx's `RestrictToDetector.checkRestrictTo` for the library scopes, each answering the
 * text of the restriction that was not met, or null when it was.
 */
internal object RestrictToLibrary {
    /** `RestrictTo.Scope.LIBRARY_GROUP`: the groups must be equal. */
    fun libraryGroup(
        thisCoordinates: MavenCoordinate?,
        methodCoordinates: MavenCoordinate?,
    ): String? {
        val thisGroup = thisCoordinates?.groupId
        val methodGroup = methodCoordinates?.groupId
        return if (thisGroup != methodGroup && methodGroup != null) {
            val thisGroupDisplayText = thisGroup ?: "<unknown>"
            "from within the same library group (referenced groupId=`$methodGroup` from " +
                "groupId=`$thisGroupDisplayText`)"
        } else {
            null
        }
    }

    /** `RestrictTo.Scope.LIBRARY_GROUP_PREFIX`: the groups must share a prefix. */
    fun libraryGroupPrefix(
        thisCoordinates: MavenCoordinate?,
        methodCoordinates: MavenCoordinate?,
    ): String? {
        val thisGroup = thisCoordinates?.groupId
        val methodGroup = methodCoordinates?.groupId
        return if (methodGroup != null && (thisGroup == null || !sameLibraryGroupPrefix(thisGroup, methodGroup))) {
            val expectedPrefix =
                methodGroup.lastIndexOf('.').let {
                    if (it < 0) "\"\"" else methodGroup.substring(0, it)
                }
            "from within the same library group prefix (referenced " +
                "groupId=`$methodGroup` with prefix $expectedPrefix" +
                "${if (thisGroup != null) " from groupId=`$thisGroup`" else ""})"
        } else {
            null
        }
    }

    /**
     * `RestrictTo.Scope.LIBRARY`: a different group must also be a different artifact. A declaration of the
     * same group is never reported here: upstream reports one that is source of another Gradle project, and
     * detekt analyzes the sources of one module, so every source declaration is that module's own.
     */
    fun library(
        thisCoordinates: MavenCoordinate?,
        methodCoordinates: MavenCoordinate?,
    ): String? {
        val thisGroup = thisCoordinates?.groupId
        val methodGroup = methodCoordinates?.groupId
        return if (thisGroup != methodGroup && methodGroup != null &&
            thisCoordinates?.artifactId != methodCoordinates.artifactId
        ) {
            val name =
                if (methodGroup == "__local_aars__") {
                    "missing Maven coordinate due to repackaging"
                } else {
                    "$methodGroup:${methodCoordinates.artifactId}"
                }
            "from within the same library ($name)"
        } else {
            null
        }
    }

    /**
     * Implements the group prefix equality that is described in the documentation for the
     * `RestrictTo.Scope.LIBRARY_GROUP_PREFIX` enum constant.
     */
    fun sameLibraryGroupPrefix(
        group1: String,
        group2: String,
    ): Boolean {
        val i1 = group1.lastIndexOf('.')
        val i2 = group2.lastIndexOf('.')
        return group1 == group2 ||
            listOf(group1, group2).all { it.startsWith("androidx.") } ||
            (i2 == i1 && i1 != -1 && group1.regionMatches(0, group2, 0, i1))
    }
}
