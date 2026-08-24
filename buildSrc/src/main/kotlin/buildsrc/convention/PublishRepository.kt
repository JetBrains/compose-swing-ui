// The remote repositories publications are uploaded to, each described by the same structure so the
// publishing convention configures them in one loop. Credentials are providers rather than values:
// maven-publish validates them only when a remote publish task actually executes, so a repository with
// none configured still leaves publishToMavenLocal working.
package buildsrc.convention

import org.gradle.api.Project
import org.gradle.api.provider.Provider

/**
 * A Maven repository publications are uploaded to.
 *
 * @property name names the repository in Gradle, and so in the `publish...To<name>Repository` tasks.
 * @property url the repository to upload to.
 * @property username the account to authenticate as, absent when none is configured.
 * @property password the secret to authenticate with, absent when none is configured.
 */
public data class PublishRepository(
    val name: String,
    val url: String,
    val username: Provider<String>,
    val password: Provider<String>,
)

/**
 * The repositories this build publishes to: GitHub Packages for [source], and the Maven repository
 * `MAVEN_REPO_URL` names, which is left out when nothing configures it. No URL is defaulted, so a
 * fork publishes to its own repository by configuring one.
 */
public fun Project.publishRepositories(source: SourceRepository): List<PublishRepository> =
    listOfNotNull(
        PublishRepository(
            name = "GitHubPackages",
            url = "https://maven.pkg.github.com/${source.owner}/${source.name}",
            username = setting("githubActor", "GITHUB_ACTOR"),
            password = setting("githubToken", "GITHUB_TOKEN"),
        ),
        setting("mavenRepoUrl", "MAVEN_REPO_URL").orNull?.takeIf { it.isNotBlank() }?.let { url ->
            PublishRepository(
                name = "Maven",
                url = url,
                username = setting("mavenRepoUsername", "MAVEN_REPO_USERNAME"),
                password = setting("mavenRepoPassword", "MAVEN_REPO_PASSWORD"),
            )
        },
    )

/** A build setting, taken from a gradle property first and the environment second. */
private fun Project.setting(
    property: String,
    environmentVariable: String,
): Provider<String> =
    providers
        .gradleProperty(property)
        .orElse(providers.environmentVariable(environmentVariable))
