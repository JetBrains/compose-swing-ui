package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.BoxLayout
import javax.swing.JLabel

/**
 * A container's `content` is composition state like any other: the children it declares become the
 * container's own children, a child re-declared with a new value re-renders, and a child that stops
 * being declared is detached.
 *
 * Each test drives one container through those steps and reads the captions back off the live
 * component tree, in the order that container holds its children in.
 */
class LayoutContentReactivityTest {
    /** Asserts the single container the composition emits hosts labels captioned [captions], in order. */
    private fun ComposeSwingTest.assertTheContainerHosts(vararg captions: String) {
        val labels = onRoot().onChild().onChildren().filter(SwingMatcher.isOfType<JLabel>())
        labels.assertCountEquals(captions.size)
        captions.forEachIndexed { index, caption -> labels[index].assertTextEquals(caption) }
    }

    /**
     * Declares [container] with one label whose caption is composition state and a second label
     * behind a flag, then asserts the container hosts what the latest composition declares.
     *
     * [hosted] puts the captions declared into the order [container] holds the children carrying them,
     * which is the order they are declared in unless that container says otherwise.
     */
    private suspend fun ComposeSwingTest.assertTheContentIsFollowed(
        hosted: (List<String>) -> List<String> = { it },
        container: @Composable (@Composable () -> Unit) -> Unit,
    ) {
        var caption by mutableStateOf(FIRST_CAPTION)
        var showExtra by mutableStateOf(false)
        setContent {
            container {
                Label(caption)
                if (showExtra) Label(EXTRA_CAPTION)
            }
        }

        assertTheContainerHosts(*hosted(listOf(FIRST_CAPTION)).toTypedArray())

        caption = SECOND_CAPTION
        awaitIdle()
        assertTheContainerHosts(*hosted(listOf(SECOND_CAPTION)).toTypedArray())

        showExtra = true
        awaitIdle()
        assertTheContainerHosts(*hosted(listOf(SECOND_CAPTION, EXTRA_CAPTION)).toTypedArray())

        showExtra = false
        caption = FIRST_CAPTION
        awaitIdle()
        assertTheContainerHosts(*hosted(listOf(FIRST_CAPTION)).toTypedArray())
    }

    @TestFactory
    fun containersFollowTheirContent(): List<DynamicTest> = listOf(
        ContainerCase("aBoxFollowsItsContent") { content -> Panel(PanelLayout.Box()) { content() } },
        ContainerCase("aRowFollowsItsContent") { content ->
            Panel(PanelLayout.Box(axis = BoxLayout.X_AXIS)) { content() }
        },
        ContainerCase("aFlowPanelFollowsItsContent") { content -> Panel(PanelLayout.Flow()) { content() } },
        ContainerCase("aGridPanelFollowsItsContent") { content -> Panel(PanelLayout.Grid()) { content() } },
        ContainerCase("aToolBarFollowsItsContent") { content -> ToolBar(floatable = false) { content() } },
    ).map { (name, container) ->
        dynamicTest(name) {
            runComposeSwingTest { assertTheContentIsFollowed(container = container) }
        }
    }

    private data class ContainerCase(
        val name: String,
        val content: @Composable (@Composable () -> Unit) -> Unit,
    )

    private companion object {
        const val FIRST_CAPTION = "first"
        const val SECOND_CAPTION = "second"
        const val EXTRA_CAPTION = "extra"
    }
}
