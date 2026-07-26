package org.jetbrains.compose.swing.components

import org.jetbrains.compose.swing.components.InProcessCompilerHarness.CompilationResult
import org.jetbrains.compose.swing.components.InProcessCompilerHarness.SourceSpec
import org.jetbrains.kotlin.cli.common.ExitCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A hoisted state holder is a contract a caller reads and writes, never one a caller supplies: the library
 * hands out the only implementations there are, and drives a realized widget through them. A caller that
 * could substitute an implementation of its own could hand the library values that never reach a widget or
 * that contradict what one reports, so the holders are final - an outside type cannot stand in for one.
 *
 * The refusal is a compile-time one and no test compiled together with the library can express it, so this
 * drives the real Kotlin compiler in-process over a snippet that consumes the library as a third party
 * does, and asserts on the diagnostics it emits.
 *
 * What is pinned here is substitution and not construction. Finality is an access flag, so it refuses a
 * caller of any language; where a holder's constructor is `internal`, that is Kotlin visibility alone - it
 * compiles to a public one a Java caller can still invoke to obtain the library's own holder.
 */
class StateHolderSubstitutionTest {
    @Test
    fun aHolderCannotBeImplementedOutsideTheLibrary() {
        val result =
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.desktop.InternalFrameState
                import org.jetbrains.compose.swing.components.layout.ScrollState
                import org.jetbrains.compose.swing.window.DialogState
                import org.jetbrains.compose.swing.window.WindowState

                class MyWindowState : WindowState()

                class MyDialogState : DialogState()

                class MyInternalFrameState : InternalFrameState(java.awt.Rectangle())

                // A ScrollState is reached through rememberScrollState alone, so this names no arguments:
                // its constructor is internal, so the snippet has none to call. The refusal to extend is
                // the same one.
                class MyScrollState : ScrollState()
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a snippet standing in its own state holders must not compile:\n${result.output}",
        )
        for (holder in listOf("WindowState", "DialogState", "InternalFrameState", "ScrollState")) {
            assertTrue(
                result.refusalsToExtend(holder).isNotEmpty(),
                "standing in an own $holder must be refused, but nothing refused it:\n${result.output}",
            )
        }
    }

    /**
     * The counterpart of the refusal: the very same snippet shape, hoisting and driving the holders the way
     * a caller does, compiles. Without this the refusal above could as well be a broken classpath.
     */
    @Test
    fun aHolderIsHoistedAndDrivenByAnOutsideCaller() {
        val result =
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.desktop.InternalFrameState
                import org.jetbrains.compose.swing.window.DialogState
                import org.jetbrains.compose.swing.window.WindowState
                import org.jetbrains.compose.swing.window.WindowPosition
                import java.awt.Dimension
                import java.awt.Frame
                import java.awt.Rectangle

                fun drive() {
                    val window = WindowState(WindowPosition.Absolute(10, 20), Dimension(300, 200))
                    window.extendedState = Frame.MAXIMIZED_BOTH
                    val dialog = DialogState(size = Dimension(120, 80))
                    dialog.position = WindowPosition.CenteredOnOwner
                    val frame = InternalFrameState(Rectangle(0, 0, 100, 100))
                    frame.bounds = Rectangle(5, 5, 100, 100)
                    frame.iconified = true
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "hoisting and driving the holders must compile:\n${result.output}",
        )
    }

    private companion object {
        /** The system property the Gradle test task uses to hand the harness the plugin jar(s). */
        private const val PLUGIN_CLASSPATH_PROPERTY = "compose.compiler.plugin.classpath"

        // The compiler's refusal to extend a final type, as captured from a real run of the embeddable
        // compiler, and the source line it echoes underneath each diagnostic - which is where the name of
        // the type being extended appears.
        private const val FINAL_TYPE_ERROR = ": error: this type is final, so it cannot be extended"

        private val composePluginClasspath: List<File> by lazy {
            System
                .getProperty(PLUGIN_CLASSPATH_PROPERTY)
                .orEmpty()
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map(::File)
        }

        private fun compileSnippet(source: String): CompilationResult = InProcessCompilerHarness.compileSnippet(
            source = SourceSpec(relativePath = "Snippet.kt", contents = source),
            pluginClasspath = composePluginClasspath,
        )

        /** The compiler's refusals to extend [symbol], one entry per diagnostic. */
        private fun CompilationResult.refusalsToExtend(symbol: String): List<String> {
            val lines = output.lines()
            return lines.indices
                .filter { index ->
                    lines[index].contains(FINAL_TYPE_ERROR) &&
                        lines.getOrNull(index + 1)?.contains(symbol) == true
                }.map { lines[it] }
        }
    }
}
