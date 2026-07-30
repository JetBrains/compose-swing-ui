// Aggregates the publishing modules into the single signed bundle the Central Portal accepts. Applied
// to the root project, which owns the aggregation the portal uploads.
package buildsrc.convention

plugins {
    id("com.gradleup.nmcp.aggregation")
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("CENTRAL_TOKEN_USER")
        password = providers.environmentVariable("CENTRAL_TOKEN_PASS")
        // A deployment is validated and then waits to be released from the portal, because
        // publication to Maven Central is permanent.
        publishingType = "USER_MANAGED"
    }
}

// Applying the publishing convention is what enrols a module: it brings the plugin that exposes the
// module's publications to the aggregation. Modules without it, the samples among them, expose nothing
// and drop out of the bundle.
dependencies {
    subprojects.forEach { nmcpAggregation(project(it.path)) }
}

// Signing is per module and skipped when no key is present, so an unsigned bundle fails portal
// validation only once transferred. The guard sits on the task that uploads; the lifecycle task of
// the same name completes after it.
tasks.named("nmcpPublishAggregationToCentralPortal") {
    val signingKeyConfigured = resolveSigningKey() != null
    doFirst {
        if (!signingKeyConfigured) {
            throw GradleException(
                "Publishing to Maven Central requires a signing key: set SIGNING_KEY to an " +
                    "ASCII-armored PGP private key, and SIGNING_PASSWORD to its passphrase.",
            )
        }
    }
}
