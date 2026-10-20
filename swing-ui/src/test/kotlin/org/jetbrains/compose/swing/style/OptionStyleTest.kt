package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuGroup
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JMenu
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals

class OptionStyleTest {
    @Test
    fun aRadioGroupOptionsOwnStyleAppliesToThatOptionOnly() = runComposeSwingTest {
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            RadioGroup(selectedIndex = 0, onSelectionChange = {}) {
                option("a")
                option("b", style = { enabled(false) })
                option("c", style = { background(color) })
            }
        }
        val options = onAllNodesOfType<JRadioButton>()

        listOf(true, false, true).forEachIndexed { index, expected ->
            options[index].assertProperty(expected) { isEnabled }
        }
        for (expected in listOf(Color.MAGENTA, Color.RED)) {
            color = expected
            awaitIdle()
            repeat(3) { index ->
                options[index].assertProperty(index == 2, "only option c takes $expected") { background == expected }
            }
        }
    }

    @Test
    fun aRadioButtonMenuGroupOptionsOwnStyleAppliesToThatOptionOnly() = runComposeSwingTest {
        val popup =
            composeMenu {
                Menu("View") {
                    RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) {
                        option("a")
                        option("b", style = { enabled(false) })
                    }
                }
            }
        val menu = popup.getComponent(0) as JMenu

        assertEquals(
            listOf(true, false),
            menu.menuComponents.map { it.isEnabled },
            "only the option styled so is disabled",
        )
    }
}
