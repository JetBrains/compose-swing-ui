// Renders the API reference of every published module as one HTML site. Applied to the root project,
// which owns the aggregation the site is built from.
package buildsrc.convention

import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

plugins {
    id("org.jetbrains.dokka")
}

// Precompiled script plugins do not get generated `libs` accessors, so resolve the catalog directly.
private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun requiredLibrary(alias: String) =
    libs
        .findLibrary(alias)
        .orElseThrow { IllegalStateException("Missing library '$alias' in gradle/libs.versions.toml") }

dokka {
    moduleName.set(rootProject.name)
    // The versioning plugin stamps the version this site documents into its header, and switches
    // between it and the generated documentation of earlier versions placed in `olderVersionsDir`.
    pluginsConfiguration.versioning {
        version.set(resolvePublishVersion())
    }
}

dependencies {
    dokkaPlugin(requiredLibrary("dokka-versioning-plugin"))

    // Applying the publishing convention is what enrols a module: it brings the Dokka plugin that
    // exposes the module's documentation to the aggregation. Modules without it, the samples among
    // them, expose nothing and stay out of the site.
    subprojects.forEach { module ->
        module.plugins.withId("buildsrc.convention.publishing") { dokka(module) }
    }
}
