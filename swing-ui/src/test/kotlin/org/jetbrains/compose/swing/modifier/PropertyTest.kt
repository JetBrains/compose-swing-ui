package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The public builder a custom component declares a Swing property through: it writes the declared value
 * while the declaration stands, and puts back what the component was found holding once it leaves.
 *
 * A caller states through `restores` whether the component is held to carrying that value again, and
 * may name a `rewriteOn` bean property whose announced change means the declared value was overwritten.
 */
class PropertyTest {
    @Test
    fun aDeclaredPropertyIsWrittenAndHandedBackFromTheWidget() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JLabel("target").apply { toolTipText = WIDGET_TIP } },
                modifier = if (declared) SwingModifier.declaredTip(DECLARED_TIP) else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(DECLARED_TIP, label.fetch().toolTipText, "the declared value should reach the widget")

        declared = false
        awaitIdle()
        assertEquals(
            WIDGET_TIP,
            label.fetch().toolTipText,
            "withdrawing the declaration should hand back what the widget was carrying",
        )
    }

    @Test
    fun aDeclaredPropertyOnlyPolicyOwesBackOnlyItsOwnName() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel("target").apply {
                        toolTipText = WIDGET_TIP
                        name = WIDGET_NAME
                    }
                },
                modifier = if (declared) SwingModifier.declaredTipDerivingName(DECLARED_TIP) else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(DECLARED_TIP, label.toolTipText, "the declared value should reach the widget")

        declared = false
        // The write derives `name` from the tooltip too, the way a look and feel derives a property of
        // its own from a write. RestorePolicy.DeclaredPropertyOnly holds the departing slot to its own
        // name ("toolTipText") alone, so the restore check the harness runs does not fail even though
        // `name` is left standing at what the write derived rather than at what it carried before.
        awaitIdle()
        assertEquals(WIDGET_TIP, label.toolTipText, "the declared property is still put back")
        assertEquals(
            "derived from $WIDGET_TIP",
            label.name,
            "a property the policy does not own is left as the write derived it, not restored",
        )
    }

    @Test
    fun rewriteOnReassertsTheDeclaredValueWhenTheNamedPropertyAnnouncesAChange() = runComposeSwingTest {
        setContent {
            SwingNode(
                factory = { JLabel("target") },
                modifier = SwingModifier.property(DeclaredNameProperty, DECLARED_TIP, rewriteOn = "name"),
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(DECLARED_TIP, label.name, "the declared value should reach the widget")

        // Something outside the modifier overwrites the property and announces the change, the way a
        // look and feel does; the node listens for the property it declares to be announced - "name"
        // reliably fires a PropertyChangeEvent of that exact name, unlike "toolTipText", which Swing
        // announces under the differently-cased client-property key "ToolTipText" - and writes the
        // declared value again.
        label.name = "elsewhere"
        awaitIdle()
        assertEquals(
            DECLARED_TIP,
            label.name,
            "an external overwrite of a rewriteOn property is reasserted",
        )
    }
}

/** The property [rewriteOnReassertsTheDeclaredValueWhenTheNamedPropertyAnnouncesAChange] declares and listens on. */
private val DeclaredNameProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "name",
        read = { it.name },
        write = { label, value -> label.name = value },
    )

/**
 * The property both declarations here write through, built once so they share one slot, the way
 * [SwingModifier.property]'s documentation directs a custom component to declare a property.
 */
private val ToolTipProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "toolTipText",
        read = { it.toolTipText },
        write = { label, value -> label.toolTipText = value },
    )

private fun SwingModifier.declaredTip(tip: String): SwingModifier = property(ToolTipProperty, tip)

/**
 * Writes the tooltip and, standing in for a look and feel that derives a property of its own from a
 * write, derives the component's name from it too.
 */
private val ToolTipDerivingNameProperty =
    ComponentPropertyDescriptor<JLabel, String?>(
        name = "toolTipText",
        read = { it.toolTipText },
        write = { label, value ->
            label.toolTipText = value
            label.name = "derived from $value"
        },
    )

private fun SwingModifier.declaredTipDerivingName(tip: String): SwingModifier =
    property(ToolTipDerivingNameProperty, tip, restores = RestorePolicy.DeclaredPropertyOnly)

/** What the widget is built carrying, so a restore has something of the widget's own to hand back. */
private const val WIDGET_TIP = "carried by the widget"

private const val DECLARED_TIP = "named by the declaration"

/** What the widget is built named, so leaving a derived name standing is a difference from this. */
private const val WIDGET_NAME = "named by the widget"
