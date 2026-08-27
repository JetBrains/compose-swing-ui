package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JSlider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a two-way declaration costs the composition, and what it still has to deliver.
 *
 * A declaration settled against the value a widget holds is written by the pass that makes it, and the
 * write is the wrapper's own: it is where the declaration was already heading, so nothing about it is
 * news to the scope that declared it and no further pass is due. A move the widget makes for itself is
 * the opposite - it is news, and reaches the caller and the composition alike.
 *
 * The node's own update block is where the passes are counted, because that is the scope a two-way
 * declaration reads the mirror from and so the scope a change invalidates.
 */
class DeclaredValueSettlesInOnePassTest {
    @Test
    fun aDeclarationTheWidgetTakesIsSettledByOnePass() = runComposeSwingTest {
        val passes = intArrayOf(0)
        var declared by mutableIntStateOf(DECLARED)
        setContent { DeclaringNode(declared) { passes[0]++ } }
        awaitIdle()

        val settled = passes[0]
        declared = REDECLARED
        awaitIdle()

        assertEquals(REDECLARED, onNodeOfType<JSlider>().fetch().value, "the widget should hold the new declaration")
        assertEquals(
            settled + 1,
            passes[0],
            "a declaration the widget takes should be settled by the pass that makes it and no other",
        )
    }

    @Test
    fun aDeclarationOntoASilentWidgetIsSettledByOnePass() = runComposeSwingTest {
        val passes = intArrayOf(0)
        var declared by mutableStateOf(FIRST_NAME)
        setContent { SilentNode(declared) { passes[0]++ } }
        awaitIdle()

        val settled = passes[0]
        declared = SECOND_NAME
        awaitIdle()

        assertEquals(SECOND_NAME, onNodeOfType<JLabel>().fetch().name, "the widget should hold the new declaration")
        assertEquals(
            settled + 1,
            passes[0],
            "a declaration the widget takes should be settled by the pass that makes it and no other",
        )
    }

    @Test
    fun aChangeTheWidgetMakesReachesTheCallerAndTheComposition() = runComposeSwingTest {
        val reported = mutableListOf<Int>()
        val declared by mutableIntStateOf(DECLARED)
        setContent {
            Slider(value = declared, onValueChange = { reported += it }, min = MIN, max = MAX)
        }
        awaitIdle()

        val slider = onNodeOfType<JSlider>().fetch()
        assertEquals(DECLARED, slider.value, "the slider should open on what the composition declares")

        // Nothing but the mirror can carry this to the composition: the test writes no state of its own
        // between the change and the pass that answers it.
        slider.value = CHANGED
        awaitIdle()

        assertEquals(listOf(CHANGED), reported, "the change should reach the caller")
        assertEquals(DECLARED, slider.value, "a change the caller does not adopt should be written back")
    }

    @Test
    fun aChangeTheCallerAdoptsStands() = runComposeSwingTest {
        var declared by mutableIntStateOf(DECLARED)
        setContent {
            Slider(value = declared, onValueChange = { declared = it }, min = MIN, max = MAX)
        }
        awaitIdle()

        val slider = onNodeOfType<JSlider>().fetch()
        slider.value = CHANGED
        awaitIdle()

        assertEquals(CHANGED, slider.value, "a change the caller adopts should be left where the user put it")
        assertEquals(CHANGED, declared, "the caller should be holding the value it adopted")
    }

    /**
     * A slider whose value is declared two-way, with [onPass] run once for each pass that declares it.
     *
     * Built here rather than taken from [Slider] so that the passes are countable: [onPass] runs in the
     * node's own scope, which is the scope the mirror is read from. The slider feeds its mirror as every
     * two-way component does, so a settle's write reaches the mirror by both routes - the widget's own
     * notification and the read-back the settle makes.
     */
    @Composable
    private fun DeclaringNode(
        declared: Int,
        onPass: () -> Unit,
    ) {
        val mirror = rememberMirrorState(MIN)
        SwingNode(
            factory = { JSlider(MIN, MAX, MIN).apply { addChangeListener { mirror.observed(value) } } },
            update = {
                onPass()
                declare(declared, mirror, JSlider::getValue, JSlider::setValue)
            },
        )
    }

    /**
     * A widget whose name is declared two-way, with [onPass] run once for each pass that declares it.
     *
     * A name is a property nothing on the widget publishes, so a settle's own read-back is the only route
     * its write reaches the mirror by.
     */
    @Composable
    private fun SilentNode(
        declared: String,
        onPass: () -> Unit,
    ) {
        val mirror = rememberMirrorState<String?>(null)
        SwingNode(
            factory = { JLabel() },
            update = {
                onPass()
                declare(declared, mirror, { name }, { name = it })
            },
        )
    }

    private companion object {
        /** The name the silent widget is declared on first, and the one that replaces it. */
        const val FIRST_NAME = "first"
        const val SECOND_NAME = "second"

        const val MIN = 0
        const val MAX = 100

        /** What the composition declares, and goes on declaring while the widget moves away from it. */
        const val DECLARED = 40

        /** Where the widget is left, away from the declaration. */
        const val CHANGED = 70

        /** The declaration that replaces [DECLARED]. */
        const val REDECLARED = 20
    }
}
