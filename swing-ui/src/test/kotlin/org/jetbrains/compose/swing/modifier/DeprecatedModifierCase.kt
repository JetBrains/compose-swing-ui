package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.SwingStyleScope
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import javax.swing.JComponent

class DeprecatedModifierCase<S : SwingStyleScope>(
    val name: String,
    val expected: Any?,
    val host: @Composable (SwingModifier, SwingStyle<S>) -> Unit,
    val read: (JComponent) -> Any?,
    val modifier: SwingModifier,
    val style: SwingStyle<S>,
) {
    override fun toString(): String = name
}

private enum class Declared { None, Modifier, Style }

internal fun <S : SwingStyleScope> runDeprecatedModifierCase(case: DeprecatedModifierCase<S>) = runComposeSwingTest {
    underMetal {
        val targetTag = "target"
        var declared by mutableStateOf(Declared.None)
        setContent {
            case.host(
                SwingModifier
                    .testTag(targetTag)
                    .then(if (declared == Declared.Modifier) case.modifier else SwingModifier),
                if (declared == Declared.Style) case.style else SwingStyle,
            )
        }
        val component = onNodeWithTag(targetTag).assertIsOfType<JComponent>()

        var original: Any? = null
        component.assertProperty(true) {
            original = case.read(this)
            true
        }

        declared = Declared.Modifier
        awaitIdle()
        component.assertProperty(
            case.expected,
            "${case.name}: the modifier writes the literal value",
        ) { case.read(this) }

        declared = Declared.None
        awaitIdle()
        component.assertProperty(original) { case.read(this) }

        declared = Declared.Style
        awaitIdle()
        component.assertProperty(
            case.expected,
            "${case.name}: the style setter writes the literal value",
        ) { case.read(this) }

        declared = Declared.None
        awaitIdle()
        component.assertProperty(original) { case.read(this) }
    }
}
