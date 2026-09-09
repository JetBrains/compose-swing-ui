package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnusedAnimatedContentTargetStateTest {
    private fun lint(source: String) = UnusedAnimatedContentTargetState(Config.empty).lint(source)

    private fun assertReported(source: String) =
        assertEquals(1, lint(source).size, "expected one finding for:\n$source")

    private fun assertClean(source: String) =
        assertEquals(emptyList(), lint(source), "expected no finding for:\n$source")

    @Test
    fun `reports content that ignores the state it is handed`() {
        assertReported(
            """
            package sample

            fun show() {
                AnimatedContent(page) { Label(title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports content passed under its name`() {
        assertReported(
            """
            package sample

            fun show() {
                AnimatedContent(page, content = { Label(title) })
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a content key that ignores the state it is handed`() {
        val findings =
            lint(
                """
                package sample

                fun show() {
                    AnimatedContent(page, contentKey = { current }) { Label(it.title) }
                }
                """.trimIndent(),
            )

        assertEquals(1, findings.size)
        assertTrue(findings.single().message.contains("makes two states one content"))
    }

    @Test
    fun `reports content and its key separately`() {
        assertEquals(
            2,
            lint(
                """
                package sample

                fun show() {
                    AnimatedContent(page, contentKey = { current }) { Label(title) }
                }
                """.trimIndent(),
            ).size,
        )
    }

    @Test
    fun `passes over content that reads the state through it`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page) { Label(it.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over content that reads the state by name`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page) { target -> Label(target.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a content key that reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page, contentKey = { it.id }) { Label(it.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a lambda taking more than one value`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page) { first, second -> Label(title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a content key that is not a lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page, contentKey = ::identity) { Label(it.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a call that takes no content`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a call to something else`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedVisibility(visible) { Label(title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a crossfade whose content ignores the state`() {
        assertReported(
            """
            package sample

            fun show() {
                Crossfade(page) { Label(title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a transition crossfade whose content ignores the state`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.Crossfade { Label(title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a transition crossfade whose content key ignores the state`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.Crossfade(contentKey = { current }) { Label(it.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a crossfade that reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                Crossfade(page) { Label(it.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a transition crossfade that reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.Crossfade { page -> Label(page.title) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over content that reads it only inside a nested lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page) { items.forEach { use(it) } }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an animated content whose nested content lambda reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                AnimatedContent(page) { Row { Label(it.title) } }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a crossfade whose nested content lambda reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                Crossfade(page) { Panel { Label(it.title) } }
            }
            """.trimIndent(),
        )
    }
}
