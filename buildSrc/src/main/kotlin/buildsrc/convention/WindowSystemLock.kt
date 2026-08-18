// Service type for the `buildsrc.convention.window-system-lock` precompiled plugin.
// Kept as a plain Kotlin file (not inside the .gradle.kts script), matching the project's convention
// of extracting non-DSL declarations so the script stays readable.
package buildsrc.convention

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Held by every test task that shows real windows, and granted to one of them at a time.
 *
 * Which of this process's windows the window system is attending to - which one is focused, which has
 * been minimized - is a single state for the whole machine, and a test that asserts on it loses the
 * assertion the moment another test opens a window of its own. Losing it reports a withheld capability
 * and skips, so the damage is silent: coverage that stops running while the build stays green.
 *
 * Gradle runs tasks of different projects concurrently, which is what puts two such tasks on the machine
 * at once. Tasks within one project are already serialized, so this constrains only across projects.
 */
public abstract class WindowSystemLock : BuildService<BuildServiceParameters.None>
