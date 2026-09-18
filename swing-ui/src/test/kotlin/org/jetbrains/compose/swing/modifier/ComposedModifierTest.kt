package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [composed] entries are materialized in the composition of the node they reach: the factory's state
 * belongs to that node, and what it returns is applied as if declared in the entry's place.
 */
class ComposedModifierTest {
    @Test
    fun aFactorysRememberIsKeptAcrossPassesAndOwnedByEachComponent() = runComposeSwingTest {
        val created = AtomicInteger()
        var tick by mutableIntStateOf(0)
        val shared = SwingModifier.composed { name("id-${remember { created.incrementAndGet() }}") }
        setContent {
            Label("first $tick", modifier = shared)
            Label("second $tick", modifier = shared)
        }

        repeat(5) {
            tick++
            awaitIdle()
        }

        onNodeWithText("first 5").assertExists()
        onNodeWithName("id-1").assertExists()
        onNodeWithName("id-2").assertExists()
        assertEquals(2, created.get(), "each component remembers once, and a pass never remembers again")
    }

    @Test
    fun aBackgroundAFactoryReturnsIsPaintedAndLeavesWithTheEntry() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            val base = SwingModifier.testTag("composed").opaque(true).preferredSize(32, 32)
            Label(
                "",
                modifier = if (declared) base.composed { background(Color.RED) } else base,
            )
        }
        val undecorated = onNodeWithTag("composed").captureToImage().getRGB(16, 16)

        declared = true
        awaitIdle()
        assertEquals(Color.RED.rgb, onNodeWithTag("composed").captureToImage().getRGB(16, 16))

        declared = false
        awaitIdle()
        assertEquals(
            undecorated,
            onNodeWithTag("composed").captureToImage().getRGB(16, 16),
            "the background the entry declared is restored once the entry leaves the modifier",
        )
    }

    @Test
    fun aMenuItemMaterializesAFactory() = runComposeSwingTest {
        val popup = composeMenu { MenuItem("Cut", onClick = { }, modifier = SwingModifier.composed { name("cut") }) }

        assertEquals("cut", popup.getComponent(0).name, "a menu node applies what the factory returns")
    }

    @Test
    fun aFactoryReturningAnEqualModifierIsNotAppliedAgain() = runComposeSwingTest {
        val updates = AtomicInteger()
        var tick by mutableIntStateOf(0)
        setContent {
            Label("tick $tick", modifier = SwingModifier.composed { this then CountingElement(updates) })
        }

        repeat(5) {
            tick++
            awaitIdle()
        }

        onNodeOfType<JLabel>().assertTextEquals("tick 5")
        assertEquals(1, updates.get(), "an entry materializing to what it materialized last writes nothing again")
    }

    /** Writes the tooltip and counts each write; two built from one counter declare the same thing. */
    private class CountingElement(
        private val updates: AtomicInteger,
    ) : SwingModifier.NodeElement<JComponent, CountingElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override fun create(): Node = Node()

        override fun update(node: Node) {
            updates.incrementAndGet()
            node.component.toolTipText = "counted"
        }

        override fun equals(other: Any?): Boolean = other is CountingElement && updates === other.updates

        override fun hashCode(): Int = System.identityHashCode(updates)

        class Node : SwingModifier.ComponentNode<JComponent>() {
            override fun onDetach() {
                component.toolTipText = null
            }
        }
    }
}
