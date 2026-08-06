package org.jetbrains.compose.swing.core

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Proves the behaviour the `ReusableComposeNode` switch buys: a node that is parked/reactivated or
 * moved across recompositions is **recycled**, not recreated. The same backing Swing [Component]
 * instance survives, and the holder's [org.jetbrains.compose.swing.node.SwingNodeHolder.onReuse] /
 * `onDeactivate` has drained the previous node's state so the recycled component reacts to the new
 * content rather than the old.
 *
 * Two genuine reuse paths are pinned to the *same instance*:
 *  - a [ReusableContentHost] child parked (`active = false`) and reactivated, and
 *  - a [movableContentOf] node moved between two slots.
 *
 * Note on `key()`: changing a `key()` argument is an explicit identity change, so the runtime
 * disposes the old keyed group and builds a fresh node with a new component instance - that is NOT a
 * reuse, and [ListenerReattachAfterReuseTest] pins it. These tests pin the *recycling* itself for the
 * paths that truly recycle.
 */
class NodeReuseRecyclesComponentTest {
    /**
     * A [movableContentOf] [Button] moved from NORTH to SOUTH keeps the same `JButton` instance: the
     * runtime relocates the existing node (deactivate in the old slot, reactivate in the new) rather
     * than building a fresh one, and the moved instance still fires its onClick.
     */
    @Test
    fun movingAMovableContentNodeKeepsTheSameComponentInstance() = runComposeSwingTest {
        var counter by mutableIntStateOf(0)
        var inNorth by mutableStateOf(true)

        setContent {
            val button =
                remember {
                    movableContentOf {
                        Button(text = "Movable", onClick = { counter++ })
                    }
                }
            BorderPanel {
                if (inNorth) north { button() } else south { button() }
                center { Button(text = "anchor", onClick = {}) }
            }
        }

        val movable = onNodeWithText("Movable")
        val before = movable.fetch()

        // Move NORTH -> SOUTH. The same node is relocated, not recreated.
        inNorth = false
        awaitIdle()

        assertSame(
            before,
            movable.fetch(),
            "movableContent must relocate the SAME JButton instance across the move, not " +
                "allocate a new one",
        )

        // The relocated instance still reacts.
        movable.performClick()
        assertEquals(1, counter, "the relocated button must dispatch its onClick")
    }

    /**
     * A button parked via [ReusableContentHost] (active = false) and then reactivated keeps the same
     * underlying component instance: deactivation detaches its listeners and reactivation reuses the
     * recycled node rather than building a new one.
     */
    @Test
    fun reusableContentHostReactivationKeepsTheSameComponentInstance() = runComposeSwingTest {
        var counter by mutableIntStateOf(0)
        var active by mutableStateOf(true)

        setContent {
            BorderPanel {
                center {
                    ReusableContentHost(active = active) {
                        Button(text = "Recyclable", onClick = { counter++ })
                    }
                }
            }
        }

        val recyclable = onNodeWithText("Recyclable")
        val before = recyclable.fetch()

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertSame(
            before,
            recyclable.fetch(),
            "A deactivated/reactivated ReusableContentHost child must reuse the same component " +
                "instance",
        )

        // And the recycled instance still reacts.
        recyclable.performClick()
        assertEquals(1, counter, "the recycled button must dispatch its onClick")
    }
}
