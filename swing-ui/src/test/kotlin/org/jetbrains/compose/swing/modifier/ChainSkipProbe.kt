// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JTree
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How many ticks a chain-skip probe drives its component through. */
const val CHAIN_SKIP_PASSES: Int = 20

/**
 * Recomposes a sibling of a component whose own parameters stand still and whose chain ends in what
 * [declare] builds on every pass, and asserts the component is executed once only: a chain equal to
 * the one passed last is a parameter the runtime skips the component for.
 */
fun assertSkippedWhole(
    kind: ProbeKind,
    declare: SwingModifier.() -> SwingModifier,
): Unit = assertComponentPasses(kind, { declare() }) { 1 }

/**
 * As [assertSkippedWhole], with [declare] handed the pass's tick to build a callback of a new identity
 * from, and asserts the component is executed on every pass that declares it.
 */
fun assertReExecutedForANewCallback(
    kind: ProbeKind,
    declare: SwingModifier.(tick: Int) -> SwingModifier,
): Unit = assertComponentPasses(kind, declare) { declarations -> declarations }

/**
 * Asserts the component is executed once per tick, for a builder taking [callbackCount] callbacks:
 * `at(index)` yields the callback for the index-th of them, and each tick gives exactly one of them a new
 * identity, every index in turn.
 */
fun <C : Any> assertReExecutedForEachNewCallback(
    kind: ProbeKind,
    callbackCount: Int,
    newCallback: (generation: Int) -> C,
    declare: SwingModifier.(at: (index: Int) -> C) -> SwingModifier,
) {
    val callbacks = HashMap<Pair<Int, Int>, C>()
    assertComponentPasses(
        kind,
        { tick ->
            declare { index ->
                val generation = (tick + callbackCount - 1 - index) / callbackCount
                callbacks.getOrPut(index to generation) { newCallback(generation) }
            }
        },
    ) { CHAIN_SKIP_PASSES + 1 }
}

/**
 * Drives a component whose chain ends in what [declare] builds through every tick and asserts the number of
 * times it was executed is what [expectedPasses] answers for the number of times its content declared it.
 */
fun assertComponentPasses(
    kind: ProbeKind,
    declare: SwingModifier.(tick: Int) -> SwingModifier,
    expectedPasses: (declarations: Int) -> Int,
): Unit = runComposeSwingTest {
    val counts = ChainCounts()
    val declarations = AtomicInteger()
    var tick by mutableIntStateOf(0)
    setContent {
        declarations.incrementAndGet()
        Label("tick $tick")
        ProbePanel(
            tick = 0,
            counts = counts,
            modifier = SwingModifier.then(ChainProbeElement(counts)).declare(tick),
            kind = kind,
        )
    }

    repeat(CHAIN_SKIP_PASSES) {
        tick++
        awaitIdle()
    }

    assertTrue(
        declarations.get() >= CHAIN_SKIP_PASSES + 1,
        "every tick must re-execute the content that declares the component",
    )
    assertEquals(
        expectedPasses(declarations.get()),
        counts.passes.get(),
        "the component is skipped exactly when its chain declares the callbacks it declared last",
    )
}

/** A callback that captures [tick], so each pass's is of a new identity. */
fun callbackOfPass(tick: Int): (Any) -> Unit = { check(tick >= 0) }

/** An answer that captures [tick], so each pass's is of a new identity. */
fun answerOfPass(tick: Int): (Any) -> Boolean = { tick >= 0 }

/**
 * A panel whose modifier comes from the caller and which writes [tick] onto the component it renders, so
 * a caller moving the tick re-executes the body and the counts read the diff rather than a skipped
 * composable.
 */
@Composable
fun ProbePanel(
    tick: Int,
    counts: ChainCounts,
    modifier: SwingModifier = SwingModifier,
    kind: ProbeKind = ProbeKind.Panel,
) {
    counts.passes.incrementAndGet()
    when (kind) {
        ProbeKind.Panel -> ProbeNodes.Panel(tick, modifier)
        ProbeKind.Button -> ProbeNodes.Button(tick, modifier)
        ProbeKind.CheckBox -> ProbeNodes.CheckBox(tick, modifier)
        ProbeKind.Slider -> ProbeNodes.Slider(tick, modifier)
        ProbeKind.List -> ProbeNodes.List(tick, modifier)
        ProbeKind.TextField -> ProbeNodes.TextField(tick, modifier)
        ProbeKind.InternalFrame -> ProbeNodes.InternalFrame(tick, modifier)
        ProbeKind.Tree -> ProbeNodes.Tree(tick, modifier)
        ProbeKind.Label -> ProbeNodes.Label(tick, modifier)
    }
}

/** The component class a [ProbePanel] renders. */
sealed interface ProbeKind {
    /** A [JPanel]. */
    data object Panel : ProbeKind

    /** A [JButton]. */
    data object Button : ProbeKind

    /** A [JCheckBox]. */
    data object CheckBox : ProbeKind

    /** A [JSlider]. */
    data object Slider : ProbeKind

    /** A [JList]. */
    data object List : ProbeKind

    /** A [JTextField]. */
    data object TextField : ProbeKind

    /** A [JInternalFrame]. */
    data object InternalFrame : ProbeKind

    /** A [JTree]. */
    data object Tree : ProbeKind

    /** A [JLabel]. */
    data object Label : ProbeKind
}

/** What one run asks of the component under test and of the one element of its modifier chain. */
class ChainCounts {
    /** How many times the component under test was executed. */
    val passes: AtomicInteger = AtomicInteger()

    /** How many times the diff asked the probe element for its key. */
    val keyReads: AtomicInteger = AtomicInteger()

    /** How many times the probe element was written onto its node. */
    val updates: AtomicInteger = AtomicInteger()
}

/**
 * A property element that writes the target's tooltip and reports what the diff asks of it: its
 * [key], which only the partition of a modifier chain being diffed asks for, and its `update`, called
 * whenever the slot takes it as new data.
 *
 * Two elements built from one [counts] declare the same thing and are equal, so the element never
 * defeats the skip it measures.
 */
class ChainProbeElement(
    private val counts: ChainCounts,
) : SwingModifier.NodeElement<JComponent, ChainProbeElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "toolTipText"

    override val key: Any
        get() {
            counts.keyReads.incrementAndGet()
            return javaClass
        }

    override fun create(): Node = Node()

    override fun update(node: Node) {
        counts.updates.incrementAndGet()
        node.write("probe")
    }

    override fun equals(other: Any?): Boolean = other is ChainProbeElement && counts === other.counts

    override fun hashCode(): Int = System.identityHashCode(counts)

    /** Remembers the tooltip it found and puts it back on detach. */
    class Node : SwingModifier.ComponentNode<JComponent>() {
        private var original: String? = null

        override fun onAttach() {
            original = component.toolTipText
        }

        internal fun write(text: String) {
            component.toolTipText = text
        }

        override fun onDetach() {
            component.toolTipText = original
        }
    }
}

/** One node per component class a [ProbePanel] renders. */
private object ProbeNodes {
    @Composable
    fun Panel(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JPanel() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun Button(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JButton() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun CheckBox(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JCheckBox() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun Slider(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JSlider() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun List(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JList<String>() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun TextField(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JTextField() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun InternalFrame(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JInternalFrame() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun Tree(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JTree() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }

    @Composable
    fun Label(
        tick: Int,
        modifier: SwingModifier,
    ) {
        SwingNode(
            factory = { JLabel() },
            modifier = modifier,
            update = {
                set(tick) { pass -> name = "pass $pass" }
            },
        )
    }
}
