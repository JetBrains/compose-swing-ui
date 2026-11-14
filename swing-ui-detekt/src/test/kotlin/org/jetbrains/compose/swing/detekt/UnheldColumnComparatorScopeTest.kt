// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class UnheldColumnComparatorScopeTest {
    private fun lint(source: String) = composeSwingRule("UnheldColumnComparator").lint(source)

    private fun lintTable(body: String) =
        lint(
            """
            package sample

            import org.jetbrains.compose.swing.components.selection.Table

            fun table() {
                Table(rows = emptyList<String>(), columns = {
                    $body
                })
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
                    Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = byName) { it }
                    })
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
                    Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = byName) { it }
                    })
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
                    Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = byName) { it }
                    })
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
                    org.jetbrains.compose.swing.components.selection.Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    })
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
                    Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    })
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
                    Table(rows = emptyList<String>(), columns = {
                        column("Name", comparator = compareBy<String> { it }) { it }
                    })
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
                    Table(rows = emptyList<String>(), columns = {
                        column(comparator = compareBy<String> { it })
                    })
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
