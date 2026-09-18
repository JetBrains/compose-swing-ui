package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.TestRecomposer
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * End-to-end tests for the `defaultButton` modifier mounted under a real [JRootPane]. They assert the
 * observable wiring - the root pane's default button - that pressing Enter would activate, which a bare
 * `JPanel` test root cannot express because it has no root pane.
 */
class DefaultButtonModifierTest {
    private val rootPane: JRootPane = JRootPane()
    private val root: JComponent = rootPane.contentPane as JComponent

    private suspend fun setContent(
        test: TestRecomposer,
        content: @Composable () -> Unit,
    ): DisposableHandle {
        val handle = root.setContent(parent = test.recomposer, content = content)
        test.awaitIdle()
        return handle
    }

    /** The single button the composition mounted under the root pane's content pane. */
    private fun theButton(): JButton {
        fun find(component: Component): JButton? = when {
            component is JButton -> component
            component is Container -> component.components.firstNotNullOfOrNull(::find)
            else -> null
        }
        return find(root) ?: error("the composition mounted no button")
    }

    @Test
    fun defaultButtonBecomesRootPaneDefault() = runSwingTest {
        val test = TestRecomposer(this)
        var handle: DisposableHandle? = null
        try {
            handle = setContent(test) { Button("OK", onClick = { }, modifier = SwingModifier.defaultButton()) }
            assertSame(
                theButton(),
                rootPane.defaultButton,
                "the modifier should make the button the root pane default",
            )
        } finally {
            handle?.dispose()
            test.cancel()
        }
    }

    @Test
    fun clearingDefaultButtonReleasesRootPaneDefault() = runSwingTest {
        val test = TestRecomposer(this)
        var handle: DisposableHandle? = null
        try {
            var isDefault by mutableStateOf(true)
            handle =
                setContent(test) {
                    Button("OK", onClick = { }, modifier = SwingModifier.defaultButton(isDefault))
                }
            assertSame(
                theButton(),
                rootPane.defaultButton,
                "the button should start as the root pane default",
            )

            isDefault = false
            test.awaitIdle()
            assertNull(rootPane.defaultButton, "clearing the flag should release the root pane default")
        } finally {
            handle?.dispose()
            test.cancel()
        }
    }

    @Test
    fun removingDefaultButtonModifierReleasesRootPaneDefault() = runSwingTest {
        val test = TestRecomposer(this)
        var handle: DisposableHandle? = null
        try {
            var present by mutableStateOf(true)
            handle =
                setContent(test) {
                    Button(
                        "OK",
                        onClick = { },
                        modifier = if (present) SwingModifier.defaultButton() else SwingModifier,
                    )
                }
            assertSame(
                theButton(),
                rootPane.defaultButton,
                "the button should start as the root pane default",
            )

            present = false
            test.awaitIdle()
            // The element left the chain, so its reset releases the root pane's default button.
            assertNull(rootPane.defaultButton, "removing the modifier should release the root pane default")
        } finally {
            handle?.dispose()
            test.cancel()
        }
    }

    @Test
    fun defaultButtonFollowsTheButtonToAnotherRootPane() = runSwingTest {
        val test = TestRecomposer(this)
        var handle: DisposableHandle? = null
        try {
            val host = JPanel().also(root::add)
            handle =
                host.setContent(parent = test.recomposer) {
                    Button("OK", onClick = { }, modifier = SwingModifier.defaultButton())
                }
            test.awaitIdle()
            val button = theButton()
            assertSame(button, rootPane.defaultButton, "the button should start as the root pane default")

            val otherRootPane = JRootPane()
            root.remove(host)
            otherRootPane.contentPane.add(host)

            assertSame(
                button,
                otherRootPane.defaultButton,
                "the default should follow the button to the root pane it moved to",
            )
            assertNull(rootPane.defaultButton, "the root pane the button left should hold no default")
        } finally {
            handle?.dispose()
            test.cancel()
        }
    }
}
