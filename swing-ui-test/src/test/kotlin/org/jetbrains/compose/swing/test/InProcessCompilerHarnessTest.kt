package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.test.InProcessCompilerHarness.CompilationResult
import org.jetbrains.compose.swing.test.InProcessCompilerHarness.SourceSpec
import org.jetbrains.kotlin.cli.common.ExitCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InProcessCompilerHarnessTest {
    @Test
    fun errorsExtractMessagesWithoutDependingOnDiagnosticSpacing() {
        val output =
            """
            e: /tmp/Snippet.kt:3:9: error: first diagnostic
            e: /tmp/Snippet.kt:4:9: warning: ignored diagnostic
            e: /tmp/Snippet.kt:5:9:error: second diagnostic
            """.trimIndent()

        val result = CompilationResult(ExitCode.COMPILATION_ERROR, output)

        assertEquals(
            listOf("first diagnostic", "second diagnostic"),
            result.errors(),
        )
        assertEquals(output, result.output)
    }

    @Test
    fun dataStructuresFulfillContracts() {
        val spec1 = SourceSpec("a/b.kt", "code")
        val spec2 = SourceSpec("a/b.kt", "code")
        val spec3 = SourceSpec("a/c.kt", "other")

        assertEquals(spec1, spec2)
        assertNotEquals(spec1, spec3)
        assertEquals(spec1.hashCode(), spec2.hashCode())
        assertTrue(spec1.toString().contains("a/b.kt"))

        val res1 = CompilationResult(ExitCode.OK, "out")
        val res2 = CompilationResult(ExitCode.OK, "out")
        val res3 = CompilationResult(ExitCode.COMPILATION_ERROR, "out")

        assertEquals(res1, res2)
        assertNotEquals(res1, res3)
        assertEquals(res1.hashCode(), res2.hashCode())
        assertTrue(res1.toString().contains("OK"))
    }

    @Test
    fun compilesSnippetWithPlugin() {
        val result = InProcessCompilerHarness.compileSnippet("TestSnippet.kt", "val answer: Int = 42\n")
        assertEquals(ExitCode.OK, result.exitCode, "Snippet compilation should succeed: ${result.output}")
        assertTrue(InProcessCompilerHarness.composePluginClasspath.isNotEmpty())
    }
}
