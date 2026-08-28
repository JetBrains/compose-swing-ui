package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Component
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JProgressBar
import javax.swing.JRadioButton
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.LookAndFeel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Proves that every component wrapper is a faithful, side-effect-free view over its Swing widget: the
 * pixels it paints are identical to those of a hand-written raw widget configured with the same public
 * values.
 *
 * Any rendering side effect the wrapper might inject that the raw widget does not - a stray border, an
 * altered font or color, an extra margin, a shifted alignment, a changed opacity - shifts pixels and
 * fails the comparison.
 */
class WrapperRenderingSideEffectTest {
    private var hostLookAndFeel: LookAndFeel? = null

    @BeforeTest
    fun pinLookAndFeel() {
        // Pin a single, always-available, cross-platform LaF so composed and raw widgets resolve the
        // same fonts, colors, borders and insets regardless of the host OS.
        hostLookAndFeel = UIManager.getLookAndFeel()
        UIManager.setLookAndFeel(MetalLookAndFeel())
    }

    @AfterTest
    fun restoreLookAndFeel() {
        // The Look-and-Feel is process-wide state, and which one is installed decides the borders,
        // fonts and insets every later component resolves. Restoring it keeps this test's requirement
        // from deciding how the rest of the suite renders.
        UIManager.setLookAndFeel(hostLookAndFeel)
    }

    @Test
    fun labelAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { Label(text = LABEL_TEXT) },
        find = { onNodeOfType<JLabel>() },
        buildRaw = {
            JLabel().apply { text = LABEL_TEXT }
        },
    )

    @Test
    fun buttonAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { Button(text = BUTTON_TEXT, onClick = { }) },
        find = { onNodeOfType<JButton>() },
        buildRaw = { JButton().apply { text = BUTTON_TEXT } },
    )

    @Test
    fun uncheckedCheckBoxAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { CheckBox(text = CHECK_TEXT, checked = false, onCheckedChange = {}) },
        find = { onNodeOfType<JCheckBox>() },
        buildRaw = {
            JCheckBox().apply {
                text = CHECK_TEXT
                isSelected = false
            }
        },
    )

    @Test
    fun checkedCheckBoxAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { CheckBox(text = CHECK_TEXT, checked = true, onCheckedChange = {}) },
        find = { onNodeOfType<JCheckBox>() },
        buildRaw = {
            JCheckBox().apply {
                text = CHECK_TEXT
                isSelected = true
            }
        },
    )

    @Test
    fun unselectedRadioButtonAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { RadioButton(text = RADIO_TEXT, selected = false, onSelectedChange = {}) },
        find = { onNodeOfType<JRadioButton>() },
        buildRaw = {
            JRadioButton().apply {
                text = RADIO_TEXT
                isSelected = false
            }
        },
    )

    @Test
    fun selectedRadioButtonAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { RadioButton(text = RADIO_TEXT, selected = true, onSelectedChange = {}) },
        find = { onNodeOfType<JRadioButton>() },
        buildRaw = {
            JRadioButton().apply {
                text = RADIO_TEXT
                isSelected = true
            }
        },
    )

    @Test
    fun selectedToggleButtonAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { ToggleButton(text = TOGGLE_TEXT, selected = true, onSelectedChange = {}) },
        find = { onNodeOfType<JToggleButton>() },
        buildRaw = {
            JToggleButton().apply {
                text = TOGGLE_TEXT
                isSelected = true
            }
        },
    )

    @Test
    fun sliderAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { Slider(value = SLIDER_VALUE, onValueChange = {}, min = 0, max = 100) },
        find = { onNodeOfType<JSlider>() },
        buildRaw = { JSlider(0, 100, SLIDER_VALUE) },
    )

    @Test
    fun progressBarAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { ProgressBar(value = PROGRESS_VALUE, min = 0, max = 100) },
        find = { onNodeOfType<JProgressBar>() },
        buildRaw = { JProgressBar(0, 100).apply { value = PROGRESS_VALUE } },
    )

    @Test
    fun horizontalSeparatorAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        // A horizontal separator has zero preferred height; give it an explicit size on both sides
        // so it lays out to identical, non-zero bounds and can be captured.
        content = {
            Separator(
                modifier = SwingModifier.preferredSize(Dimension(SEPARATOR_WIDTH, SEPARATOR_HEIGHT)),
                orientation = SwingConstants.HORIZONTAL,
            )
        },
        find = { onNodeOfType<JSeparator>() },
        buildRaw = {
            JSeparator(SwingConstants.HORIZONTAL).apply {
                preferredSize = Dimension(SEPARATOR_WIDTH, SEPARATOR_HEIGHT)
            }
        },
    )

    @Test
    fun textFieldAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { TextField(value = FIELD_TEXT, onValueChange = {}) },
        find = { onNodeOfType<JTextField>() },
        buildRaw = { JTextField(0).apply { text = FIELD_TEXT } },
    )

    @Test
    fun textAreaAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { TextArea(value = AREA_TEXT, onValueChange = {}) },
        find = { onNodeOfType<JTextArea>() },
        buildRaw = { JTextArea(0, 0).apply { text = AREA_TEXT } },
    )

    @Test
    fun passwordFieldAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { PasswordField(value = PASSWORD.toCharArray(), onValueChange = {}) },
        find = { onNodeOfType<JPasswordField>() },
        buildRaw = { JPasswordField(0).apply { text = PASSWORD } },
    )

    @Test
    fun comboBoxAddsNoRenderingSideEffects() = assertWrapperMatchesRaw(
        content = { ComboBox(items = COMBO_ITEMS, selectedItem = COMBO_ITEMS.first(), onSelectionChange = {}) },
        find = { onNodeOfType<JComboBox<*>>() },
        buildRaw = {
            JComboBox<String>().apply {
                COMBO_ITEMS.forEach { addItem(it) }
                selectedIndex = 0
            }
        },
    )

    /**
     * Renders [content] under the harness, finds the composed widget via [find], captures it at its
     * laid-out bounds, builds the raw equivalent via [buildRaw] at the same bounds, and asserts the two
     * captures are pixel-identical.
     */
    private fun assertWrapperMatchesRaw(
        content: @Composable () -> Unit,
        find: ComposeSwingTest.() -> SwingNodeInteraction<Component>,
        buildRaw: () -> Component,
    ) = runComposeSwingTest {
        setContent { content() }

        val node = find()
        val composed: BufferedImage = node.captureToImage()
        val raw: BufferedImage =
            buildRaw()
                .apply {
                    // A composite widget (e.g. JComboBox) positions its internal children - the
                    // arrow button, an editor - via its own LayoutManager; doLayout() is what runs
                    // it. The composed equivalent gets this from the harness's own layout pass.
                    setBounds(0, 0, composed.width, composed.height)
                    doLayout()
                }.captureToImage()

        assertImagesPixelPerfect(
            expected = raw,
            image = composed,
            maxDifferentPixels = 0,
        )
    }

    private companion object {
        const val LABEL_TEXT = "Label text"
        const val BUTTON_TEXT = "Click me"
        const val CHECK_TEXT = "Enable feature"
        const val RADIO_TEXT = "Option A"
        const val TOGGLE_TEXT = "Bold"
        const val FIELD_TEXT = "field content"
        const val AREA_TEXT = "area content"
        const val PASSWORD = "secret"
        const val SLIDER_VALUE = 42
        const val PROGRESS_VALUE = 70
        const val SEPARATOR_WIDTH = 120
        const val SEPARATOR_HEIGHT = 8
        val COMBO_ITEMS = listOf("Alpha", "Beta", "Gamma")
    }
}
