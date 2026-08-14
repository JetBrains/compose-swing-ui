// Coordinate-resolution helpers for the `buildsrc.convention.publishing` precompiled plugin.
// Kept as a plain Kotlin file (not inside the .gradle.kts script) so the script stays readable and
// the derivation logic is unit-reviewable. Derives coordinates from the standard GitHub Actions
// environment, adapted to a single JVM library: owner/repo come from GITHUB_REPOSITORY, the server host
// from GITHUB_SERVER_URL, and credentials from GITHUB_ACTOR/GITHUB_TOKEN, each with a gradle-property
// override. Credentials are modeled as nullable providers so the GitHubPackages repository block
// configures cleanly with no environment present; maven-publish only validates them when a remote
// publish task actually executes.
package buildsrc.convention

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.net.URI

// Used twice (resolution + the failure message), so kept as a named constant.
private const val REPOSITORY_SLUG_PROPERTY: String = "repositorySlug"

/**
 * Resolved GitHub repository coordinates used to build the Maven repository URL and POM metadata.
 *
 * @property owner the account the repository belongs to.
 * @property name the repository name.
 * @property webUrl the repository's page, used for POM metadata.
 * @property host the server host the repository is on.
 * @property isExplicit whether owner/name came from an explicitly configured slug (gradle property or
 *   environment) rather than the local-only `<name>/<name>` fallback. Remote publishing requires an
 *   explicit slug so a fallback-derived POM never leaves the machine.
 */
public data class GitHubRepositoryCoordinates(
    val owner: String,
    val name: String,
    val webUrl: String,
    val host: String,
    val isExplicit: Boolean,
) {
    /** The Maven repository these coordinates publish to. */
    val packagesUrl: String
        get() = "https://maven.pkg.github.com/$owner/$name"
}

/** Username for the GitHubPackages repository: `-PgithubActor` override, else the GITHUB_ACTOR env. */
public fun Project.publishUsername(): Provider<String> =
    providers
        .gradleProperty("githubActor")
        .orElse(providers.environmentVariable("GITHUB_ACTOR"))

/** Token for the GitHubPackages repository: `-PgithubToken` override, else the GITHUB_TOKEN env. */
public fun Project.publishToken(): Provider<String> =
    providers
        .gradleProperty("githubToken")
        .orElse(providers.environmentVariable("GITHUB_TOKEN"))

/**
 * Resolves owner/repo, web url, and host from the GitHub Actions environment, with a
 * `-PrepositorySlug=<owner>/<repo>` override. Never reads credentials, so it is safe to call during
 * configuration with no environment present (local `publishToMavenLocal`); in that case the slug
 * falls back to `<name>/<name>` and the result is marked non-[explicit][GitHubRepositoryCoordinates.isExplicit].
 */
public fun Project.resolveRepositoryCoordinates(): GitHubRepositoryCoordinates {
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

    return GitHubRepositoryCoordinates(
        owner = ownerAndName[0],
        name = ownerAndName[1],
        webUrl = "$normalizedServerUrl/${ownerAndName[0]}/${ownerAndName[1]}",
        host = host,
        isExplicit = explicitSlug != null,
    )
}
