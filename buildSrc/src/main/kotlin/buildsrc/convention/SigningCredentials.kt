// Signing credentials, resolved identically for the module publications that sign and the aggregation
// that refuses to upload unsigned. A workflow expands an undefined secret to an empty value rather than
// leaving the variable unset, so a blank key counts as no key.
package buildsrc.convention

import org.gradle.api.Project

/** The ASCII-armored PGP private key publications are signed with, or null when none is configured. */
public fun Project.resolveSigningKey(): String? =
    providers.environmentVariable("SIGNING_KEY").orNull?.takeIf { it.isNotBlank() }

/** The passphrase protecting [resolveSigningKey], empty when the key carries none. */
public fun Project.resolveSigningPassword(): String = providers.environmentVariable("SIGNING_PASSWORD").orNull.orEmpty()
