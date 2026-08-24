package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusTraversalIndex
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Component
import java.awt.GraphicsEnvironment
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.awt.Button as AwtButton
import java.awt.Panel as AwtPanel

private const val FORM_TITLE = "focus-traversal-form"
private const val PANEL_TAG = "panel"

/** Realizes [content] in a window, so the components under test have a peer for the focus cycle. */
private fun ComposeSwingTest.setFormContent(content: @Composable () -> Unit) {
    setContent {
        Window(onCloseRequest = {}, title = FORM_TITLE, visible = true) { content() }
    }
}

/** The control of the realized form reading [text]. */
private fun ComposeSwingTest.formControl(text: String): SwingNodeInteraction<Component> =
    onWindowWithTitle(FORM_TITLE).onNodeWithText(text)

/** The container of the realized form tagged [tag], for one a control's own text cannot name. */
private fun ComposeSwingTest.formContainer(tag: String): SwingNodeInteraction<Component> =
    onWindowWithTitle(FORM_TITLE).onNodeWithTag(tag)

/**
 * Behavioral tests for the focus-traversal modifiers. They drive the real `FocusTraversalPolicy` the
 * container installs and assert the observable Swing wiring rather than the modifier's internal records.
 *
 * Traversal is Swing's, so every case that needs a stop to be judged realizes a window: a component with
 * no peer is never a focus stop, whatever else is declared for it.
 */
class FocusTraversalModifierTest {
    @Test
    fun orderedFocusTraversalVisitsChildrenByIndex() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("third", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(30))
                TextField("first", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(10))
                TextField("second", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(20))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val first = formControl("first").fetch<JTextField>()
        val second = formControl("second").fetch<JTextField>()
        val third = formControl("third").fetch<JTextField>()

