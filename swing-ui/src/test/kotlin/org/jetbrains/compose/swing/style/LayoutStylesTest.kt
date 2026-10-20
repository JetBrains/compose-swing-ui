package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JLabel
import kotlin.test.Test

class LayoutStylesTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("setterCaseNames")
    fun eachSetterWritesRewritesAndRestores(caseIndex: Int) = runComposeSwingTest {
        assertEachSetterWritesRewritesAndRestores(listOf(setterCases()[caseIndex]))
    }

    @Test
    fun aLabelTakesTheOrientationItsContainerSets() = runComposeSwingTest {
        var orientation: ComponentOrientation? by mutableStateOf(ComponentOrientation.RIGHT_TO_LEFT)
        setContent {
            Panel(style = { orientation?.let { componentOrientation(it) } }) { Label("inherits") }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(ComponentOrientation.RIGHT_TO_LEFT) { componentOrientation }

        orientation = ComponentOrientation.LEFT_TO_RIGHT
        awaitIdle()
        label.assertProperty(ComponentOrientation.LEFT_TO_RIGHT, "a changed value reaches the label") {
            componentOrientation
        }

        orientation = null
        awaitIdle()
        label.assertProperty(ComponentOrientation.UNKNOWN, "the label gives back its own once unset") {
            componentOrientation
        }
    }

    companion object {
        @JvmStatic
        fun setterCaseNames(): List<Named<Int>> = setterCases().mapIndexed { index, case -> Named.of(case.name, index) }
    }
}

private fun declaredPreferredSize(component: Component): Dimension? =
    if (component.isPreferredSizeSet) component.preferredSize else null

private fun declaredMinimumSize(component: Component): Dimension? =
    if (component.isMinimumSizeSet) component.minimumSize else null

private fun declaredMaximumSize(component: Component): Dimension? =
    if (component.isMaximumSizeSet) component.maximumSize else null

private fun setterCases(): List<SetterCase> = listOf(
    setterCase("enabled", first = false, second = true, read = { it.isEnabled }) { enabled(it) },
    setterCase("focusable", first = false, second = true, read = { it.isFocusable }) { focusable(it) },
    setterCase("visible", first = false, second = true, read = { it.isVisible }) { visible(it) },
    setterCase(
        "componentOrientation",
        ComponentOrientation.RIGHT_TO_LEFT,
        ComponentOrientation.LEFT_TO_RIGHT,
        { it.componentOrientation },
    ) { componentOrientation(it) },
    setterCase("bounds", Rectangle(1, 2, 3, 4), Rectangle(5, 6, 7, 8), { it.bounds }) {
        bounds(it.x, it.y, it.width, it.height)
    },
    setterCase("location", Point(1, 2), Point(3, 4), { it.location }) { location(it) },
    setterCase("location(x, y)", Point(1, 2), Point(3, 4), { it.location }) { location(it.x, it.y) },
    setterCase("x", 1, 2, { it.x }) { x(it) },
    setterCase("y", 1, 2, { it.y }) { y(it) },
    setterCase("size", Dimension(1, 2), Dimension(3, 4), { it.size }) { size(it) },
    setterCase("size(width, height)", Dimension(1, 2), Dimension(3, 4), { it.size }) {
        size(it.width, it.height)
    },
    setterCase("width", 1, 2, { it.width }) { width(it) },
    setterCase("height", 1, 2, { it.height }) { height(it) },
    setterCase("preferredSize", Dimension(1, 2), Dimension(3, 4), ::declaredPreferredSize) {
        preferredSize(it)
    },
    setterCase("preferredSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredPreferredSize) {
        preferredSize(it.width, it.height)
    },
    setterCase("minimumSize", Dimension(1, 2), Dimension(3, 4), ::declaredMinimumSize) { minimumSize(it) },
    setterCase("minimumSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredMinimumSize) {
        minimumSize(it.width, it.height)
    },
    setterCase("maximumSize", Dimension(1, 2), Dimension(3, 4), ::declaredMaximumSize) { maximumSize(it) },
    setterCase("maximumSize(width, height)", Dimension(1, 2), Dimension(3, 4), ::declaredMaximumSize) {
        maximumSize(it.width, it.height)
    },
)
