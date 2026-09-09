package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class UnusedTransitionTargetStateTest {
    private fun lint(source: String) = UnusedTransitionTargetState(Config.empty).lint(source)

    private fun assertReported(source: String) =
        assertEquals(1, lint(source).size, "expected one finding for:\n$source")

    private fun assertClean(source: String) =
        assertEquals(emptyList(), lint(source), "expected no finding for:\n$source")

    @Test
    fun `reports a lambda that ignores the state it is handed`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { if (visible) 1f else 0f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a lambda that names the state and never reads it`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateInt { state -> if (visible) 1 else 0 }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a lambda that discards the state`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateValue(converter) { _ -> Offset }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports an empty lambda`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a call made without a receiver`() {
        assertReported(
            """
            package sample

            fun Transition<State>.show() {
                animateFloat(label = "alpha") { 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a lambda that reads the state through it`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat { if (it == Visible) 1f else 0f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a lambda that reads the state by name`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateInt { state -> state.size }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a lambda that reads the state it is destructured into`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat { (alpha, _) -> alpha }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an animation spec that precedes the state lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat({ spring() }) { it.alpha }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a call that takes no lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat(converter, spec, label, targetValueByState)
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a call to something else that takes a lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                animatable.animateTo(1f) { println("done") }
                animateFloatAsState(1f) { println("done") }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports an animateColor lambda that ignores the state`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateColor { if (visible) Red else Blue }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an animateColor lambda that reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateColor { if (it) Red else Blue }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an infinite transition color animation`() {
        assertClean(
            """
            package sample

            fun show() {
                infinite.animateColor(Red, Blue, spec) 
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a call whose name only starts with animate`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateOffset { Offset }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an it read only by a nested lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat { items.forEach { use(it) }; 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a name shadowed by a nested lambda parameter`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { state -> items.forEach { state -> use(state) }; 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a name shadowed by a nested function parameter`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { state -> fun (state: Int) = state; 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a name that is only a qualified selector`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { state -> other.state }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a read of the name before a nested lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat { state -> items.forEach { use(state) }; 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over an it read beside a nested lambda`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat { items.forEach { use(it) }; if (it) 1f else 0f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a lambda that declares the state as an underscore`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat { _ -> 1f }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `reports a state lambda passed under its name`() {
        assertReported(
            """
            package sample

            fun show() {
                transition.animateFloat(label = "alpha", targetValueByState = { if (visible) 1f else 0f })
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `passes over a state lambda passed under its name that reads the state`() {
        assertClean(
            """
            package sample

            fun show() {
                transition.animateFloat(label = "alpha", targetValueByState = { if (it) 1f else 0f })
            }
            """.trimIndent(),
        )
    }
}
