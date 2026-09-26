package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.InProcessCompilerHarness.CompilationResult
import org.jetbrains.kotlin.cli.common.ExitCode
import kotlin.test.assertEquals

/** Asserts that this result compiled cleanly. */
internal fun CompilationResult.assertCompiled() {
    assertEquals(ExitCode.OK, exitCode, "the snippet must compile, output was:\n$output")
}

/**
 * Asserts this result failed to compile with one error naming each of [names], and no other. When
 * [context] is given, every failure message is prefixed with it, so a failing case in a loop is named.
 */
internal fun CompilationResult.assertRejected(
    names: List<String>,
    context: String? = null,
) {
    val errors = errors()
    val prefix = context?.let { "$it: " }.orEmpty()
    assertEquals(
        names.size,
        errors.size,
        "${prefix}each of $names must be rejected once, output was:\n$output",
    )
    for (name in names.distinct()) {
        assertEquals(
            names.count { it == name },
            errors.count { name in it },
            "${prefix}the rejections must name `$name`, output was:\n$output",
        )
    }
    assertEquals(ExitCode.COMPILATION_ERROR, exitCode, "${prefix}output was:\n$output")
}
