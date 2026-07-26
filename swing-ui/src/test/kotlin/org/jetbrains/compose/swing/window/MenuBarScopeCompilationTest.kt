package org.jetbrains.compose.swing.window

import org.jetbrains.compose.swing.components.InProcessCompilerHarness
import org.jetbrains.compose.swing.components.InProcessCompilerHarness.CompilationResult
import org.jetbrains.compose.swing.components.InProcessCompilerHarness.SourceSpec
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import java.io.File
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
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.Menu
                import org.jetbrains.compose.swing.components.MenuItem
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
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.Menu
                import org.jetbrains.compose.swing.components.MenuItem
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
            compileSnippet(
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
            compileSnippet(
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
            compileSnippet(
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

    /**
     * What the compiler said in each error diagnostic, with the source location it prefixes the message
     * with dropped.
     *
     * The location is the snippet's own file path, so a match against the whole line holds for whatever
     * the snippet file happens to be called rather than for anything the compiler found in it: the name
     * of the declaration under test appears in every diagnostic, and in a passing assertion, even when
     * the compiler is complaining about something else entirely.
     */
    private fun CompilationResult.errors(): List<String> = output
        .lineSequence()
        .filter { DIAGNOSTIC_SEPARATOR in it }
        .map { it.substringAfter(DIAGNOSTIC_SEPARATOR) }
        .toList()

    private companion object {
        /** What the compiler puts between the source location of a diagnostic and the message itself. */
        private const val DIAGNOSTIC_SEPARATOR = ": error: "

        /** The system property the Gradle test task uses to hand the harness the plugin jar(s). */
        private const val PLUGIN_CLASSPATH_PROPERTY = "compose.compiler.plugin.classpath"

        /**
         * The Compose compiler plugin jar(s) the Gradle test task hands every test in this module, so a
         * snippet is compiled the way the real build compiles it. Whether a call has a receiver to
         * resolve against is settled by the frontend either way.
         */
        private val composePluginClasspath: List<File> by lazy { resolveComposePluginClasspath() }

        /**
         * Fail fast at suite startup when the Compose compiler plugin classpath is not wired up: every
         * snippet below still compiles without the plugin, so an unset property would leave the
         * assertions passing over a compilation that is not the one the real build performs.
         */
        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            resolveComposePluginClasspath()
        }

        /**
         * Resolves the Compose compiler plugin jar(s) from the [PLUGIN_CLASSPATH_PROPERTY] system
         * property, asserting both that the property is set and that every jar it names exists, with a
         * message that tells the user exactly which property the Gradle test task must set.
         */
        private fun resolveComposePluginClasspath(): List<File> {
            val raw =
                System.getProperty(PLUGIN_CLASSPATH_PROPERTY)
                    ?: throw AssertionError(
                        "System property '$PLUGIN_CLASSPATH_PROPERTY' is not set; the Gradle test task must " +
                            "hand the resolved Compose compiler plugin jar to the harness. Run these tests " +
                            "via Gradle (./gradlew :swing-ui:test), or set " +
                            "-D$PLUGIN_CLASSPATH_PROPERTY=<path-to-compose-compiler-plugin.jar> when running " +
                            "them directly.",
                    )
            val jars = raw.split(File.pathSeparator).filter(String::isNotBlank).map(::File)
            val missing = jars.filterNot(File::exists)
            if (jars.isEmpty() || missing.isNotEmpty()) {
                throw AssertionError(
                    "System property '$PLUGIN_CLASSPATH_PROPERTY' does not point at existing Compose " +
                        "compiler plugin jar(s). Value: '$raw'. " +
                        if (jars.isEmpty()) {
                            "No jar paths were listed."
                        } else {
                            "Missing: ${missing.joinToString { it.path }}."
                        },
                )
            }
            return jars
        }

        private fun compileSnippet(source: String): CompilationResult = InProcessCompilerHarness.compileSnippet(
            source = SourceSpec(relativePath = "MenuBarSnippet.kt", contents = source),
            pluginClasspath = composePluginClasspath,
        )
    }
}
