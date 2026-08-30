package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.components.selection.rememberTableState
import org.jetbrains.compose.swing.components.selection.rememberTreeState
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import java.awt.Rectangle
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * A component declares one restart scope: its own. The node each one renders is inlined into it, so the
 * scope the call site pays for is the one a recomposition restarts at, and no widget opens a second.
 *
 * Inspection is process-wide and belongs to the application, which these tests stand in for: the
 * assertion turns it on and the default is restored after each test.
 *
 * A menu item composes under an applier of its own, and the data this reads is the root's, so the menu
 * components are not ones these counts cover.
 */
class RestartScopeCountTest {
    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
    }

    @Test
    fun aButtonOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { Button(text = "Save", onClick = {}) }
    }

    @Test
    fun aCheckBoxOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { CheckBox(text = "Word wrap", checked = false, onCheckedChange = {}) }
    }

    @Test
    fun aRadioButtonOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { RadioButton(text = "Nightly", selected = false, onSelectedChange = {}) }
    }

    @Test
    fun aToggleButtonOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { ToggleButton(text = "Bold", selected = false, onSelectedChange = {}) }
    }

    @Test
    fun aComboBoxOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) {
            ComboBox(items = listOf("A", "B"), selectedItem = "A", onSelectionChange = {})
        }
    }

    @Test
    fun aSpinnerOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { Spinner(value = 5, onValueChange = {}) }
    }

    @Test
    fun aSliderOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { Slider(value = 50, onValueChange = {}) }
    }

    @Test
    fun aProgressBarOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { ProgressBar(value = 50) }
    }

    @Test
    fun aTextFieldOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { TextField(value = "hello", onValueChange = {}) }
    }

    @Test
    fun aTextAreaOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { TextArea(value = "hello", onValueChange = {}) }
    }

    @Test
    fun aPasswordFieldOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { PasswordField(value = "pass".toCharArray(), onValueChange = {}) }
    }

    @Test
    fun aFormattedTextFieldOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { FormattedTextField(value = 1, onValueChange = {}) }
    }

    @Test
    fun anEditorPaneOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { EditorPane(markup = "", onLinkActivate = {}) }
    }

    @Test
    fun aTextPaneOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { TextPane(value = "hello", onValueChange = {}) }
    }

    @Test
    fun aTableOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) {
            Table(model = DefaultTableModel(arrayOf(arrayOf<Any>("a")), arrayOf<Any>("col")), selectedRowIndices = null)
        }
    }

    @Test
    fun aListBoxOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { ListBox(items = listOf("a", "b"), selectedIndices = null) }
    }

    @Test
    fun aTreeOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) {
            Tree(root = "root", children = { emptyList() }, label = { it })
        }
    }

    // A container's content is a composable lambda the caller wrote, and a lambda of its own is a scope of
    // its own. The container adds none on top of it.
    @Test
    fun aSplitPaneOpensNoScopeBeyondItsContent() = runComposeSwingTest {
        assertRestartScopeCount(2) { SplitPane { } }
    }

    @Test
    fun aTabbedPaneOpensNoScopeBeyondItsContent() = runComposeSwingTest {
        assertRestartScopeCount(2) { TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) { } }
    }

    @Test
    fun aBoxPanelOpensNoScopeBeyondItsContent() = runComposeSwingTest {
        assertRestartScopeCount(2) { BoxPanel { } }
    }

    // A state-driven overload reaches the same node as the lambda one; it declares no scope of its own on
    // the way there.
    @Test
    fun aStateDrivenListBoxOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) { ListBox(items = listOf("a", "b"), state = rememberListState()) }
    }

    @Test
    fun aStateDrivenTreeOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) {
            Tree(root = "root", children = { emptyList() }, state = rememberTreeState(), label = { it })
        }
    }

    @Test
    fun aStateDrivenTableOpensOneRestartScope() = runComposeSwingTest {
        assertRestartScopeCount(1) {
            Table(
                model = DefaultTableModel(arrayOf(arrayOf<Any>("a")), arrayOf<Any>("col")),
                state = rememberTableState(),
            )
        }
    }

    // Two nested containers, each one scope of its own plus the content lambda the caller wrote.
    @Test
    fun aFramedDesktopOpensNoScopeBeyondTheTwoContents() = runComposeSwingTest {
        assertRestartScopeCount(4) {
            DesktopPane {
                InternalFrame(title = "Editor", bounds = Rectangle(0, 0, 80, 60), onClose = {}) { }
            }
        }
    }
}
