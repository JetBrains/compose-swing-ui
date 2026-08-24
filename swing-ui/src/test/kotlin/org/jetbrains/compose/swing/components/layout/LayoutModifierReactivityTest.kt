package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JToolBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A container's `modifier` is composition state like any other: the property an element carries
 * reaches the container on the composition that declares it, follows a later value, and is handed
 * back to what the container held on its own once the element is dropped.
 *
 * Each test drives one container through those three steps with a tool-tip element, reading the tip
 * off the live component.
 */
class LayoutModifierReactivityTest {
    /**
     * Declares [container] with a tool-tip element, then asserts the component [target] resolves
     * reports the declared tip, follows a second one, and reports none once the element is dropped.
     */
    private suspend fun ComposeSwingTest.assertTheModifierIsFollowed(
        subject: String,
        target: ComposeSwingTest.() -> JComponent = { onRoot().onChild().fetch<JComponent>() },
        container: @Composable (SwingModifier) -> Unit,
    ) {
        var tip by mutableStateOf<String?>(FIRST_TIP)
        setContent { container(tip?.let { SwingModifier.toolTip(it) } ?: SwingModifier) }

        assertEquals(FIRST_TIP, target().toolTipText, "$subject: the declared tool tip")

        tip = SECOND_TIP
        awaitIdle()
        assertEquals(SECOND_TIP, target().toolTipText, "$subject: the tool tip after the element changed")

        tip = null
        awaitIdle()
        assertNull(target().toolTipText, "$subject: the tool tip after the element was dropped")
    }

    @Test
    fun aBorderPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("BorderPanel") { modifier ->
            BorderPanel(modifier = modifier) { Label("child", SwingModifier.center()) }
        }
    }

    @Test
    fun aBoxPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("BoxPanel") { modifier ->
            BoxPanel(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aColumnFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("Column") { modifier ->
            Column(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aRowFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("Row") { modifier ->
            Row(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aCardPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("CardPanel") { modifier ->
            CardPanel(selectedCard = "only", modifier = modifier) { Label("child", SwingModifier.card("only")) }
        }
    }

    @Test
    fun aFlowPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("FlowPanel") { modifier ->
            FlowPanel(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aGridBagPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("GridBagPanel") { modifier ->
            GridBagPanel(modifier = modifier) { Label("child", SwingModifier.item()) }
        }
    }

    @Test
    fun aGridPanelFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("GridPanel") { modifier ->
            GridPanel(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aScrollPaneFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("ScrollPane") { modifier ->
            ScrollPane(modifier = modifier) { Label("child", SwingModifier.viewport()) }
        }
    }

    @Test
    fun aSplitPaneFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("SplitPane") { modifier ->
            SplitPane(modifier = modifier) {
                Label("A", SwingModifier.first())
                Label("B", SwingModifier.second())
            }
        }
    }

    @Test
    fun aTabbedPaneFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("TabbedPane") { modifier ->
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = {},
                modifier = modifier,
            ) { Label("child", SwingModifier.tab("General")) }
        }
    }

    @Test
    fun aToolBarFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed("ToolBar") { modifier ->
            ToolBar(modifier = modifier) { Label("child") }
        }
    }

    @Test
    fun aToolBarSeparatorFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed(
            subject = "ToolBarSeparator",
            target = { onNodeOfType<JToolBar.Separator>().fetch() },
        ) { modifier ->
            ToolBar { ToolBarSeparator(modifier = modifier) }
        }
    }

    private companion object {
        const val FIRST_TIP = "first"
        const val SECOND_TIP = "second"
    }
}
