// Extension type for the `buildsrc.convention.jacoco-coverage` precompiled plugin.
// Kept as a plain Kotlin file (not inside the .gradle.kts script), matching the project's convention
// of extracting non-DSL declarations so the script stays readable.
package buildsrc.convention

import org.gradle.api.provider.Property
import java.math.BigDecimal

/**
 * Coverage floors for a module's `jacocoTestCoverageVerification` task, configured via the
 * `jacocoCoverage { }` block. [lineMinimum] is required; [branchMinimum] is optional, so a module
 * with no branch-level floor can leave it unset.
 */
public interface JacocoCoverageExtension {
    public val lineMinimum: Property<BigDecimal>
    public val branchMinimum: Property<BigDecimal>
}
