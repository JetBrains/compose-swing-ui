package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.test.InProcessCompilerHarness.CompilationResult
import org.jetbrains.compose.swing.test.InProcessCompilerHarness.SourceSpec
import org.jetbrains.kotlin.cli.common.ExitCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InProcessCompilerHarnessTest {
    @Test
    fun errorsExtractMessagesWithoutDependingOnDiagnosticSpacing() {
        val output =
            """
            e: Snippet.kt:3:9: error: first diagnostic
            e: Snippet.kt:4:9: warning: ignored diagnostic
            e: Snippet.kt:5:9:error: second diagnostic
            """.trimIndent()

        val result = CompilationResult(ExitCode.COMPILATION_ERROR, output)

        assertEquals(
            listOf("first diagnostic", "second diagnostic"),
            result.errors(),
        )
        assertEquals(output, result.output)
    }

    @Test
    fun assertRejectedFailsWhenAnErrorGoesUnnamed() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "TwoUnresolvedSnippet.kt",
                """
                val first = missingFirst
                val second = missingSecond
                """.trimIndent(),
            )

        result.assertRejected(listOf("missingFirst", "missingSecond"))
        assertFailsWith<AssertionError> { result.assertRejected(listOf("missingFirst")) }
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
    fun rejectsAComposableCallOutsideAComposableFunctionWithPlugin() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "InvalidComposableCall.kt",
                """
                import androidx.compose.runtime.Composable

                @Composable
                fun content() {}

                fun callOutsideComposition() = content()
                """.trimIndent(),
            )

        assertTrue(
            result.errors().any { it.contains("Composable invocations can only happen", ignoreCase = true) },
            "Expected the Compose plugin to reject the invocation, got: ${result.output}",
        )
        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
    }

    @Test
    fun compilesContextParameterDeclarationAndCallWithKotlin22Settings() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "ContextParameterSnippet.kt",
                """
                interface Dependency

                context(dependency: Dependency)
                fun useDependency(): Dependency = dependency

                fun callWithContext(dependency: Dependency): Dependency =
                    context(dependency) { useDependency() }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "Context parameter declaration and call should compile: ${result.output}",
        )
    }

    @Test
    fun reportsMeaningfulDiagnosticWhenContextParameterIsMissing() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MissingContextParameterSnippet.kt",
                """
                interface Dependency

                context(dependency: Dependency)
                fun useDependency(): Dependency = dependency

                fun callWithoutContext(): Dependency = useDependency()
                """.trimIndent(),
            )

        assertEquals(ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.errors().any { diagnostic ->
                diagnostic.contains("no context argument for", ignoreCase = true) &&
                    diagnostic.contains("dependency: Dependency")
            },
            "Expected a missing-context diagnostic, got: ${result.output}",
        )
    }
}
