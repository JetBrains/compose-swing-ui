package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JLayeredPane

internal class SetterCase(
    val name: String,
    val read: (Component) -> Any?,
    val first: Any?,
    val second: Any?,
    val setFirst: ComponentStyleScope.() -> Unit,
    val setSecond: ComponentStyleScope.() -> Unit,
)

internal fun <V> setterCase(
    name: String,
    first: V,
    second: V,
    read: (Component) -> Any?,
    set: ComponentStyleScope.(V) -> Unit,
): SetterCase = SetterCase(name, read, first, second, { set(first) }, { set(second) })

internal suspend fun ComposeSwingTest.assertEachSetterWritesRewritesAndRestores(cases: List<SetterCase>) {
    var stage by mutableIntStateOf(0)
    setContent {
        LayeredPane {
            cases.forEach { case ->
                Label(
                    text = case.name,
                    modifier = SwingModifier.testTag(case.name).layer(JLayeredPane.DEFAULT_LAYER),
                    style = {
                        when (stage) {
                            1 -> case.setFirst(this)
                            2 -> case.setSecond(this)
                        }
                    },
                )
            }
        }
    }

    val originalValues = mutableMapOf<SetterCase, Any?>()
    cases.forEach { case ->
        onNodeWithTag(case.name).assertIsOfType<JLabel>().assertProperty(case.name) {
            originalValues[case] = case.read(this)
            text
        }
    }

    stage = 1
    awaitIdle()
    cases.forEach { case ->
        onNodeWithTag(case.name).assertProperty(case.first, "${case.name} writes its value") { case.read(this) }
    }

    stage = 2
    awaitIdle()
    cases.forEach { case ->
        onNodeWithTag(case.name).assertProperty(
            case.second,
            "${case.name} rewrites a changed value",
        ) { case.read(this) }
    }

    stage = 0
    awaitIdle()
    cases.forEach { case ->
        onNodeWithTag(case.name).assertProperty(
            originalValues.getValue(case),
            "${case.name} restores its original value",
        ) { case.read(this) }
    }
}
