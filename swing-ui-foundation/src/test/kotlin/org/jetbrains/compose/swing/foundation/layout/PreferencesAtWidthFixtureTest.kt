package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

private val FillingBox: Map<String, ContainerContent> =
    mapOf(
        "Box" to { modifier, leaf ->
            Box(modifier = modifier) { leaf(SwingModifier.fillMaxWidth()) }
        },
    )

class PreferencesAtWidthFixtureTest {
    @Test
    fun aTextAreaGivenTwoWidthsWithinOneEventIsReported() {
        val leaf =
            mapOf(
                "HalfWidthFirstPanel" to @Composable { modifier: SwingModifier ->
                    SwingNode(factory = ::HalfWidthFirstPanel, modifier = modifier)
                },
            )
        val failure =
            assertFailsWith<AssertionError> {
                assertPreferencesSettle(FillingBox, slots = mapOf("BorderLayout NORTH" to leaf))
            }
        assertContains(failure.message.orEmpty(), "JTextArea is given another width")
    }

    @Test
    fun aSlotNameOutsideThePreferenceSlotsFails() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                assertPreferencesSettle(FillingBox, slots = mapOf("BorderLayout North" to DefaultLeaves))
            }
        assertContains(failure.message.orEmpty(), "BorderLayout North")
    }
}

private class HalfWidthFirstPanel : JPanel(BorderLayout()) {
    init {
        add(ResizeCountingTextArea())
    }

    override fun doLayout() {
        getComponent(0).setSize(width / 2, height)
        super.doLayout()
    }
}
