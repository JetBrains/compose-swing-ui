package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.ComponentOrientation
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test

class LayoutStylesTest {
    @TestFactory
    fun eachSetterWritesRewritesAndRestores(): List<DynamicTest> = setterCaseTests(setterCases())

    @Test
    fun aLabelTakesTheOrientationItsContainerSets() = runComposeSwingTest {
        var orientation: ComponentOrientation? by mutableStateOf(ComponentOrientation.RIGHT_TO_LEFT)
        setContent {
            Panel(style = { orientation?.let { componentOrientation(it) } }) { Label("inherits") }
        }
        onNodeOfType<JLabel>().assertProperty(ComponentOrientation.RIGHT_TO_LEFT) { componentOrientation }

        orientation = ComponentOrientation.LEFT_TO_RIGHT
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(ComponentOrientation.LEFT_TO_RIGHT, "a changed value reaches the label") {
            componentOrientation
        }

        orientation = null
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(ComponentOrientation.UNKNOWN, "the label gives back its own once unset") {
            componentOrientation
        }
    }
}

private fun setterCases(): List<SetterCase> = listOf(
    layoutCase("enabled", first = false, second = true, read = { it.isEnabled }) { enabled(it) },
    layoutCase("focusable", first = false, second = true, read = { it.isFocusable }) { focusable(it) },
    layoutCase("visible", first = false, second = true, read = { it.isVisible }) { visible(it) },
    layoutCase(
        "componentOrientation",
        ComponentOrientation.RIGHT_TO_LEFT,
        ComponentOrientation.LEFT_TO_RIGHT,
        { it.componentOrientation },
    ) { componentOrientation(it) },
)

internal inline fun <reified V : Any> layoutCase(
    name: String,
    first: V,
    second: V,
    noinline read: (Component) -> Any?,
    noinline set: ComponentStyleScope.(V) -> Unit,
): SetterCase = SetterCase(name, first, second, { onNodeWithTag(name).assertIsOfType<JLabel>() }, read) { value ->
    LayeredPane {
        Label(
            text = name,
            modifier = SwingModifier.testTag(name).layer(JLayeredPane.DEFAULT_LAYER),
            style = { (value() as V?)?.let { set(it) } },
        )
    }
}
