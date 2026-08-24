package buildsrc.convention

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

private val publishGroup: String =
    providers.gradleProperty("group").orNull?.takeIf { it.isNotBlank() }
        ?: "org.jetbrains.compose.swing"
private val publishVersion: String = resolvePublishVersion()

group = publishGroup
version = publishVersion

private val sourceRepository = resolveSourceRepository()
private val publishRepositories = publishRepositories(sourceRepository)

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
                url.set(sourceRepository.webUrl)

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set(sourceRepository.owner)
                        name.set(sourceRepository.owner)
                    }
                }

                scm {
                    connection.set("scm:git:${sourceRepository.webUrl}.git")
                    developerConnection.set(
                        "scm:git:ssh://git@${sourceRepository.host}/" +
                            "${sourceRepository.owner}/${sourceRepository.name}.git",
                    )
                    url.set(sourceRepository.webUrl)
                }
            }
        }
    }

    repositories {
        publishRepositories.forEach { repository ->
            maven {
                name = repository.name
                url = uri(repository.url)
                credentials {
                    username = repository.username.orNull
                    password = repository.password.orNull
                }
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
// artifact, so every remote publish task demands an explicitly configured slug.
tasks.withType<PublishToMavenRepository>().configureEach {
    val slugIsExplicit = sourceRepository.isExplicit
    val repositoryName = repository.name
    doFirst {
        if (!slugIsExplicit) {
            throw GradleException(
                "Publishing to the $repositoryName repository requires explicit repository " +
                    "coordinates: set -PrepositorySlug=<owner>/<repo> or the GITHUB_REPOSITORY " +
                    "environment variable.",
            )
        }
    }
}
