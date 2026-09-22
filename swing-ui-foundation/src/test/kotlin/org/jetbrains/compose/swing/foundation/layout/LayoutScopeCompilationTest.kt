package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.assertRejected
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [ConstrainedScope] as the only scope in reach inside a [Layout]'s content. A child declares its
 * placement to the container that measures it, so a declaration meant for an enclosing row must not be
 * readable by a child of a `Layout` nested in one: the row's child is the `Layout`, and the policy the
 * `Layout` holds reads nothing a row's scope names. A container nested in another hides the outer one's
 * scope the same way.
 *
 * A caller cannot be shown a rejected program by a running test, so these compile one with the official
 * Kotlin compiler driven in-process and read the diagnostics it emits.
 */
class LayoutScopeCompilationTest {
    @Test
    fun aRowsDeclarationInsideANestedLayoutDoesNotCompile() {
        val result = compileSnippet("Label(\"Details\", modifier = SwingModifier.weight(1f))")

        assertEquals(
            ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "a row's weight declared on a child of a nested layout must be rejected, output was:\n${result.output}",
        )
        assertTrue(
            result.errors().any { "weight" in it },
            "the rejection must name the declaration it is about, output was:\n${result.output}",
        )
    }

    @Test
    fun aNestedContainerHidesTheOuterContainersOwnScope() {
        assertRejected(
            "weight",
            """
            @Composable
            fun Nested() {
                Row {
                    Box {
                        Label("Details", modifier = SwingModifier.weight(1f))
                    }
                }
            }
            """,
        )
        assertRejected(
            "align",
            """
            @Composable
            fun Nested() {
                Box {
                    Row {
                        Label("Details", modifier = SwingModifier.align(Alignment.Center))
                    }
                }
            }
            """,
        )
    }

    /** What the policy itself honors stays in reach: those builders are the scope the content is given. */
    @Test
    fun aChildsOwnLayoutModifiersResolveInsideANestedLayout() {
        val result =
            compileSnippet(
                "Label(\"Details\", modifier = " +
                    "SwingModifier.padding(4).fillMaxWidth(.5f).fillMaxHeight(.5f).fillMaxSize(.5f)" +
                    ".size(20).size(20, 30).width(20).height(30).width(IntrinsicSize.Min)" +
                    ".height(IntrinsicSize.Max).widthIn(min = 10, max = 20)" +
                    ".heightIn(min = 10, max = 20).sizeIn(minWidth = 10, minHeight = 20)" +
                    ".requiredSize(20).requiredSize(20, 30).requiredWidth(20).requiredHeight(30)" +
                    ".requiredWidth(IntrinsicSize.Min).requiredHeight(IntrinsicSize.Max)" +
                    ".requiredWidthIn(min = 10, max = 20).requiredHeightIn(min = 10, max = 20)" +
                    ".requiredSizeIn(minWidth = 10, minHeight = 20).wrapContentWidth()" +
                    ".wrapContentHeight().wrapContentSize().defaultMinSize(minWidth = 10, minHeight = 20))",
            )

        assertEquals(
            ExitCode.OK,
            result.exitCode,
            "every constrained layout modifier must resolve for a child of a nested layout, output was:\n" +
                result.output,
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

        /** Asserts [program] fails to compile with an error naming [name]. */
        fun assertRejected(
            name: String,
            program: String,
        ) = compile(program).assertRejected(listOf(name))

        fun compile(program: String) =
            InProcessCompilerHarness.compileSnippet(
                "LayoutScopeSnippet.kt",
                """
                import androidx.compose.runtime.Composable
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.foundation.layout.*
                import org.jetbrains.compose.swing.modifier.SwingModifier

                """.trimIndent() + program.trimIndent(),
            )

        /** [child] written inside a `Layout` that is itself written inside a `Row`. */
        fun compileSnippet(child: String) =
            InProcessCompilerHarness.compileSnippet(
                "LayoutScopeSnippet.kt",
                """
                import org.jetbrains.compose.swing.components.Label
                import org.jetbrains.compose.swing.foundation.layout.*
                import org.jetbrains.compose.swing.modifier.SwingModifier

                @androidx.compose.runtime.Composable
                fun Nested() {
                    Row {
                        Layout(
                            measurePolicy = { measurables, constraints ->
                                val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
                                layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.sumOf { it.height }) {
                                    var y = 0
                                    for (placeable in placeables) {
                                        placeable.placeRelative(0, y)
                                        y += placeable.height
                                    }
                                }
                            },
                            modifier = SwingModifier,
                        ) {
                            $child
                        }
                    }
                }
                """.trimIndent(),
            )
    }
}
