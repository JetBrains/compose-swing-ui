package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Pins listener liveness across every path that swaps out the composable behind a Swing component.
 *
 * The contract under test: whichever way the runtime re-homes a node - recycling the existing
 * component or building a new one - the resulting live component ends up with exactly one framework
 * listener attached, so clicks keep dispatching to the currently composed `onClick`.
 *
 * Three paths are asserted live: a **movableContent** move, a **ReusableContentHost**
 * deactivate/reactivate cycle, and a **`key()`** change. The first two recycle the component
 * instance; a `key()` change is an explicit identity change, so the runtime discards the old node and
 * builds a fresh component. All three must dispatch clicks afterwards.
 */
class ListenerReattachAfterReuseTest {
    /**
     * Moves a single [Button] between two BorderLayout regions via [movableContentOf]. The move
     * relocates the underlying node, and the relocated button must still increment on click.
     */
    @Test
    fun movableButtonStillFiresAfterBeingMoved() = runComposeSwingTest {
        var counter by mutableIntStateOf(0)
        var inNorth by mutableStateOf(true)

        setContent {
            val button =
                remember {
                    movableContentOf<SwingModifier> { modifier ->
                        Button(text = "Move me", onClick = { counter++ }, modifier = modifier)
                    }
                }
            BorderPanel {
                if (inNorth) button(SwingModifier.north()) else button(SwingModifier.south())
                Label(text = "anchor", modifier = SwingModifier.center())
            }
        }

        val button = onNodeWithText("Move me")
        button.performClick()
        assertEquals(1, counter, "precondition: the button must dispatch clicks before the move")

        // Force the move (NORTH -> SOUTH). The same component instance is reused in the new region.
        inNorth = false
        awaitIdle()

        button.performClick()
        assertEquals(2, counter, "the moved button must still dispatch its onClick")
    }

    /**
     * A button deactivated and reactivated via [ReusableContentHost] keeps a live listener: the
     * reactivating recomposition re-applies the modifier chain onto the recycled component. Asserts
     * the click still fires after a deactivate/reactivate cycle.
     */
    @Test
    fun deactivatedButtonReattachesListenerOnReactivation() = runComposeSwingTest {
        var counter by mutableIntStateOf(0)
        var active by mutableStateOf(true)

        setContent {
            BorderPanel {
                ReusableContentHost(active = active) {
                    Button(text = "Reusable", onClick = { counter++ }, modifier = SwingModifier.center())
                }
            }
        }

        val button = onNodeWithText("Reusable")
        button.performClick()
        assertEquals(1, counter, "precondition: the button must dispatch clicks before deactivation")

        active = false
        awaitIdle()

        active = true
        awaitIdle()

        button.assertExists().performClick()
        assertEquals(2, counter, "the reactivated button must still dispatch its onClick")
    }

    /**
     * Changing the [androidx.compose.runtime.key] argument is an explicit identity change: the runtime
     * discards the old keyed group and builds a fresh component, which must be wired to the newly
     * composed onClick.
     */
    @Test
    fun keyChangeBuildsANewComponentBoundToTheNewOnClick() = runComposeSwingTest {
        var counterA by mutableIntStateOf(0)
        var counterB by mutableIntStateOf(0)
        var useA by mutableStateOf(true)

        setContent {
            BorderPanel {
                KeyedButton(
                    key = if (useA) "A" else "B",
                    onClick = { if (useA) counterA++ else counterB++ },
                    modifier = SwingModifier.center(),
                )
            }
        }

        val button = onNodeWithText("Keyed")
        val before = button.fetch()
        button.performClick()
        assertEquals(1, counterA, "precondition: the button must dispatch clicks under the first key")
        assertEquals(0, counterB, "precondition: only the first key's onClick may run before the swap")

        useA = false
        awaitIdle()

        val after = button.fetch()
        assertNotSame(
            before,
            after,
            "a key() change must build a fresh component instance rather than recycle the old one",
        )

        button.performClick()
        assertEquals(1, counterB, "the component built for the new key must dispatch the new onClick")
        assertEquals(1, counterA, "the discarded key's onClick must no longer run")
    }
}

@Composable
private fun KeyedButton(
    key: String,
    onClick: () -> Unit,
    modifier: SwingModifier,
) {
    androidx.compose.runtime.key(key) {
        Button(text = "Keyed", onClick = onClick, modifier = modifier)
    }
}
