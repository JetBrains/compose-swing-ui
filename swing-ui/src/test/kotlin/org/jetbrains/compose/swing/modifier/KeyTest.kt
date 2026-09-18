package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * What a modifier's [key] token is worth: while it stands the modifier is diffed as usual, and a token that
 * changes takes every slot apart and applies the modifier again from scratch.
 *
 * The derivation the rebuild exists for is stood in for by a listener of the test's own, so the case
 * says the same thing whichever look and feel works a property out and from which write.
 */
class KeyTest {
    private val passes = 5

    @Test
    fun aChangedTokenTakesTheChainApartAndAppliesItAgain() = runComposeSwingTest {
        val counts = ProbeCounts()
        var token by mutableStateOf(0)
        var declared by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JLabel("X").apply { toolTipText = FOUND } },
                modifier = if (declared) SwingModifier.key(token).then(ProbeElement(counts)) else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>().fetch()
        assertEquals(1, counts.attaches.get(), "the modifier's one slot attaches on the first apply")
        assertEquals(PROBE, label.toolTipText, "and writes what it declares")

        token = 1
        awaitIdle()
        assertEquals(1, counts.detaches.get(), "a changed token should take the standing slot apart")
        assertEquals(2, counts.attaches.get(), "and attach it afresh")
        assertEquals(PROBE, label.toolTipText, "the rebuilt modifier declares what it declared before")

