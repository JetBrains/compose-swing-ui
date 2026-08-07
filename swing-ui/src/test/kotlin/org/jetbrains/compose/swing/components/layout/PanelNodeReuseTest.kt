package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JSplitPane
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * A panel node is recyclable: a parked [ReusableContentHost] child is reactivated onto the component the
 * node already holds, and the node's factory does not run a second time. What a container writes to its
 * component from *outside* that node - a [CardPanel]'s card flip, for one - therefore has to address the
 * component the node holds rather than one the composable made for itself, or the two drift apart and
 * every later declaration lands on a component no longer in the tree.
 */
class PanelNodeReuseTest {
    /** Asserts the [CardPanel] shows the card labeled [shown] and keeps the one labeled [hidden] down. */
    private fun ComposeSwingTest.assertShownCard(
        shown: String,
        hidden: String,
    ) {
        onNodeWithText(shown).assertIsVisible()
        onNodeWithText(hidden).assertIsNotVisible()
    }

    @Test
    fun aReactivatedCardPanelStillFlipsToTheSelectedCard() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var selected by mutableStateOf("first")
        setContent {
            ReusableContentHost(active = active) {
                CardPanel(selectedCard = selected) {
                    card("first") { Label("first") }
                    card("second") { Label("second") }
                }
            }
        }
        assertShownCard(shown = "first", hidden = "second")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        assertShownCard(shown = "first", hidden = "second")

        selected = "second"
        awaitIdle()

        assertShownCard(shown = "second", hidden = "first")
    }

    @Test
    fun aReactivatedSplitPaneHostsTheSidesTheCompositionDeclares() = runComposeSwingTest {
        var active by mutableStateOf(true)
        var trailing by mutableStateOf("second")
        setContent {
            ReusableContentHost(active = active) {
                SplitPane {
                    first { Label(text = "first") }
                    second { Label(text = trailing) }
                }
            }
        }
        val pane = onNodeOfType<JSplitPane>().fetch()
        assertSame(onNodeWithText("first").fetch(), pane.leftComponent, "the first side should be hosted")

        active = false
        awaitIdle()
        active = true
        awaitIdle()

        val reactivated = onNodeOfType<JSplitPane>().fetch()
        assertSame(
            onNodeWithText("first").fetch(),
            reactivated.leftComponent,
            "the reactivated pane should host the first side again",
        )

        trailing = "replaced"
        awaitIdle()

        assertSame(
            onNodeWithText("replaced").fetch(),
            onNodeOfType<JSplitPane>().fetch().rightComponent,
            "a side redeclared after reactivation should land on the hosted pane",
        )
    }
}
