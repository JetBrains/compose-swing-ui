package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performContextClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Restore checks report un-restored declarations in root, renderer, menu, and window compositions.
 * A failed property read is reported while the composition remains able to recompose.
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
        onNodeWithText("target").performContextClick()
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
            Table(
                rows = listOf(if (failing) FAILING_ROW else "row"),
                columns = { column("Name") { row -> if (row == FAILING_ROW) throw CellValueFailure() else row } },
            )
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
        onNodeOfType<JLabel>().assertProperty(
            "second",
            "the composition should go on recomposing after a read that threw",
        ) { this.text }
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
    val message = assertFailsWith<AssertionError>(block = gate).message.orEmpty()
    assertContains(message, "departing unrestoredName declaration")
    assertContains(message, "left <left-behind>")
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
