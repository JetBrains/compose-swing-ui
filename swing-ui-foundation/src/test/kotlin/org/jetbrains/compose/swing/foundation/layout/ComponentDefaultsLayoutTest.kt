package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.ComponentOrientation
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentDefaultsLayoutTest {
    /** No modifier names the row's orientation: it takes the one the component defaults provide. */
    @Test
    fun aRowArrangesByTheOrientationTheComponentDefaultsProvide() =
        runComposeSwingTest {
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            setContent {
                ProvideComponentDefaults(DefaultComponentOrientation provides orientation) {
                    Row(modifier = SwingModifier.testTag(CONTAINER_TAG).preferredSize(300, CHILD_HEIGHT)) {
                        repeat(CHILD_COUNT) { SizedChild(it) }
                    }
                }
            }

            orientation = ComponentOrientation.RIGHT_TO_LEFT
            awaitIdle()

            assertEquals(
                rowCells(250, 200, 150),
                childBounds(),
                "Arrangement.Start must pack the children against the right edge the provided orientation leads from",
            )
        }
}
