package org.jetbrains.compose.swing.window

import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the containment of [MenuBar] as a compile-time constraint: a window carries the bar, so the
 * declaration lives on [WindowScope] and a call to it with no window to belong to is a compile error,
 * not a failure at run time. The scope itself is a final class with a private constructor, so the only
 * value of it is the one a window handed its content - a scope of a caller's own is rejected by the
 * compiler as well, rather than at run time.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class MenuBarScopeCompilationTest {
    @Test
    fun aMenuBarOutsideAWindowsContentDoesNotCompile() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MenuBarSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.menu.Menu
                import org.jetbrains.compose.swing.components.menu.MenuItem
                import org.jetbrains.compose.swing.window.MenuBar

                @androidx.compose.runtime.Composable
                fun Bare() {
                    MenuBar {
                        Menu("File") { MenuItem("New", onClick = {}) }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a menu bar with no window to belong to must be rejected, output was:\n${result.output}",
        )
        assertTrue(
            result.errors().any { "MenuBar" in it },
            "the rejection must name the call it is about, output was:\n${result.output}",
        )
    }

    @Test
    fun aMenuBarInAWindowsContentCompiles() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MenuBarSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.menu.Menu
                import org.jetbrains.compose.swing.components.menu.MenuItem
                import org.jetbrains.compose.swing.window.Dialog
                import org.jetbrains.compose.swing.window.MenuBar
                import org.jetbrains.compose.swing.window.Window

                @androidx.compose.runtime.Composable
                fun Framed() {
                    Window(onCloseRequest = {}) {
                        MenuBar {
                            Menu("File") { MenuItem("New", onClick = {}) }
                        }
                    }
                }

                @androidx.compose.runtime.Composable
                fun Dialogged() {
                    Dialog(onCloseRequest = {}) {
                        MenuBar {
                            Menu("File") { MenuItem("New", onClick = {}) }
                        }
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "the content of a window and of a dialog must both take a menu bar, output was:\n${result.output}",
        )
    }

    /**
     * A caller who could subtype [WindowScope] could reach [MenuBar] with a value standing for no window,
     * so the type is final and the attempt does not compile.
     */
    @Test
    fun aWindowScopeSubtypeOfACallersOwnDoesNotCompile() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MenuBarSnippet.kt",
                """
                import org.jetbrains.compose.swing.window.WindowScope

                class MyScope : WindowScope()
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a window scope subtype of a caller's own must be rejected, output was:\n${result.output}",
        )
        assertTrue(
            result.errors().any { "final" in it },
            "the rejection must say the type is final, output was:\n${result.output}",
        )
    }

    /**
     * The other way to a value standing for no window is to construct one, and the only constructor is
     * private: a window is what hands a scope out.
     */
    @Test
    fun aWindowScopeConstructedByACallerDoesNotCompile() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MenuBarSnippet.kt",
                """
                import org.jetbrains.compose.swing.window.WindowScope
                import javax.swing.JRootPane

                fun mine(): WindowScope = WindowScope(JRootPane())
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a window scope a caller constructs must be rejected, output was:\n${result.output}",
        )
        assertTrue(
            result.errors().any { "private" in it },
            "the rejection must say the constructor is private, output was:\n${result.output}",
        )
    }

    /**
     * The scope is a receiver the content is free to ignore, so content that declares nothing on the
     * window it fills compiles as a plain trailing lambda.
     */
    @Test
    fun contentThatIgnoresTheScopeCompiles() {
        val result =
            InProcessCompilerHarness.compileSnippet(
                "MenuBarSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.window.Window

                @androidx.compose.runtime.Composable
                fun Plain() {
                    Window(onCloseRequest = {}) {
                        Label("hi")
                    }
                }
                """.trimIndent(),
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "content that ignores the scope it is given must compile, output was:\n${result.output}",
        )
    }

    private companion object {
        /**
         * Resolves the compiler plugin classpath once at startup, so a test task that does not hand the
         * harness one reports a single failure here rather than the same failure in every case below.
         */
        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }
    }
}
