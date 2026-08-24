// Resolves the repository the sources live in, which the POM's url and scm metadata describe. Kept as
// a plain Kotlin file (not inside the .gradle.kts script) so the script stays readable and the
// derivation logic is unit-reviewable. Derives from the standard GitHub Actions environment, adapted to
// a single JVM library: owner/repo come from GITHUB_REPOSITORY and the server host from
// GITHUB_SERVER_URL, each with a gradle-property override. Never reads credentials, so it is safe to
// call during configuration with nothing set.
package buildsrc.convention

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.net.URI

// Used twice (resolution + the failure message), so kept as a named constant.
private const val REPOSITORY_SLUG_PROPERTY: String = "repositorySlug"

/**
 * The repository the published sources come from.
 *
 * @property owner the account the repository belongs to.
 * @property name the repository name.
 * @property webUrl the repository's page, used for POM metadata.
 * @property host the server host the repository is on.
 * @property isExplicit whether owner/name came from an explicitly configured slug (gradle property or
 *   environment) rather than the local-only `<name>/<name>` fallback. Remote publishing requires an
 *   explicit slug so a fallback-derived POM never leaves the machine.
 */
public data class SourceRepository(
    val owner: String,
    val name: String,
    val webUrl: String,
    val host: String,
    val isExplicit: Boolean,
)

/**
 * Resolves owner/repo, web url, and host, with a `-PrepositorySlug=<owner>/<repo>` override. With no
 * environment present (local `publishToMavenLocal`) the slug falls back to `<name>/<name>` and the
 * result is marked non-[explicit][SourceRepository.isExplicit].
 */
public fun Project.resolveSourceRepository(): SourceRepository {
    val explicitSlug =
        providers.gradleProperty(REPOSITORY_SLUG_PROPERTY).orNull
            ?: providers.environmentVariable("GITHUB_REPOSITORY").orNull
    val slug = explicitSlug ?: rootProject.name.let { "$it/$it" }
    val normalizedSlug = slug.trim().removePrefix("/").removeSuffix("/")
    val ownerAndName = normalizedSlug.split("/", limit = 2)
    if (ownerAndName.size != 2 || ownerAndName.any { it.isBlank() }) {
        throw GradleException(
            "Unable to resolve repository slug. " +
                "Set '$REPOSITORY_SLUG_PROPERTY' or 'GITHUB_REPOSITORY' as '<owner>/<repo>'.",
        )
    }

    val serverUrl =
        providers.gradleProperty("githubServerUrl").orNull
            ?: providers.environmentVariable("GITHUB_SERVER_URL").orNull
            ?: "https://github.com"
    val normalizedServerUrl = serverUrl.trim().removeSuffix("/")
    val host =
        runCatching { URI(normalizedServerUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "github.com"

    return SourceRepository(
        owner = ownerAndName[0],
        name = ownerAndName[1],
        webUrl = "$normalizedServerUrl/${ownerAndName[0]}/${ownerAndName[1]}",
        host = host,
        isExplicit = explicitSlug != null,
    )
}