        // Despite the declaration order (third, first, second), the policy orders by index.
        assertSame(first, policy.getFirstComponent(panel), "the lowest index starts the traversal order")
        assertSame(second, policy.getComponentAfter(panel, first), "index 20 should follow index 10")
        assertSame(third, policy.getComponentAfter(panel, second), "index 30 should follow index 20")
        assertSame(third, policy.getLastComponent(panel), "the highest index ends the traversal order")
    }

    @Test
    fun orderedFocusTraversalRestoresPolicyOnRemoval() = runComposeSwingTest {
        var ordered by mutableStateOf(true)
        setContent {
            FlowPanel(
                modifier =
                    SwingModifier.testTag(PANEL_TAG).let {
                        if (ordered) it.orderedFocusTraversal() else it
                    },
            ) {
                TextField("", onValueChange = {})
            }
        }
        // A bare FlowPanel is not a focus cycle root and inherits its container's policy; the modifier
        // makes it one and installs the composition-order policy.
        val before = onNodeWithTag(PANEL_TAG).fetch<JPanel>()
        assertTrue(before.isFocusCycleRoot, "the modifier should make the panel a focus cycle root")
        assertTrue(before.isFocusTraversalPolicyProvider, "the modifier should make the panel a policy provider")
        val installed = before.focusTraversalPolicy

        ordered = false
        awaitIdle()

        val after = onNodeWithTag(PANEL_TAG).fetch<JPanel>()
        assertFalse(after.isFocusCycleRoot, "removing the modifier should restore the pre-modifier cycle-root flag")
        assertFalse(
            after.isFocusTraversalPolicyProvider,
            "removing the modifier should restore the pre-modifier policy-provider flag",
        )
        assertNotSame(
            installed,
            after.focusTraversalPolicy,
            "removing the modifier should restore the pre-modifier traversal policy",
        )
    }

    @Test
    fun aFormWithNoPeerHasNoTraversalOrder() = runComposeSwingTest {
        setContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("", onValueChange = {})
            }
        }
        val panel = onNodeWithTag(PANEL_TAG).fetch<JPanel>()
        val field = onNodeOfType<JTextField>().fetch()
        val policy = panel.focusTraversalPolicy

        // The harness root is never attached to a window, so nothing under it is displayable, and a
        // component with no peer cannot be given the keyboard.
        assertFalse(field.isDisplayable, "an unrealized form has no peer")
        assertNull(policy.getFirstComponent(panel), "a form with no peer has no first component")
        assertNull(policy.getLastComponent(panel), "a form with no peer has no last component")
        assertNull(policy.getComponentAfter(panel, field), "a control with no peer is not stepped to")
    }

    @Test
    fun orderedFocusTraversalWrapsAroundInBothDirections() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("first", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(10))
                TextField("second", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(20))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val first = formControl("first").fetch<JTextField>()
        val second = formControl("second").fetch<JTextField>()

        assertSame(first, policy.getComponentAfter(panel, second), "the order should wrap forward to the first")
        assertSame(second, policy.getComponentBefore(panel, first), "the order should wrap backward to the last")
        assertSame(first, policy.getComponentBefore(panel, second), "the previous of the second is the first")
        assertSame(first, policy.getDefaultComponent(panel), "the default component is the first in the order")
    }

    @Test
    fun unindexedChildrenFollowIndexedOnesInDeclarationOrder() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("plainA", onValueChange = {})
                TextField("indexed", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(5))
                TextField("plainB", onValueChange = {})
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val indexed = formControl("indexed").fetch<JTextField>()
        val plainA = formControl("plainA").fetch<JTextField>()
        val plainB = formControl("plainB").fetch<JTextField>()

        assertSame(indexed, policy.getFirstComponent(panel), "an indexed child is visited before un-indexed ones")
        assertSame(plainA, policy.getComponentAfter(panel, indexed), "un-indexed children follow the indexed ones")
        assertSame(plainB, policy.getComponentAfter(panel, plainA), "un-indexed children keep their declaration order")
        assertSame(plainB, policy.getLastComponent(panel), "the last un-indexed child ends the order")
    }

    @Test
    fun disabledAndInvisibleChildrenAreSkipped() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("off", onValueChange = {}, modifier = SwingModifier.enabled(false).focusTraversalIndex(10))
                TextField("hidden", onValueChange = {}, modifier = SwingModifier.visible(false).focusTraversalIndex(20))
                TextField("live", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(30))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val live = formControl("live").fetch<JTextField>()

        // Only a child that can actually take focus takes part in the order, so the sole reachable
        // child is both the first and the last, and stepping from it returns to itself.
        assertSame(live, policy.getFirstComponent(panel), "a disabled or invisible child cannot start the order")
        assertSame(live, policy.getLastComponent(panel), "a disabled or invisible child cannot end the order")
        assertSame(live, policy.getComponentAfter(panel, live), "a single reachable child cycles to itself")
    }

    @Test
    fun aControlUnderADisabledHeavyweightContainerIsSkipped() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val heavyweight = AwtPanel().apply { isEnabled = false }
        val nested = JTextField(5)
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                SwingNode(factory = { heavyweight }) {
                    SwingNode(factory = { nested })
                }
                Button("OK", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val ok = formControl("OK").fetch<JButton>()

        // Disabling a heavyweight container disables everything the window system draws inside it, so
        // its content cannot be given the keyboard and never joins the order. A lightweight container
        // does not disable its children, and Swing keeps those in the order.
        assertFalse(heavyweight.isLightweight, "the case needs a heavyweight container to disable")
        assertTrue(nested.isEnabled, "the nested control is disabled through its container only")
        assertSame(ok, policy.getFirstComponent(panel), "content under a disabled heavyweight cannot start the order")
        assertSame(ok, policy.getLastComponent(panel), "content under a disabled heavyweight cannot end the order")
    }

    @Test
    fun aRawAwtControlIsATraversalStop() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val raw = AwtButton("raw")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                SwingNode(factory = { raw })
                Button("OK", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val ok = formControl("OK").fetch<JButton>()

        // A control that is not a Swing component carries no Swing key bindings, and the window system
        // is asked about it instead. An AWT button reports itself focusable, so Tab stops on it exactly
        // as it does in a hand-written form.
        assertSame(raw, policy.getFirstComponent(panel), "a raw AWT control is a stop")
        assertSame(ok, policy.getComponentAfter(panel, raw), "the Swing control follows it")
    }

    @Test
    fun descendantsOfNestedContainersJoinTheOrder() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                TextField("outer", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(20))
                FlowPanel {
                    TextField("nested", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(10))
                }
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val nested = formControl("nested").fetch<JTextField>()
        val outer = formControl("outer").fetch<JTextField>()

        // The order spans the whole cycle: a grandchild takes its indexed place among the top-level ones.
        assertSame(nested, policy.getFirstComponent(panel), "a nested child joins the order at its index")
        assertSame(outer, policy.getComponentAfter(panel, nested), "the order interleaves nesting levels by index")
    }

    @Test
    fun aNestedCycleRootIsEnteredAsAWhole() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag("outerPanel").orderedFocusTraversal()) {
                TextField("outer", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(20))
                FlowPanel(modifier = SwingModifier.testTag("innerPanel").orderedFocusTraversal()) {
                    TextField("inner", onValueChange = {}, modifier = SwingModifier.focusTraversalIndex(10))
                }
            }
        }
        val outerPanel = formContainer("outerPanel").fetch<JPanel>()
        val innerPanel = formContainer("innerPanel").fetch<JPanel>()
        val outer = formControl("outer").fetch<JTextField>()
        val inner = formControl("inner").fetch<JTextField>()
        val outerPolicy = outerPanel.focusTraversalPolicy

        // A nested ordered container owns its children's traversal: they are not stops of the outer
        // order, which steps to the container as a whole and hands the position on to the policy it
        // owns. A container takes the earliest index declared inside it, so the group is entered where
        // its first member asked to be.
        assertSame(inner, outerPolicy.getFirstComponent(outerPanel), "the outer order enters the nested container")
        assertSame(outer, outerPolicy.getLastComponent(outerPanel), "the outer container's own control ends it")
        assertSame(
            inner,
            innerPanel.focusTraversalPolicy.getFirstComponent(innerPanel),
            "the nested root traverses its own child",
        )
    }

    @Test
    fun aContainerWithoutFocusableChildrenHasNoTraversalOrder() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Label("caption", modifier = SwingModifier.focusable(false))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val caption = formControl("caption").fetch<JLabel>()

        assertNull(policy.getFirstComponent(panel), "a container with no focusable child has no first component")
        assertNull(policy.getLastComponent(panel), "a container with no focusable child has no last component")
        assertNull(policy.getComponentAfter(panel, caption), "there is nothing to step forward to")
        assertNull(policy.getComponentBefore(panel, caption), "there is nothing to step back to")
    }

    @Test
    fun focusableModifierLeavesComponentReachableForTraversal() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Button("A", onClick = { }, modifier = SwingModifier.focusable(true).focusTraversalIndex(1))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val a = formControl("A").fetch<JButton>()
        assertSame(
            a,
            panel.focusTraversalPolicy.getFirstComponent(panel),
            "a control declared focusable is a stop of the order",
        )
    }

    @Test
    fun captionsAndLayoutContainersAreNotTraversalStops() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Label("Name:")
                FlowPanel {
                    TextField("name", onValueChange = {})
                }
                Button("OK", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val field = formControl("name").fetch<JTextField>()
        val ok = formControl("OK").fetch<JButton>()

        // Tabbing visits only the real controls; the caption and the nested layout panel are stepped over.
        assertSame(field, policy.getFirstComponent(panel), "the text field is the first control")
        assertSame(ok, policy.getComponentAfter(panel, field), "the button follows the text field directly")
        assertSame(field, policy.getComponentAfter(panel, ok), "traversal wraps back to the first control")
        assertSame(ok, policy.getLastComponent(panel), "the button is the last control")
    }

    @Test
    fun declaredFocusabilityOverridesTheDefaultJudgement() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Label("Legend", modifier = SwingModifier.focusable(true))
                Button("Skipped", onClick = { }, modifier = SwingModifier.focusable(false))
                Button("OK", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val legend = formControl("Legend").fetch<JLabel>()
        val ok = formControl("OK").fetch<JButton>()

        assertSame(legend, policy.getFirstComponent(panel), "the label declared focusable is a stop")
        assertSame(ok, policy.getComponentAfter(panel, legend), "the button declared non-focusable is skipped")
        assertSame(ok, policy.getLastComponent(panel), "only the label and the remaining button are stops")
    }

    @Test
    fun aDeclaredFocusableChildStillHasToBeAbleToTakeFocus() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Label("Legend", modifier = SwingModifier.focusable(true).enabled(false))
                Button("OK", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val ok = formControl("OK").fetch<JButton>()

        // The declaration decides whether a component counts as a control, not whether the keyboard can
        // reach it: a disabled one stays out of the order either way.
        assertSame(ok, policy.getFirstComponent(panel), "a disabled child declared focusable is still skipped")
        assertSame(ok, policy.getLastComponent(panel), "it cannot end the order either")
    }

    @Test
    fun disabledAndInvisibleControlsAreSkipped() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Button("A", onClick = { })
                Button("B", onClick = { }, modifier = SwingModifier.enabled(false))
                Button("C", onClick = { }, modifier = SwingModifier.visible(false))
                Button("D", onClick = { })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val a = formControl("A").fetch<JButton>()
        val d = formControl("D").fetch<JButton>()

        assertSame(a, policy.getFirstComponent(panel), "the first reachable button starts the order")
        assertSame(d, policy.getComponentAfter(panel, a), "the disabled and invisible buttons are skipped")
        assertSame(a, policy.getComponentBefore(panel, d), "backward traversal skips them as well")
    }

    @Test
    fun onlyToggleButtonsCompeteToBeTheirGroupsEntryPoint() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val group = ButtonGroup()
        val push = JButton("push")
        val firstOption = JRadioButton("first")
        val secondOption = JRadioButton("second")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                SwingNode(factory = { push.also(group::add) })
                SwingNode(factory = { firstOption.also(group::add) })
                SwingNode(factory = { secondOption.also(group::add) })
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy

        // Tab enters a group of toggle buttons once, at its first member, and the arrow keys move
        // within it. Only toggle buttons take part in that: an ordinary button sharing the group is a
        // stop in its own right and does not stand in for the group.
        assertSame(push, policy.getFirstComponent(panel), "an ordinary button in a group is a stop of its own")
        assertSame(firstOption, policy.getComponentAfter(panel, push), "the first toggle button is the entry point")
        assertSame(firstOption, policy.getLastComponent(panel), "the remaining toggle buttons are not stops")
    }

    @Test
    fun steppingBackwardFromAComponentOutsideTheOrderContinuesFromItsEnd() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setFormContent {
            FlowPanel(modifier = SwingModifier.testTag(PANEL_TAG).orderedFocusTraversal()) {
                Button("A", onClick = { })
                Button("B", onClick = { })
                Button("C", onClick = { })
                FlowPanel(modifier = SwingModifier.testTag("group"))
            }
        }
        val panel = formContainer(PANEL_TAG).fetch<JPanel>()
        val policy = panel.focusTraversalPolicy
        val c = formControl("C").fetch<JButton>()
        val group = formContainer("group").fetch<JPanel>()

        assertNotSame(group, policy.getFirstComponent(panel), "a layout container is not a stop")
        assertSame(
            c,
            policy.getComponentBefore(panel, group),
            "stepping back from outside the order resumes at its last stop",
        )
    }
}
