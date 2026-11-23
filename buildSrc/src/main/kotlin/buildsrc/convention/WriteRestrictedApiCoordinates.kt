// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Writes the Maven coordinates detekt's `RestrictedApi` rule compares, in the rule's own configuration keys: the
 * analyzed module's `group:name`, and the `group:name` of every project dependency by the class directory or jar
 * it contributes to the classpath. The rule cannot read Gradle's model, so the build hands it this file.
 */
public abstract class WriteRestrictedApiCoordinates : DefaultTask() {
    /** The analyzed module's `group:name`. */
    @get:Input
    public abstract val moduleCoordinates: Property<String>

    /** The `group:name` of each project dependency, by the absolute path of a class directory or jar it provides. */
    @get:Input
    public abstract val libraryCoordinates: MapProperty<String, String>

    /** The detekt configuration file written. */
    @get:OutputFile
    public abstract val configFile: RegularFileProperty

    /**
     * Writes [configFile] with [moduleCoordinates] and [libraryCoordinates] in the rule's configuration keys,
     * libraries sorted by path, so equal inputs give equal bytes.
     */
    @TaskAction
    public fun write() {
        val libraries =
            libraryCoordinates.get().entries.sortedBy { it.key }.joinToString("\n") { (path, coordinates) ->
                "      - ${quoted("$path=$coordinates")}"
            }
        configFile.get().asFile.writeText(
            buildString {
                appendLine("androidx-annotations:")
                appendLine("  RestrictedApi:")
                appendLine("    moduleCoordinates: ${quoted(moduleCoordinates.get())}")
                if (libraries.isEmpty()) {
                    appendLine("    libraryCoordinates: []")
                } else {
                    appendLine("    libraryCoordinates:")
                    appendLine(libraries)
                }
            },
        )
    }

    private fun quoted(text: String): String = "'${text.replace("'", "''")}'"
}
