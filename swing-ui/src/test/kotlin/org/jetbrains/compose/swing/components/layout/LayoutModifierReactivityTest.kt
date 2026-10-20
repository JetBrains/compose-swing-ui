package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.BoxLayout
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
 * Each test drives one container through those steps with a name element, reading the name
 * off the live component.
 */
class LayoutModifierReactivityTest {
    /**
     * Declares [container] with a name element, then asserts the component [target] resolves
     * reports the declared name, follows a second one, and reports none once the element is dropped.
     */
    private suspend fun ComposeSwingTest.assertTheModifierIsFollowed(
        subject: String,
        target: ComposeSwingTest.() -> JComponent = { onRoot().onChild().fetch<JComponent>() },
        container: @Composable (SwingModifier) -> Unit,
    ) {
        var declared by mutableStateOf<String?>("first")
        setContent { container(declared?.let { SwingModifier.name(it) } ?: SwingModifier) }

        assertEquals("first", target().name, "$subject: the declared name")

        declared = "second"
        awaitIdle()
        assertEquals("second", target().name, "$subject: the name after the element changed")

        declared = null
        awaitIdle()
        assertNull(target().name, "$subject: the name after the element was dropped")
    }

    @TestFactory
    fun containersFollowTheirModifier(): List<DynamicTest> = listOf<Pair<String, @Composable (SwingModifier) -> Unit>>(
        "PanelLayout.Border" to { modifier ->
            Panel(PanelLayout.Border(), modifier = modifier) { Label("child", SwingModifier.center()) }
        },
        "Box" to { modifier ->
            Panel(PanelLayout.Box(), modifier = modifier) { Label("child") }
        },
        "Row" to { modifier ->
            Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS), modifier = modifier) { Label("child") }
        },
        "PanelLayout.Card" to { modifier ->
            Panel(PanelLayout.Card(selectedCard = "only"), modifier = modifier) {
                Label("child", SwingModifier.card("only"))
            }
        },
        "PanelLayout.Flow" to { modifier ->
            Panel(PanelLayout.Flow(), modifier = modifier) { Label("child") }
        },
        "PanelLayout.GridBag" to { modifier ->
            Panel(PanelLayout.GridBag, modifier = modifier) { Label("child", SwingModifier.item()) }
        },
        "PanelLayout.Grid" to { modifier ->
            Panel(PanelLayout.Grid(), modifier = modifier) { Label("child") }
        },
        "ScrollPane" to { modifier ->
            ScrollPane(modifier = modifier) { Viewport { Label("child") } }
        },
        "SplitPane" to { modifier ->
            SplitPane(modifier = modifier) {
                Label("A", SwingModifier.first())
                Label("B", SwingModifier.second())
            }
        },
        "TabbedPane" to { modifier ->
            TabbedPane(
                selectedIndex = 0,
                onSelectedIndexChange = {},
                modifier = modifier,
            ) { Label("child", SwingModifier.tab("General")) }
        },
        "ToolBar" to { modifier ->
            ToolBar(modifier = modifier, floatable = false) { Label("child") }
        },
    ).map { (subject, container) ->
        dynamicTest(subject) {
            runComposeSwingTest { assertTheModifierIsFollowed(subject, container = container) }
        }
    }

    @Test
    fun aToolBarSeparatorFollowsItsModifier() = runComposeSwingTest {
        assertTheModifierIsFollowed(
            subject = "ToolBarSeparator",
            target = { onNodeOfType<JToolBar.Separator>().fetch() },
        ) { modifier ->
            ToolBar(floatable = false) { ToolBarSeparator(modifier = modifier) }
        }
    }
}
