package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.DeclaredNodesListener
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyDeclaredModifier
import org.jetbrains.compose.swing.modifier.resetModifierState
import org.jetbrains.compose.swing.node.CreatedNodeHolder
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.TestCompositionOwner
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class StyleTeardownFailureTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun teardownFailureRestoresOtherProperties(removeStyle: Boolean) = runComposeSwingTest {
        val probe = StyleProbe("probe")
        val failure = object : IllegalStateException() {}
        val events = ArrayList<String>()
        val throwing = stylePropertyOf { _: Unit -> LifecycleElement("styled", events, detachFailure = failure) }
        var styled by mutableStateOf(true)
        val style =
            SwingStyle<ProbeContainerScope> {
                if (removeStyle || styled) {
                    ProbeSecond.provide("styled")
                    InheritedFirst.provide("styled")
                    throwing.provide(Unit)
                }
            }
        setContent {
            SwingNode(
                factory = { probe },
                modifier =
                    if (styled || !removeStyle) SwingModifier.styleable(style, PlainProbeContainer) else SwingModifier,
            ) {}
        }

        styled = false
        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
        assertEquals(PROBE_OWN_VALUE, probe.first, "a property taken apart after the failure is restored")
        assertEquals(PROBE_OWN_VALUE, probe.second)
        assertEquals(listOf("attach", "update styled", "detach"), events)
    }

    @Test
    fun failedUpdateDetachesOnce() = runComposeSwingTest {
        val events = ArrayList<String>()
        val failing =
            stylePropertyOf { _: Unit ->
                LifecycleElement("styled", events, updateFailure = IllegalStateException("The property failed"))
            }
        val owner = TestCompositionOwner()
        val holder = CreatedNodeHolder(StyleProbe()).attachedTo(owner)
        try {
            assertFailsWith<IllegalStateException> {
                holder.applyDeclaredModifier(SwingModifier.styleable({ failing.provide(Unit) }, PlainProbeContainer))
            }
            assertEquals(listOf("attach", "update styled"), events)
            events.clear()

            holder.applyDeclaredModifier(SwingModifier.styleable({ }, PlainProbeContainer))

            assertEquals(listOf("detach"), events, "the node whose update threw comes apart once the style drops it")
        } finally {
            holder.resetModifierState()
            owner.dispose()
        }
    }

    @Test
    fun unrestoredPropertyIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        val unrestored =
            stylePropertyOf { value: String ->
                LifecycleElement(value, ArrayList(), restoresOnDetach = false)
            }
        setContent {
            SwingNode(
                factory = { JLabel("own text") },
                modifier = SwingModifier.styleable({ if (declared) unrestored.provide("left behind") }, PlainProbe),
            )
        }

        declared = false
        val failure = assertFailsWith<AssertionError> { awaitIdle() }
        assertContains(failure.message.orEmpty(), "toolTipText")
    }

    @Test
    fun aResolveAfterOneThatFailedInstallsTheDeclarationsItNeverReached() = runComposeSwingTest {
        val armed = AtomicBoolean()
        val first = stylePropertyOf(failOnceFirstDescriptor(armed))
        val grown = mutableStateOf(false)
        val modifier =
            SwingModifier.styleable({
                first.provide(if (grown.value) "grown" else "plain")
                if (grown.value) ProbeSecond.provide("reached")
            }, PlainProbe)
        val owner = TestCompositionOwner()
        val probe = StyleProbe()
        val holder = CreatedNodeHolder(probe).attachedTo(owner)
        try {
            holder.applyDeclaredModifier(modifier)
            assertEquals("plain", probe.first)
            armed.set(true)

            grown.value = true
            assertFailsWith<IllegalStateException> { awaitIdle() }
            assertEquals(PROBE_OWN_VALUE, probe.second, "the property the failed pass never reached is not installed")

            holder.applyDeclaredModifier(modifier)

            assertEquals("grown", probe.first)
            assertEquals("reached", probe.second, "the next resolve installs the property the failed one never reached")
        } finally {
            holder.resetModifierState()
            owner.dispose()
        }
    }

    @Test
    fun aLayoutNodeWhoseUpdateFailedIsUpdatedByTheNextResolve() = runComposeSwingTest {
        val armed = AtomicBoolean()
        val inset = stylePropertyOf { amount: Int -> FailOnceInset(amount, armed) }
        val modifier = SwingModifier.styleable({ inset.provide(5) }, PlainProbe)
        val label = DeclaredNodesLabel()
        val owner = TestCompositionOwner()
        val holder = CreatedNodeHolder(label).attachedTo(owner)
        try {
            armed.set(true)
            assertFailsWith<IllegalStateException> { holder.applyDeclaredModifier(modifier) }

            holder.applyDeclaredModifier(modifier)

            assertEquals(
                listOf(5),
                label.declared.map { (it as InsetNode).amount },
                "an equal declaration is not adopted over an update that failed",
            )
        } finally {
            holder.resetModifierState()
            owner.dispose()
        }
    }
}

private fun failOnceFirstDescriptor(armed: AtomicBoolean) = ComponentPropertyDescriptor<StyleProbe, String?>(
    "first",
    { it.first },
    { probe, value ->
        check(!armed.getAndSet(false)) { "The write failed" }
        probe.first = value
    },
)

/** A layout node declaration whose update fails once while [armed], before writing. */
private class FailOnceInset(
    private val amount: Int,
    private val armed: AtomicBoolean,
) : ParentLayoutNodeElement<InsetNode>() {
    override val parentProtocol: ParentProtocol get() = Inset(amount).parentProtocol

    override val inheritable: Boolean get() = true

    override fun create(): InsetNode = InsetNode(0)

    override fun update(node: InsetNode) {
        check(!armed.getAndSet(false)) { "The update failed" }
        node.amount = amount
    }

    override fun equals(other: Any?): Boolean =
        other is FailOnceInset && amount == other.amount && armed === other.armed

    override fun hashCode(): Int = amount
}

private class DeclaredNodesLabel :
    JLabel(),
    DeclaredNodesListener {
    var declared: List<SwingModifier.Node> = emptyList()

    override fun onDeclaredNodesChanged(
        componentNode: SwingComponentNode<*>,
        nodes: List<SwingModifier.Node>,
    ) {
        declared = nodes
    }
}