        declared = false
        awaitIdle()
        assertEquals(2, counts.detaches.get(), "dropping the modifier takes the rebuilt slot apart")
        assertEquals(
            FOUND,
            label.toolTipText,
            "the slot re-attached after the restore, so it captured the value the modifier found, not its own write",
        )
    }

    @Test
    fun aChangedTokenAttachesAndDetachesWhereOnlyOneSideDeclaresASlot() = runComposeSwingTest {
        val counts = ProbeCounts()
        var token by mutableStateOf(0)
        var declared by mutableStateOf(false)
        setContent {
            val chain = SwingModifier.key(token)
            SwingNode(
                factory = { JLabel("X").apply { toolTipText = FOUND } },
                modifier = if (declared) chain.then(ProbeElement(counts)) else chain,
            )
        }
        val label = onNodeOfType<JLabel>().fetch()

        token = 1
        awaitIdle()
        assertEquals(0, counts.attaches.get(), "a token changing over no slot attaches nothing")

        token = 2
        declared = true
        awaitIdle()
        assertEquals(1, counts.attaches.get(), "a changed token over no standing slot attaches the declared one")
        assertEquals(PROBE, label.toolTipText, "which writes what it declares")

        token = 3
        declared = false
        awaitIdle()
        assertEquals(1, counts.detaches.get(), "a changed token declaring no slot takes the standing one apart")
        assertEquals(1, counts.attaches.get(), "and attaches nothing")
        assertEquals(FOUND, label.toolTipText, "the detached slot puts back what the component carried")
    }

    @Test
    fun anUnchangedTokenLeavesEverySlotStanding() = runComposeSwingTest {
        val counts = ProbeCounts()
        var tick by mutableStateOf(0)
        var color by mutableStateOf(Color.BLUE)
        setContent {
            Label("tick $tick", modifier = SwingModifier.key(THEME).background(color).then(ProbeElement(counts)))
        }
        val label = onNodeOfType<JLabel>().fetch()

        repeat(passes) {
            tick++
            awaitIdle()
        }

        assertEquals("tick $passes", label.text, "the label must re-execute on every pass")
        assertEquals(
            1,
            counts.updates.get(),
            "a modifier declaring what it declared last is adopted, not written again",
        )

        // A modifier that did change is diffed, and the token standing still is what leaves its slots where
        // they are.
        color = Color.GREEN
        awaitIdle()
        assertEquals(Color.GREEN, label.background, "the changed declaration must reach the component")
        assertEquals(1, counts.attaches.get(), "an unchanged token attaches nothing afresh")
        assertEquals(0, counts.detaches.get(), "and takes nothing apart")
    }

    @Test
    fun aChangedTokenRunsADerivationAnUnchangedDeclarationSwallows() = runComposeSwingTest {
        val styleKey = "JComponent.sizeVariant"
        var token by mutableStateOf(0)
        var tick by mutableStateOf(0)
        var styled by mutableStateOf(false)
        setContent {
            val chain = SwingModifier.key(token)
            Label("tick $tick", modifier = if (styled) chain.clientProperty(styleKey, "small") else chain)
        }
        val label = onNodeOfType<JLabel>().fetch()
        val original = label.font
        // A look and feel that works the font out from a styling key, over a theme that moves under a
        // component whose declarations do not.
        var themed = original.deriveFont(original.size2D + 2f)
        label.addPropertyChangeListener(styleKey) { label.font = themed }

        styled = true
        awaitIdle()
        assertEquals(themed, label.font, "the deriving stand-in should have worked the font out from the key")

        val stale = label.font
        themed = original.deriveFont(original.size2D + 6f)
        tick++
        awaitIdle()
        assertEquals(
            stale,
            label.font,
            "a declaration writing the value already standing announces nothing, so the derivation is swallowed",
        )

        token = 1
        awaitIdle()
        assertEquals(themed, label.font, "the rebuild should have made the derivation run again")
    }

    @Test
    fun keysAtEveryPositionAccumulateAndARepeatedTokenCountsOnce() = runComposeSwingTest {
        val counts = ProbeCounts()
        var leading by mutableStateOf(0)
        var trailing by mutableStateOf(1)
        var duplicated by mutableStateOf(false)
        setContent {
            var chain = SwingModifier.key(leading).then(ProbeElement(counts)).key(trailing)
            if (duplicated) chain = chain.key(trailing)
            SwingNode(factory = { JLabel("X") }, modifier = chain)
        }
        assertEquals(1, counts.attaches.get())

        // key(a).probe.key(b) accumulates both tokens regardless of which side of the element they
        // stand on: a change to either rebuilds the chain, the way key(a, b) would.
        leading = 2
        awaitIdle()
        assertEquals(1, counts.detaches.get(), "a token declared ahead of the element should still rebuild the chain")
        assertEquals(2, counts.attaches.get())

        trailing = 3
        awaitIdle()
        assertEquals(2, counts.detaches.get(), "a token declared after the element should rebuild the chain too")
        assertEquals(3, counts.attaches.get())

        // Repeating the token already standing, unchanged, adds nothing new to what the chain is tied
        // to: one key declared twice counts once.
        duplicated = true
        awaitIdle()
        assertEquals(
            2,
            counts.detaches.get(),
            "repeating a token that already stands and has not changed must not rebuild the chain",
        )
        assertEquals(3, counts.attaches.get())
    }

    @Test
    fun aKeyDeclaredAgainStandsWhereItWasDeclaredLast() = runComposeSwingTest {
        val counts = ProbeCounts()
        var repeated by mutableStateOf(true)
        setContent {
            // key(a).key(b).key(a) ties the modifier to the same thing as key(b).key(a): the repeated key
            // counts once and stands where it was declared last. Dropping the leading declaration
            // therefore changes nothing, and a modifier tied to what it was tied to is not rebuilt.
            var chain = SwingModifier.key("a")
            if (!repeated) chain = SwingModifier
            SwingNode(
                factory = { JLabel("X") },
                modifier = chain.key("b").key("a").then(ProbeElement(counts)),
            )
        }
        assertEquals(1, counts.attaches.get())

        repeated = false
        awaitIdle()
        assertEquals(
            0,
            counts.detaches.get(),
            "dropping a key that was declared again later leaves the modifier tied to what it was",
        )
        assertEquals(1, counts.attaches.get())
    }

    @Test
    fun aChangedTokenRebuildsTheObjectASlotBinds() = runComposeSwingTest {
        val caret = CountingCaret()
        var token by mutableStateOf(0)
        setContent {
            SwingNode(
                factory = { JTextField("text") },
                modifier = SwingModifier.key(token).then(CaretElement(caret)),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertSame(caret, field.caret, "the slot binds its caret to the component")
        assertEquals(1, caret.installs.get(), "which installs it once")
        field.caretPosition = 2

        token = 1
        awaitIdle()
        assertEquals(1, caret.deinstalls.get(), "a changed token takes the bound caret off the component")
        assertEquals(2, caret.installs.get(), "and installs it again")
        assertSame(caret, field.caret, "the rebuilt slot binds the same caret")
        assertEquals(0, field.caretPosition, "a rebuild is a teardown, so what the bound caret held does not survive")
    }

    private class ProbeCounts {
        val attaches: AtomicInteger = AtomicInteger()
        val detaches: AtomicInteger = AtomicInteger()
        val updates: AtomicInteger = AtomicInteger()
    }

    /**
     * A property element writing the target's tooltip and reporting what its slot's lifecycle costs: the
     * attach that captures the value the modifier found, the write, and the detach that puts it back.
     *
     * Two elements built from one [counts] declare the same thing and are equal, so the element never
     * defeats a skip it is measuring.
     */
    private class ProbeElement(
        private val counts: ProbeCounts,
    ) : SwingModifier.NodeElement<JComponent, ProbeElement.Node>() {
        override val targetType: Class<JComponent> get() = JComponent::class.java

        override val name: String get() = "toolTipText"

        override fun create(): Node = Node(counts)

        override fun update(node: Node) {
            counts.updates.incrementAndGet()
            node.write(PROBE)
        }

        override fun equals(other: Any?): Boolean = other is ProbeElement && counts === other.counts

        override fun hashCode(): Int = System.identityHashCode(counts)

        class Node(
            private val counts: ProbeCounts,
        ) : SwingModifier.ComponentNode<JComponent>() {
            private var found: String? = null

            override fun onAttach() {
                counts.attaches.incrementAndGet()
                found = component.toolTipText
            }

            fun write(text: String) {
                component.toolTipText = text
            }

            override fun onDetach() {
                counts.detaches.incrementAndGet()
                component.toolTipText = found
            }
        }
    }

    private class CaretElement(
        private val caret: DefaultCaret,
    ) : SwingModifier.NodeElement<JTextComponent, CaretElement.Node>() {
        override val targetType: Class<JTextComponent> get() = JTextComponent::class.java

        override val name: String get() = "caret"

        /** A caret is where the component's own is handed over; where it stood is the caret's, not the slot's. */
        override val restores: RestorePolicy get() = RestorePolicy.None

        override fun create(): Node = Node(caret)

        override fun update(node: Node) {
            node.bind()
        }

        override fun equals(other: Any?): Boolean = other is CaretElement && caret === other.caret

        override fun hashCode(): Int = System.identityHashCode(caret)

        class Node(
            private val caret: DefaultCaret,
        ) : SwingModifier.ComponentNode<JTextComponent>() {
            private var found: Caret? = null

            override fun onAttach() {
                found = component.caret
            }

            fun bind() {
                component.caret = caret
            }

            override fun onDetach() {
                component.caret = found
            }
        }
    }

    private class CountingCaret : DefaultCaret() {
        val installs: AtomicInteger = AtomicInteger()
        val deinstalls: AtomicInteger = AtomicInteger()

        override fun install(component: JTextComponent) {
            installs.incrementAndGet()
            super.install(component)
        }

        override fun deinstall(component: JTextComponent) {
            deinstalls.incrementAndGet()
            super.deinstall(component)
        }
    }

    private companion object {
        /** The tooltip the component carries before any modifier reaches it. */
        const val FOUND = "found"

        const val PROBE = "probe"

        /** A token standing for what a caller ties a modifier to - the theme its look and feel derives from. */
        const val THEME = "theme"
    }
}
