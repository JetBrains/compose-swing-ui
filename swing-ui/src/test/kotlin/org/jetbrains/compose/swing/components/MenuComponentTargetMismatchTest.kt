package org.jetbrains.compose.swing.components

import org.jetbrains.compose.swing.components.InProcessCompilerHarness.CompilationResult
import org.jetbrains.compose.swing.components.InProcessCompilerHarness.SourceSpec
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that the Compose compiler flags accidental mixing of our menu composables
 * ([org.jetbrains.compose.swing.components.Menu] and friends, marked `@SwingMenuComposable`) with our
 * regular component composables ([org.jetbrains.compose.swing.components.layout.FlowPanel],
 * [org.jetbrains.compose.swing.components.button.Button], ... marked `@SwingComposable`).
 *
 * The applier/target-mismatch diagnostic is intentionally a **warning**, not an error: mixed code still
 * compiles, so a plain `./gradlew build` cannot catch a regression here. To assert on it we drive the real
 * Compose compiler plugin in-process via the official embeddable Kotlin compiler, capture the emitted
 * diagnostics, and assert both the message text and that the severity stays a warning.
 *
 * Keeping the diagnostic at default (warning) severity is a deliberate design choice. These tests pin that
 * contract; they do **not** change it.
 */
class MenuComponentTargetMismatchTest {
    /**
     * A component container hosting a menu item must produce a target-mismatch warning.
     */
    @Test
    fun menuItemInsideComponentContainerWarns() {
        val result =
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.layout.FlowPanel
                import org.jetbrains.compose.swing.components.MenuItem

                @androidx.compose.runtime.Composable
                fun Mixed() {
                    FlowPanel {
                        MenuItem(text = "x")
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, "mixed code must still compile")
        result.assertHasTargetMismatchWarning()
    }

    /**
     * A menu hosting a regular component must produce a target-mismatch warning.
     */
    @Test
    fun componentInsideMenuWarns() {
        val result =
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.Menu
                import org.jetbrains.compose.swing.components.button.Button

                @androidx.compose.runtime.Composable
                fun Mixed() {
                    Menu(text = "m") {
                        Button(text = "b", onClick = {})
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, "mixed code must still compile")
        result.assertHasTargetMismatchWarning()
    }

    /**
     * Correct nesting - components in a component container, menu items in a menu - must produce no
     * target-mismatch diagnostic at all.
     */
    @Test
    fun correctNestingProducesNoMismatch() {
        val result =
            compileSnippet(
                """
                import org.jetbrains.compose.swing.components.layout.FlowPanel
                import org.jetbrains.compose.swing.components.button.Button
                import org.jetbrains.compose.swing.components.Menu
                import org.jetbrains.compose.swing.components.MenuItem

                @androidx.compose.runtime.Composable
                fun Components() {
                    FlowPanel {
                        Button(text = "b", onClick = {})
                    }
                }

                @androidx.compose.runtime.Composable
                fun Menus() {
                    Menu(text = "m") {
                        MenuItem(text = "x")
                    }
                }
                """.trimIndent(),
            )

        assertEquals(ExitCode.OK, result.exitCode, "correct nesting must compile")
        val mismatch = result.targetMismatchDiagnostics()
        assertTrue(
            mismatch.isEmpty(),
            "correct nesting must not emit any target-mismatch diagnostic, but saw:\n" +
                mismatch.joinToString("\n") { "${it.severity}: ${it.text}" },
        )
    }

    private fun CompilationResult.assertHasTargetMismatchWarning() {
        val mismatch = targetMismatchDiagnostics()
        assertTrue(
            mismatch.isNotEmpty(),
            "expected a Compose target-mismatch diagnostic, but none was emitted.\nAll messages:\n$output",
        )
        // Default severity is intentional: the mismatch must surface, but stay a warning, never an error.
        assertTrue(
            mismatch.all { it.severity == DiagnosticSeverity.WARNING },
            "target mismatch must stay a default-severity WARNING, but saw:\n" +
                mismatch.joinToString("\n") { "${it.severity}: ${it.text}" },
        )
    }

    /** Severity of a compiler diagnostic, decoded from the `w:` / `e:` line prefix. */
    private enum class DiagnosticSeverity {
        WARNING,
        ERROR,
    }

    /** A single compiler diagnostic line, with its severity decoded from the `w:` / `e:` prefix. */
    private data class ParsedDiagnostic(
        val severity: DiagnosticSeverity,
        val text: String,
    )

    private companion object {
        // Stable fragments of the Compose applier/target-mismatch diagnostic, captured from a real run of
        // the Compose compiler plugin against the snippets below. The full text reads, e.g.:
        //   "Calling a Swing Menu Composable composable function where a Swing Composable composable was
        //    expected"
        // and the symmetric form. The human-readable target names ("Swing Composable" / "Swing Menu
        // Composable") come straight from our @ComposableTargetMarker(description = ...) annotations, so we
        // anchor on the stable "...composable was expected" tail together with both marker descriptions.
        private const val TARGET_MISMATCH_TAIL = "composable was expected"
        private const val COMPONENT_TARGET_NAME = "Swing Composable"
        private const val MENU_TARGET_NAME = "Swing Menu Composable"

        /** The system property the Gradle test task uses to hand the harness the plugin jar(s). */
        private const val PLUGIN_CLASSPATH_PROPERTY = "compose.compiler.plugin.classpath"

        private val composePluginClasspath: List<File> by lazy { resolveComposePluginClasspath() }

        /**
         * Fail fast at suite startup if the Compose compiler plugin classpath is not wired up, so a
         * missing setup surfaces as this explicit assertion instead of a cryptic compile failure inside
         * the first test that drives the compiler. Resolving the (cached) classpath here also primes it
         * for every test.
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
            val jars =
                raw
                    .split(File.pathSeparator)
                    .filter { it.isNotBlank() }
                    .map(::File)
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
            source = SourceSpec(relativePath = "Snippet.kt", contents = source),
            pluginClasspath = composePluginClasspath,
        )

        // The embeddable compiler's CLI prints each diagnostic with a file location and a severity token,
        // e.g. "<path>/Snippet.kt:7:9: warning: <message>". Severity is decoded from that token.
        private const val WARNING_TOKEN = ": warning:"
        private const val ERROR_TOKEN = ": error:"

        /**
         * Diagnostics that are a Compose applier/target mismatch between our two markers - i.e. the
         * "...composable was expected" message that names both [COMPONENT_TARGET_NAME] and
         * [MENU_TARGET_NAME].
         *
         * The mismatch is parsed from the raw [CompilationResult.output] stream (one line per diagnostic)
         * so that severity is asserted on the exact text the compiler printed.
         */
        private fun CompilationResult.targetMismatchDiagnostics(): List<ParsedDiagnostic> = output
            .lineSequence()
            .mapNotNull { line ->
                val severity =
                    when {
                        line.contains(WARNING_TOKEN) -> DiagnosticSeverity.WARNING
                        line.contains(ERROR_TOKEN) -> DiagnosticSeverity.ERROR
                        else -> return@mapNotNull null
                    }
                ParsedDiagnostic(severity, line)
            }.filter {
                it.text.contains(TARGET_MISMATCH_TAIL) &&
                    it.text.contains(COMPONENT_TARGET_NAME) &&
                    it.text.contains(MENU_TARGET_NAME)
            }.toList()
    }
}
