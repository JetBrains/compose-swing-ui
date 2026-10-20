package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.interaction.caret
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.border.LineBorder
import javax.swing.text.Caret
import javax.swing.text.DefaultCaret
import kotlin.test.Test

class DeprecatedStyleModifiersTest {
    @Test
    // Tests deprecated border forwarding beside the style declared last.
    @Suppress("DEPRECATION")
    fun styleBorderOverridesChangingModifier() = runComposeSwingTest {
        var modifierColor by mutableStateOf(Color.BLUE)
        setContent {
            Label(
                text = "text",
                modifier = SwingModifier.lineBorder(modifierColor, 3),
                style = { lineBorder(Color.RED, 2) },
            )
        }
        onNodeOfType<JLabel>().apply {
            assertProperty(Color.RED) { (border as? LineBorder)?.lineColor }
            assertProperty(Insets(2, 2, 2, 2)) { insets }
        }

        modifierColor = Color.GREEN
        awaitIdle()

        onNodeOfType<JLabel>().apply {
            assertProperty(Color.RED) { (border as? LineBorder)?.lineColor }
            assertProperty(Insets(2, 2, 2, 2)) { insets }
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("customPropertyOverlaps")
    fun customPropertiesShareModifierAndStyleBaseline(
        property: OverlapProperty,
        order: RemovalOrder,
    ) = runComposeSwingTest {
        assertCustomPropertyBaseline(property, order)
    }

    companion object {
        @JvmStatic
        fun customPropertyOverlaps(): List<Arguments> = listOf(OverlapProperty.Caret)
            .flatMap { property -> RemovalOrder.entries.map { order -> Arguments.of(property, order) } }
    }
}

// Covers the deprecated modifier beside the style.
@Suppress("DEPRECATION")
internal fun caretOverlapProperty(): CustomOverlapProperty {
    val values = List<Caret>(3) { DefaultCaret() }
    return CustomOverlapProperty(
        read = { it.caret },
        value = { values[it] },
        modifier = { SwingModifier.caret(values[it]) },
        declare = { caret(values[it]) },
    )
}
