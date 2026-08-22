package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A callback that changes between passes reaches the built listener without the listener being removed
 * and added again: the callback is not what the registration is made of, and a registration held in a
 * `val` is the same instance every pass.
 *
 * The callback being no part of the registration is also what lets every slot in a chain adopt what the
 * pass declares, so the chain is applied without being diffed - see
 * [org.jetbrains.compose.swing.modifier.ModifierChainSkipTest]. The callback that fires must be the
 * newest one across that skip, and two live callbacks on one chain must each keep their own.
 *
 * A slot is asked for the element declared in its place by position, so the shape a chain is built in -
 * which way its cells nest, what sits between two callbacks - decides which slot is asked for which
 * element. Each of those shapes is pinned here to leave every slot holding the callback its own
 * element declares.
 */
class LiveCallbackListenerTest {
    private var attachments = 0
    private var removals = 0

    private val counted =
        CallbackRegistration<JButton, () -> Unit, ActionListener>(
            adapter = { current -> ActionListener { current()() } },
            registration =
                ListenerRegistration(
                    { component, listener ->
                        attachments++
                        component.addActionListener(listener)
                    },
                    { component, listener ->
                        removals++
                        component.removeActionListener(listener)
                    },
                ),
        )

    @Test
    fun aChangedCallbackReachesTheListenerWithoutReRegistering() = runComposeSwingTest {
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                // Captured while the chain is built, so each lambda reports the value its own pass
                // declared. A value read when the click fires is the latest one whichever pass wrote
                // the lambda, which cannot tell a stale callback from a live one.
                val captured = declared
                Button(
                    text = "press",
                    onClick = { },
                    modifier = SwingModifier.listener({ reported = captured }, counted),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        assertEquals("first", reported, "the listener reads the callback the first pass declared")

        declared = "second"
        awaitIdle()

        onNodeOfType<JButton>().performClick()
        assertEquals("second", reported, "and the callback the latest pass declares, with no remember")
        assertEquals(1, attachments, "one registration builds one listener for the component's whole life")
        assertEquals(0, removals, "so a fresh callback never costs a detach")
    }

    @Test
    fun aCallbackRebuiltOutsideTheCompositionIsTheOneThatFires() = runComposeSwingTest {
        val pressed = mutableListOf<String>()
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                Button(
                    text = "press",
                    onClick = { },
                    // Built outside the composition, so nothing memoizes it: every pass hands over a
                    // callback of an identity the one before it never had.
                    modifier = SwingModifier.listener(recording(pressed, declared), counted),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        declared = "second"
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("first", "second"), pressed, "each press runs the callback the latest pass built")
        assertEquals(1, attachments, "which reaches the listener already registered")
        assertEquals(0, removals, "so a callback of a new identity never costs a detach")
    }

    @Test
    fun twoLiveCallbacksOnOneChainEachKeepTheirOwn() = runComposeSwingTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        var declared by mutableStateOf("one")
        setContent {
            FlowPanel {
                val captured = declared
                Button(
                    text = "press",
                    onClick = { },
                    // Two declarations on one registration, each with a callback of its own. Both fire, and
                    // the position each occupies is the only thing telling the two apart.
                    modifier =
                        SwingModifier
                            .listener({ first += "first $captured" }, counted)
                            .listener({ second += "second $captured" }, counted),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        declared = "two"
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("first one", "first two"), first, "the leading slot keeps the callback declared for it")
        assertEquals(listOf("second one", "second two"), second, "and the trailing slot keeps its own")
        assertEquals(2, attachments, "each declaration builds one listener for the component's whole life")
        assertEquals(0, removals, "so a fresh callback never costs a detach")
    }

    @Test
    fun aChainNestedEitherWayKeepsEachSlotOnItsOwnCallback() = runComposeSwingTest {
        val fired = mutableMapOf<String, MutableList<String>>()
        var declared by mutableStateOf("one")
        setContent {
            FlowPanel {
                // Read where the chain is built, so a slot left holding an earlier pass's callback
                // reports that pass's value instead of the one declared now.
                val captured = declared
                val record: (String) -> Unit = { slot ->
                    fired.getOrPut(slot) { mutableListOf() } += captured
                }
                Button(
                    text = "left",
                    onClick = { },
                    // Each builder call puts the chain built so far inside a cell carrying the element
                    // that follows it, so three of them nest to the left.
                    modifier =
                        SwingModifier
                            .actionListener { record("left 1") }
                            .actionListener { record("left 2") }
                            .actionListener { record("left 3") },
                )
                Button(
                    text = "right",
                    onClick = { },
                    // The same three elements in the same order, nested the other way: the trailing pair
                    // is built on its own and the leading element is put in front of it.
                    modifier =
                        SwingModifier
                            .actionListener { record("right 1") }
                            .then(
                                SwingModifier
                                    .actionListener { record("right 2") }
                                    .actionListener { record("right 3") },
                            ),
                )
            }
        }

        onNodeWithText("left").performClick()
        onNodeWithText("right").performClick()
        declared = "two"
        awaitIdle()
        onNodeWithText("left").performClick()
        onNodeWithText("right").performClick()

        assertEquals(
            mapOf(
                "left 1" to listOf("one", "two"),
                "left 2" to listOf("one", "two"),
                "left 3" to listOf("one", "two"),
                "right 1" to listOf("one", "two"),
                "right 2" to listOf("one", "two"),
                "right 3" to listOf("one", "two"),
            ),
            fired,
            "every slot fires the callback its own position was last declared with, whichever way the " +
                "chain carrying it nests",
        )
    }

    @Test
    fun aKeyedElementBetweenTwoAdditiveOnesLeavesTheirSlotsInOrder() = runComposeSwingTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        var declared by mutableStateOf("one")
        setContent {
            FlowPanel {
                val captured = declared
                Button(
                    text = "press",
                    onClick = { },
                    // The tooltip holds a slot of its own, which the two callbacks are not counted
                    // among. It declares the same value on every pass, so the chain is adopted rather
                    // than diffed and position is the only thing telling the callbacks apart.
                    modifier =
                        SwingModifier
                            .actionListener { first += captured }
                            .toolTip("steady")
                            .actionListener { second += captured },
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        declared = "two"
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("one", "two"), first, "the slot before the keyed element keeps its own callback")
        assertEquals(listOf("one", "two"), second, "and the slot after it keeps its own")
    }

    @Test
    fun anEmptySegmentInAChainLeavesEachCallbackOnItsOwnSlot() = runComposeSwingTest {
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        var declared by mutableStateOf("one")
        setContent {
            FlowPanel {
                val captured = declared
                Button(
                    text = "press",
                    onClick = { },
                    // A segment that declares nothing - what a condition answering no builds. `then`
                    // drops the empty modifier rather than joining it, so what reaches the component is
                    // the pair of callbacks, and the second of them is the second slot, not the third.
                    modifier =
                        SwingModifier
                            .actionListener { first += captured }
                            .then(SwingModifier)
                            .actionListener { second += captured },
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        declared = "two"
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("one", "two"), first, "the slot before the empty segment keeps its own callback")
        assertEquals(listOf("one", "two"), second, "and the slot after it keeps its own")
    }

    @Test
    fun aCallbackDeclaredWithAChangedChainIsStillTheOneThatFires() = runComposeSwingTest {
        val pressed = mutableListOf<String>()
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                Button(
                    text = "press",
                    onClick = { },
                    // The tooltip changes with the callback, so the chain is diffed rather than skipped:
                    // the callback has to arrive through the diff as well as around it.
                    modifier =
                        SwingModifier
                            .toolTip(declared)
                            .listener(recording(pressed, declared), counted),
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        declared = "second"
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("first", "second"), pressed, "each press runs the callback the latest pass built")
        assertEquals(
            "second",
            onNodeOfType<JButton>().fetch().toolTipText,
            "and the keyed element declared beside it is applied by the diff",
        )
    }

    @Test
    fun aComponentLeavingAHoistedChainLeavesTheOtherComponentOnItAlone() = runComposeSwingTest {
        val hoisted = mutableListOf<String>()
        val own = mutableListOf<String>()
        var moved by mutableStateOf(false)
        // One chain, hoisted and handed to two components at once - what a theme token is. The callback
        // it carries belongs to it, not to either component holding it, and a component declaring
        // another chain takes its new callback with it rather than writing it into this one.
        val shared = SwingModifier.actionListener { hoisted += "hoisted" }
        setContent {
            FlowPanel {
                Button(
                    text = "mover",
                    onClick = { },
                    modifier = if (moved) SwingModifier.actionListener { own += "own" } else shared,
                )
                Button(text = "holder", onClick = { }, modifier = shared)
            }
        }

        moved = true
        awaitIdle()
        onNodeWithText("holder").performClick()
        onNodeWithText("mover").performClick()

        assertEquals(listOf("hoisted"), hoisted, "the component still on the hoisted chain runs its callback")
        assertEquals(listOf("own"), own, "and the one that moved off it runs the callback it moved to")
    }

    @Test
    fun aLiveCallbackListenerLeavingTheChainStopsFiring() = runComposeSwingTest {
        val pressed = mutableListOf<String>()
        var listening by mutableStateOf(true)
        setContent {
            FlowPanel {
                Button(
                    text = "press",
                    onClick = { },
                    modifier =
                        if (listening) {
                            SwingModifier.actionListener { pressed += "pressed" }
                        } else {
                            SwingModifier
                        },
                )
            }
        }

        onNodeOfType<JButton>().performClick()
        listening = false
        awaitIdle()
        onNodeOfType<JButton>().performClick()

        assertEquals(listOf("pressed"), pressed, "a listener the chain no longer declares is removed")
    }

    /**
     * Hands back a callback recording [label] when it runs. It is a plain function rather than anything
     * the composition holds, so each call answers with an instance of its own.
     */
    private fun recording(
        into: MutableList<String>,
        label: String,
    ): () -> Unit = { into += label }
}
