package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.popupTrigger
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * That the restore check reaches the compositions a test mounts, including the ones mounted inside
 * them: it is installed on the coroutine context the harness builds its recomposer over, and a nested
 * composition is meant to inherit it through its parent's effect context.
 *
 * Each reach case declares [UnrestoredName] and asserts the removal is reported. A composition the
 * checks never reached would report nothing, and the child-index-space walk, which rides on the same
 * seam, would stop there too.
 *
 * The last case covers what the check owes the composition it runs inside: reading a component to see
 * what a declaration writes runs the caller's own code, and a throw from it is reported rather than left
 * to end the recomposer.
 */
class ModifierRestoreCheckWiringTest {
    @Test
    fun aRestoreLeftUndoneInTheTestsOwnCompositionIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent { NamedLabel(declared) }

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aRestoreLeftUndoneInACellRenderersCompositionIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            ListBox(items = listOf("row")) { item -> NamedLabel(declared, item) }
        }
        awaitIdle()

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aRestoreLeftUndoneInAMenusCompositionIsReported() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            ContextMenu(anchor, display = { _, _, _, _ -> }) {
                MenuItem(
                    "Cut",
                    onClick = {},
                    modifier = if (declared) SwingModifier then UnrestoredName() else SwingModifier,
                )
            }
        }
        // A menu composes its content when the popup is built, so the declaration only reaches an
        // item once the trigger has opened one.
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        awaitIdle()

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aRestoreLeftUndoneInAWindowsCompositionIsReported() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var declared by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, visible = false) { NamedLabel(declared) }
        }
        awaitIdle()

        declared = false
        assertReportsTheUnrestoredName { awaitIdle() }
    }

    @Test
    fun aCellValueThatThrowsWhereTheCheckReadsIsReportedAndLeavesTheCompositionRecomposing() = runComposeSwingTest {
        var failing by mutableStateOf(false)
        var text by mutableStateOf("first")
        setContent {
            Table(rows = listOf(if (failing) FAILING_ROW else "row")) {
                column("Name") { row -> if (row == FAILING_ROW) throw CellValueFailure() else row }
            }
            Label(text)
        }

        failing = true
        val reported = assertFailsWith<AssertionError> { awaitIdle() }
        assertIs<CellValueFailure>(
            reported.cause,
            "the check's own read of the table should report what the caller's cell value threw",
        )

        failing = false
        text = "second"
        awaitIdle()
        assertEquals(
            "second",
            onNodeOfType<JLabel>().fetch<JLabel>().text,
            "the composition should go on recomposing after a read that threw",
        )
    }
}

/** The row whose cell value throws, standing for any caller code a watched read runs. */
private const val FAILING_ROW = "failing"

private class CellValueFailure : RuntimeException("the caller's own cell value")

/** A label carrying [UnrestoredName] while [declared], and nothing once it is not. */
@Composable
private fun NamedLabel(
    declared: Boolean,
    text: String = "target",
) {
    Label(text, modifier = if (declared) SwingModifier then UnrestoredName() else SwingModifier)
}

private inline fun assertReportsTheUnrestoredName(gate: () -> Unit) {
    val failure = assertFailsWith<AssertionError>(block = gate)
    assertContains(
        failure.message.orEmpty(),
        "unrestoredName",
        message = "the gate should report the declaration that left the component's name where it wrote it",
    )
}

/**
 * A declaration that writes the component's name and, on removal, leaves what it wrote standing - the
 * defect the restore check exists to catch. It carries nothing, so one declaration of it is equal to
 * every other.
 */
private class UnrestoredName : SwingModifier.NodeElement<JComponent, UnrestoredName.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val name: String get() = "unrestoredName"

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.component.name = "left-behind"
    }

    override fun equals(other: Any?): Boolean = other is UnrestoredName

    override fun hashCode(): Int = javaClass.hashCode()

    class Node : SwingModifier.ComponentNode<JComponent>()
}
