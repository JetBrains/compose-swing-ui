package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnheldColumnComparatorTest {
    private fun lint(source: String) = UnheldColumnComparator(Config.empty).lint(source)

    private fun lintTable(body: String) =
        lint(
            """
            package sample

            import org.jetbrains.compose.swing.components.selection.Table

            fun table() {
                Table(rows = emptyList<String>()) {
                    $body
                }
            }
            """.trimIndent(),
        )

    @Test
    fun `reports a comparator built with a call where the column is declared`() {
        val findings =
            lintTable("column(\"Name\", comparator = compareBy<String> { it }) { it }")

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("built anew on every pass"))
    }

    @Test
    fun `reports a comparator built with a lambda where the column is declared`() {
        assertEquals(
            1,
            lintTable("column(\"Name\", comparator = { a: String, b: String -> a.compareTo(b) }) { it }").size,
        )
    }

    @Test
    fun `reports a comparator built through an explicit table receiver`() {
        assertEquals(
            1,
            lintTable("this.column(\"Name\", comparator = compareBy<String> { it }) { it }").size,
        )
    }

    @Test
    fun `reports a comparator built inside a transparent run block`() {
        assertEquals(
            1,
            lintTable(
                """
                run {
                    column("Name", comparator = compareBy<String> { it }) { it }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a comparator built inside a qualified Kotlin run block`() {
        assertEquals(
            1,
            lintTable(
                """
                kotlin.run {
                    column("Name", comparator = compareBy<String> { it }) { it }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not follow a run imported from another package`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import other.run
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        run {
                            column("Name", comparator = compareBy<String> { it }) { it }
                        }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not follow a run from an unrelated wildcard import`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import other.*
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        run {
                            column("Name", comparator = compareBy<String> { it }) { it }
                        }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `explicit Kotlin run wins over an unrelated wildcard import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import kotlin.run
                import other.*
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        run {
                            column("Name", comparator = compareBy<String> { it }) { it }
                        }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a comparator built inside an aliased Kotlin run block`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import kotlin.run as withScope
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        withScope {
                            column("Name", comparator = compareBy<String> { it }) { it }
                        }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a comparator built with an object expression`() {
        assertEquals(
            1,
            lintTable(
                """
                column(
                    "Name",
                    comparator =
                        object : Comparator<String> {
                            override fun compare(first: String, second: String): Int = 0
                        },
                ) { it }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a comparator held by a local variable built where the column is declared`() {
        assertEquals(
            1,
            lintTable(
                """
                val byName = compareBy<String> { it }
                column("Name", comparator = byName) { it }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `follows a local variable declared in the composable across the block that declares the columns`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                fun people(names: List<String>) {
                    val byName = compareBy<String> { it }
                    Table(names) {
                        column("Name", comparator = byName) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a comparator built by a qualified call where the column is declared`() {
        assertEquals(
            1,
            lintTable("column(\"Name\", comparator = Comparators.byName()) { it }").size,
        )
    }

    @Test
    fun `does not report a comparator produced by a type cast, which this rule does not follow`() {
        assertEquals(
            0,
            lintTable(
                "column(\"Name\", comparator = String.CASE_INSENSITIVE_ORDER as Comparator<String>) { it }",
            ).size,
        )
    }

    @Test
    fun `follows a local variable through another local variable to what built the comparator`() {
        assertEquals(
            1,
            lintTable(
                """
                val base = compareBy<String> { it }
                val byName = base
                column("Name", comparator = byName) { it }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a comparator held across passes by remember`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.remember
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        val byName = remember { compareBy<String> { it } }
                        column("Name", comparator = byName) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a comparator held by an aliased Compose remember`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.remember as retain
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = retain { compareBy<String> { it } }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a comparator held by fully qualified Compose remember`() {
        assertEquals(
            0,
            lintTable(
                """
                column(
                    "Name",
                    comparator = androidx.compose.runtime.remember { compareBy<String> { it } },
                ) { it }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a comparator held by wildcard imported Compose remember`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import androidx.compose.runtime.*
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = remember { compareBy<String> { it } }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports an unrelated call named remember`() {
        assertEquals(
            1,
            lintTable(
                "column(\"Name\", comparator = remember { compareBy<String> { it } }) { it }",
            ).size,
        )
    }

    @Test
    fun `reports an explicitly imported unrelated remember`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import other.remember
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = remember { compareBy<String> { it } }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a qualified unrelated remember`() {
        assertEquals(
            1,
            lintTable(
                "column(\"Name\", comparator = other.remember { compareBy<String> { it } }) { it }",
            ).size,
        )
    }

    @Test
    fun `reports a local remember function shadowing the Compose import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import androidx.compose.runtime.remember
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    fun <T> remember(block: () -> T): T = block()
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = remember { compareBy<String> { it } }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a local remember property shadowing the Compose import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import androidx.compose.runtime.remember
                import org.jetbrains.compose.swing.components.selection.Table

                fun table() {
                    val remember: (() -> Comparator<String>) -> Comparator<String> = { it() }
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = remember { compareBy<String> { it } }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a member remember function shadowing the Compose import`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import androidx.compose.runtime.remember
                import org.jetbrains.compose.swing.components.selection.Table

                class Screen {
                    fun <T> remember(block: () -> T): T = block()

                    fun table() {
                        Table(rows = emptyList<String>()) {
                            column("Name", comparator = remember { compareBy<String> { it } }) { it }
                        }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }
}

class UnheldColumnComparatorScopeTest {
    private fun lint(source: String) = UnheldColumnComparator(Config.empty).lint(source)

    private fun lintTable(body: String) =
        lint(
            """
            package sample

            import org.jetbrains.compose.swing.components.selection.Table

            fun table() {
                Table(rows = emptyList<String>()) {
                    $body
                }
            }
            """.trimIndent(),
        )

    @Test
    fun `does not report a comparator passed in as a parameter, which outlives the pass`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                fun table(byName: Comparator<String>) {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = byName) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not resolve a parameter to a same named local in a sibling scope`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                fun table(byName: Comparator<String>) {
                    run {
                        val byName = compareBy<String> { it }
                        consume(byName)
                    }
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = byName) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a top-level comparator, which outlives the pass`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                private val byName = compareBy<String> { it }

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = byName) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a positional comparator argument, which a name alone cannot tell apart`() {
        assertEquals(
            0,
            lintTable("column(\"Name\", compareBy<String> { it }) { it }").size,
        )
    }

    @Test
    fun `does not report a call that is not a column declaration`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun sortRows() {
                    reorder(comparator = compareBy<String> { it })
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated local function named column`() {
        assertEquals(
            0,
            lintTable(
                """
                fun column(comparator: Comparator<String>) = comparator
                column(comparator = compareBy<String> { it })
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a column inside a Table imported through an alias`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table as Grid

                fun table() {
                    Grid(rows = emptyList<String>()) {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a column inside a fully qualified Table call`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                fun table() {
                    org.jetbrains.compose.swing.components.selection.Table(rows = emptyList<String>()) {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a column inside a wildcard imported Table`() {
        assertEquals(
            1,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.*

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `reports a column inside Table from its own package`() {
        assertEquals(
            1,
            lint(
                """
                package org.jetbrains.compose.swing.components.selection

                fun table() {
                    Table(rows = emptyList<String>()) {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report an unrelated top-level column function`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                fun column(comparator: Comparator<String>) = comparator

                fun sortRows() {
                    Table(rows = emptyList<String>()) {
                        column(comparator = compareBy<String> { it })
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a qualified unrelated column call`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                fun sortRows(scope: Any) {
                    scope.column(comparator = compareBy<String> { it })
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a column in a nested receiver scope`() {
        assertEquals(
            0,
            lintTable(
                """
                Other().run {
                    column(comparator = compareBy<String> { it })
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `does not report a column inside a same-file Table shadow`() {
        assertEquals(
            0,
            lint(
                """
                package sample

                import org.jetbrains.compose.swing.components.selection.Table

                fun Table(rows: List<String>, block: () -> Unit) = block()

                fun table() {
                    Table(emptyList()) {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    }
                }
                """.trimIndent(),
            ).size,
        )
    }
}
