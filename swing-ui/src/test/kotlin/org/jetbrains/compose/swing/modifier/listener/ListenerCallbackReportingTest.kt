package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.event.HierarchyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JInternalFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.JTextPane
import javax.swing.JTree
import javax.swing.event.InternalFrameEvent
import javax.swing.text.SimpleAttributeSet
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A callback handed to a listener modifier that throws is reported to the thread's uncaught-exception
 * handler where the library calls it, and nothing reaches the code that fired the Swing listener, so the
 * listeners after it and the rest of the write that raised it still run. Each case declares one callback that
 * throws between two that do not, fires the listener Swing registered for them with a real event, and then
 * checks the composition still applies the next pass.
 */
class ListenerCallbackReportingTest {
    @TestFactory
    fun aThrowingCallbackIsReportedAndTheCompositionKeepsRecomposing(): List<DynamicTest> = cases

    private val cases: List<DynamicTest> =
        listOf(
            // Text
            reports("caretListener", { JTextField("abc") }, { call -> SwingModifier.caretListener { call() } }) {
                it.caretPosition = 1
            },
            reports(
                "documentListener onInsert",
                ::JTextPane,
                { call -> SwingModifier.documentListener(onInsert = { call() }) },
            ) {
                it.document.insertString(0, "ab", null)
            },
            reports(
                "documentListener onRemove",
                { JTextPane().apply { text = "ab" } },
                { call -> SwingModifier.documentListener(onRemove = { call() }) },
            ) { it.document.remove(0, 1) },
            reports(
                "documentListener onChange",
                { JTextPane().apply { text = "ab" } },
                { call -> SwingModifier.documentListener(onChange = { call() }) },
            ) { it.styledDocument.setCharacterAttributes(0, 1, SimpleAttributeSet(), false) },
            // Action, container and property
            reports("actionListener", ::JButton, { call -> SwingModifier.actionListener { call() } }) { it.doClick() },
            reports(
                "containerListener onComponentRemoved",
                { JPanel().apply { add(JLabel()) } },
                { call -> SwingModifier.containerListener(onComponentRemoved = { call() }) },
            ) { it.remove(0) },
            reports(
                "containerListener onComponentAdded",
                ::JPanel,
                { call -> SwingModifier.containerListener(onComponentAdded = { call() }) },
                extra = { assertTrue(it.getComponent(0).width > 0, "the child the callback was told of is laid out") },
            ) { children = 1 },
            reports(
                "propertyChangeListener on a composed write",
                ::JLabel,
                { call -> SwingModifier.propertyChangeListener("name") { call() } },
                extra = { assertEquals("during", it.name, "the write the callback was notified from ran to its end") },
            ) { generation = "during" },
            reports(
                "hierarchyListener",
                ::JPanel,
                { call -> SwingModifier.hierarchyListener { call() } },
            ) {
                it.dispatchEvent(HierarchyEvent(it, HierarchyEvent.HIERARCHY_CHANGED, it, it.parent, 0))
            },
            // Internal frame
            onFrame(
                "onFrameOpened",
                { call -> SwingModifier.internalFrameListener(onFrameOpened = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_OPENED) },
            onFrame(
                "onFrameClosing",
                { call -> SwingModifier.internalFrameListener(onFrameClosing = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_CLOSING) },
            onFrame(
                "onFrameClosed",
                { call -> SwingModifier.internalFrameListener(onFrameClosed = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_CLOSED) },
            onFrame(
                "onFrameIconified",
                { call -> SwingModifier.internalFrameListener(onFrameIconified = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_ICONIFIED) },
            onFrame(
                "onFrameDeiconified",
                { call -> SwingModifier.internalFrameListener(onFrameDeiconified = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED) },
            onFrame(
                "onFrameActivated",
                { call -> SwingModifier.internalFrameListener(onFrameActivated = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_ACTIVATED) },
            onFrame(
                "onFrameDeactivated",
                { call -> SwingModifier.internalFrameListener(onFrameDeactivated = { call() }) },
            ) { it.fire(InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED) },
            // State
            reports("adjustmentListener", ::JScrollBar, { call -> SwingModifier.adjustmentListener { call() } }) {
                it.value = 5
            },
            reports("changeListener", ::JSlider, { call -> SwingModifier.changeListener { call() } }) {
                it.value = 5
            },
            reports("itemListener", ::JCheckBox, { call -> SwingModifier.itemListener { call() } }) {
                it.isSelected = true
            },
            reports(
                "listSelectionListener",
                { JList(arrayOf("a", "b")) },
                { call -> SwingModifier.listSelectionListener { _ -> call() } },
            ) { it.selectedIndex = 1 },
            reports(
                "treeExpansionListener onTreeExpanded",
                ::JTree,
                { call -> SwingModifier.treeExpansionListener(onTreeExpanded = { call() }) },
            ) {
                it.collapseRow(0)
                it.expandRow(0)
            },
            reports(
                "treeExpansionListener onTreeCollapsed",
                ::JTree,
                { call -> SwingModifier.treeExpansionListener(onTreeCollapsed = { call() }) },
            ) { it.collapseRow(0) },
            reports("treeSelectionListener", ::JTree, { call -> SwingModifier.treeSelectionListener { call() } }) {
                it.setSelectionRow(1)
            },
        )

    private fun onFrame(
        name: String,
        modifier: (call: () -> Unit) -> SwingModifier,
        fire: Pass.(FiringInternalFrame) -> Unit,
    ): DynamicTest = reports("internalFrameListener $name", ::FiringInternalFrame, modifier, fire = fire)

    /**
     * A case named [name]: [modifier] declares a callback that calls `call`, on either side of the one that
     * throws, so one of them is notified after the throw whichever order Swing uses. [fire] makes Swing call
     * them, through the composition's own state in [Pass] or on the component directly, and [extra] checks
     * what the throw must not have cut short.
     */
    private inline fun <reified T : JComponent> reports(
        name: String,
        noinline factory: () -> T,
        noinline modifier: (call: () -> Unit) -> SwingModifier,
        noinline extra: (T) -> Unit = {},
        noinline fire: Pass.(T) -> Unit,
    ): DynamicTest = DynamicTest.dynamicTest(name) {
        runComposeSwingTest {
            val failures = mutableListOf<Throwable>()
            var armed = false
            var notified = 0
            val pass = Pass()
            lateinit var component: T
            val throwing = {
                if (armed) {
                    val failure = IllegalStateException(name)
                    failures += failure
                    throw failure
                }
            }
            val quiet = { { if (armed) notified++ } }
            setContent {
                SwingNode(
                    factory = { factory().also { component = it } },
                    modifier = modifier(quiet()).then(modifier(throwing)).then(modifier(quiet())),
                    update = { set(pass.generation) { this.name = it } },
                ) {
                    repeat(pass.children) { Label("child $it") }
                }
            }

            awaitIdle()
            armed = true
            pass.fire(component)
            val reported = assertFailsWith<IllegalStateException> { awaitIdle() }
            assertSame(failures.single(), reported, "the callback's failure is reported once, as thrown")
            assertEquals(emptyList(), reported.suppressed.toList(), "no other failure is reported with it")
            assertEquals(2, notified, "the callbacks declared around the throwing one are all notified")
            extra(component)

            armed = false
            pass.generation = "after"
            awaitIdle()
            assertEquals("after", component.name, "the composition applies the pass after the throw")
        }
    }

    /** The composition state a case writes to fire a callback from the composition's own write. */
    private class Pass {
        var generation by mutableStateOf("before")
        var children by mutableStateOf(0)
    }

    /** An internal frame that lets a test fire the events Swing sends its listeners. */
    private class FiringInternalFrame : JInternalFrame() {
        fun fire(id: Int) = fireInternalFrameEvent(id)
    }
}
