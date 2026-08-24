package buildsrc.convention

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
    id("org.jetbrains.dokka")
}

private val publishGroup: String =
    providers.gradleProperty("group").orNull?.takeIf { it.isNotBlank() }
        ?: "org.jetbrains.compose.swing"
private val publishVersion: String = resolvePublishVersion()

group = publishGroup
version = publishVersion

private val coordinates = resolveRepositoryCoordinates()
private val publishUsername = publishUsername()
private val publishToken = publishToken()

private val signingKey: String? = resolveSigningKey()
private val signingPassword: String = resolveSigningPassword()

extensions.configure<JavaPluginExtension> {
    withSourcesJar()
    withJavadocJar()
}

dokka {
    moduleName.set(project.name)
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])

            pom {
                url.set(coordinates.webUrl)

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set(coordinates.owner)
                        name.set(coordinates.owner)
                    }
                }

                scm {
                    connection.set("scm:git:${coordinates.webUrl}.git")
                    developerConnection.set(
                        "scm:git:ssh://git@${coordinates.host}/" +
                            "${coordinates.owner}/${coordinates.name}.git",
                    )
                    url.set(coordinates.webUrl)
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(coordinates.packagesUrl)
            // Nullable on purpose: maven-publish validates credentials only when a remote publish task
            // actually executes, so publishToMavenLocal works with no GitHub credentials configured.
            credentials {
                username = publishUsername.orNull
                password = publishToken.orNull
            }
        }
    }
}

signing {
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["maven"])
}

// The fallback `<name>/<name>` slug exists only so publishToMavenLocal works with no GitHub
// environment configured; a remote publish would bake its synthesized POM urls into the published
// artifact, so remote publish tasks demand an explicitly configured slug.
tasks.withType<PublishToMavenRepository>().configureEach {
    val coordinatesAreExplicit = coordinates.isExplicit
    val repositoryName = repository.name
    doFirst {
        if (!coordinatesAreExplicit) {
            throw GradleException(
                "Publishing to the $repositoryName repository requires explicit repository " +
                    "coordinates: set -PrepositorySlug=<owner>/<repo> or the GITHUB_REPOSITORY " +
                    "environment variable.",
            )
        }
    }
}
