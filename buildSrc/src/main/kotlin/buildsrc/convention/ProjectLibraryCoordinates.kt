// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package buildsrc.convention

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.provider.Provider

/**
 * The `group:name` of each resolved dependency, project or external, on this resolvable configuration, by the
 * absolute path of every class directory or jar the dependency contributes.
 */
public fun Configuration.projectLibraryCoordinates(): Provider<Map<String, String>> {
    val artifacts =
        incoming.artifacts.resolvedArtifacts
    return artifacts.zip(incoming.resolutionResult.rootComponent) { resolved, root ->
        val coordinates = coordinatesByComponent(root)
        buildMap {
            resolved.forEach { artifact ->
                coordinates[artifact.id.componentIdentifier]?.let { put(artifact.file.absolutePath, it) }
            }
        }
    }
}

private fun coordinatesByComponent(root: ResolvedComponentResult): Map<ComponentIdentifier, String> {
    val visited = LinkedHashMap<ComponentIdentifier, ResolvedComponentResult>()
    val pending = ArrayDeque(listOf(root))
    while (pending.isNotEmpty()) {
        val component = pending.removeFirst()
        if (visited.put(component.id, component) == null) {
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { pending.add(it.selected) }
        }
    }
    return visited.values
        .mapNotNull { component -> component.moduleVersion?.let { component.id to "${it.group}:${it.name}" } }
        .toMap()
}
