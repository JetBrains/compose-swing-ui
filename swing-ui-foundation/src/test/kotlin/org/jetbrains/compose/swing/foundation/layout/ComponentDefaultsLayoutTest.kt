// Covers the deprecated modifiers beside the style while both are supported.
@file:Suppress("DEPRECATION")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.ComponentOrientation
import kotlin.test.assertEquals

class ComponentDefaultsLayoutTest {
    @ParameterizedTest
    @EnumSource(OrientationProvider::class)
    fun aRowArrangesByTheOrientationTheComponentDefaultsProvide(provider: OrientationProvider) =
        runComposeSwingTest {
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            setContent {
                provider.provide(orientation) {
                    Row(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).styleable { preferredSize(300, CHILD_HEIGHT) },
                    ) {
                        repeat(CHILD_COUNT) { SizedChild(it) }
                    }
                }
            }

            assertEquals(rowCells(0, 50, 100), childBounds())

            orientation = ComponentOrientation.RIGHT_TO_LEFT
            awaitIdle()

            assertEquals(
                rowCells(250, 200, 150),
                childBounds(),
                "Arrangement.Start must pack the children against the right edge the provided orientation leads from",
            )
        }
}

enum class OrientationProvider(
    val provide: @Composable (ComponentOrientation, @Composable () -> Unit) -> Unit,
) {
    ComponentDefaults({ orientation, content ->
        ProvideComponentDefaults(DefaultComponentOrientation provides orientation, content = content)
    }),
    ContainerStyle({ orientation, content ->
        Box(modifier = SwingModifier.styleable { componentOrientation(orientation) }, content = { content() })
    }),
}
