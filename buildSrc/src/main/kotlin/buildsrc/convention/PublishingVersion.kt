// Version derivation shared by the module publications and the API reference, so the version the
// documentation carries and the version the artifacts carry come from one rule.
package buildsrc.convention

import org.gradle.api.Project

private const val DEFAULT_VERSION: String = "0.1.0-SNAPSHOT"

/**
 * The version published artifacts and the API reference carry.
 *
 * Defaults to `0.1.0-SNAPSHOT`. `-Pversion=X.Y.Z` overrides it, and `-PversionSuffix=alpha.1`
 * appends a pre-release suffix.
 */
public fun Project.resolvePublishVersion(): String {
    val base = providers.gradleProperty("version").orNull?.takeIf { it.isNotBlank() } ?: DEFAULT_VERSION
    val suffix = providers.gradleProperty("versionSuffix").orNull?.takeIf { it.isNotBlank() }
    return if (suffix == null) base else "$base-$suffix"
}
