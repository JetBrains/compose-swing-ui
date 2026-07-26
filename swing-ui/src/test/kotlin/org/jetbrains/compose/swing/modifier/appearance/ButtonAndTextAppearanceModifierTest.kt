package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import java.awt.Color
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JMenuItem
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A flat button and a recoloured text selection are properties of the components themselves, so they
 * are carried by modifiers rather than by each wrapper. These pin that the value is applied, that it
 * follows the state driving it, and that removing it restores the value the component carried before.
 *
 * Three of them - a button's border painting, its content-area fill and its icon-text gap - a component
 * built on a button takes from the look and feel only until something writes it. Declaring the value the
 * component already carries writes nothing, so a look and feel installed next still answers for the
 * property; declaring any other value writes it, and that value survives the change. Both are pinned.
 */
class ButtonAndTextAppearanceModifierTest {
    @Test
    fun aButtonCanBeAskedToPaintNothingButItsContent() = runComposeSwingTest {
        setContent {
            Button(
                "Save",
                modifier =
                    SwingModifier
                        .borderPainted(false)
                        .contentAreaFilled(false)
                        .focusPainted(false),
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        assertFalse(button.isBorderPainted, "borderPainted")
        assertFalse(button.isContentAreaFilled, "contentAreaFilled")
        assertFalse(button.isFocusPainted, "focusPainted")
    }

    @Test
    fun droppingThePaintFlagsRestoresWhatTheButtonPainted() = runComposeSwingTest {
        var flat by mutableStateOf(true)
        setContent {
            Button("Save", modifier = if (flat) SwingModifier.borderPainted(false) else SwingModifier)
        }

        val button = onNodeOfType<JButton>().fetch()
        assertFalse(button.isBorderPainted, "the flag applies while declared")

        flat = false
        awaitIdle()

        assertTrue(button.isBorderPainted, "dropping it restores the button's own painting")
    }

    @Test
    fun textColoursReachATextField() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                modifier =
                    SwingModifier
                        .caretColor(Color.RED)
                        .selectionColor(Color.GREEN)
                        .selectedTextColor(Color.BLUE)
                        .disabledTextColor(Color.GRAY),
            )
        }

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(Color.RED, field.caretColor, "caretColor")
        assertEquals(Color.GREEN, field.selectionColor, "selectionColor")
        assertEquals(Color.BLUE, field.selectedTextColor, "selectedTextColor")
        assertEquals(Color.GRAY, field.disabledTextColor, "disabledTextColor")
    }

    @Test
    fun aCaretColourFollowsTheStateDrivingIt() = runComposeSwingTest {
        var color by mutableStateOf(Color.RED)
        setContent { TextField(value = "", modifier = SwingModifier.caretColor(color)) }

        val field = onNodeOfType<JTextField>().fetch()
        assertEquals(Color.RED, field.caretColor, "the declared colour")

        color = Color.BLUE
        awaitIdle()

        assertEquals(Color.BLUE, field.caretColor, "the colour follows its state")
    }

    @Test
    fun droppingACaretColourRestoresTheLookAndFeelsOwn() = runComposeSwingTest {
        var recoloured by mutableStateOf(true)
        setContent {
            TextField(value = "", modifier = if (recoloured) SwingModifier.caretColor(Color.RED) else SwingModifier)
        }

        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caretColor
        assertEquals(Color.RED, installed, "the colour applies while declared")

        recoloured = false
        awaitIdle()

        // The element captured the look and feel's own colour before writing, so that is what returns.
        assertTrue(field.caretColor != Color.RED, "dropping it restores the look and feel's colour")
    }

    @Test
    fun aMarginReachesBothAButtonAndATextField() = runComposeSwingTest {
        val margin = Insets(3, 7, 3, 7)
        setContent {
            Button("Save", modifier = SwingModifier.margin(margin))
            TextField(value = "", modifier = SwingModifier.margin(margin))
        }

        assertEquals(margin, onNodeOfType<JButton>().fetch().margin, "button")
        assertEquals(margin, onNodeOfType<JTextField>().fetch().margin, "field")
    }

    @Test
    fun droppingAMarginRestoresTheLookAndFeelsOwn() = runComposeSwingTest {
        var inset by mutableStateOf(true)
        setContent {
            Button("Save", modifier = if (inset) SwingModifier.margin(Insets(9, 9, 9, 9)) else SwingModifier)
        }

        val button = onNodeOfType<JButton>().fetch()
        assertEquals(Insets(9, 9, 9, 9), button.margin, "the margin applies while declared")

        inset = false
        awaitIdle()

        assertTrue(button.margin != Insets(9, 9, 9, 9), "dropping it restores the look and feel's margin")
    }

    @Test
    fun declaringThePaintingAMenuItemAlreadyCarriesLeavesTheLookAndFeelInCharge() = runComposeSwingTest {
        underMetal {
            // A menu item is built on a button, so it is in the scope of this modifier, and unlike a
            // plain button it takes its border painting from a look-and-feel default that can be
            // changed under it.
            val popup = composeMenu { MenuItem("Open", modifier = SwingModifier.borderPainted(true)) }
            val item = popup.getComponent(0) as JMenuItem
            assertTrue(item.isBorderPainted, "the declared painting is what the item shows")

            // A component built on a button takes this property from the look and feel only until
            // something writes it. A declared value matching what the component already carries leaves
            // the look and feel in charge, so its later answer still reaches the item.
            withLookAndFeelDefaults(MENU_ITEM_BORDER_PAINTED to false) {
                SwingUtilities.updateComponentTreeUI(popup)
            }

            assertFalse(item.isBorderPainted, "the item follows the look and feel's answer")
        }
    }

    @Test
    fun aLookAndFeelChangeReachesADeclaredFillOnlyWhereItMatchedTheComponentsOwn() = runComposeSwingTest {
        underMetal {
            // Everything built on a button fills its content area, and a Basic look and feel writes
            // nothing over that, so the button declares the fill it already carries while the check box
            // declares one it does not.
            setContent {
                Button("Save", modifier = SwingModifier.contentAreaFilled(true))
                CheckBox("Wrap", checked = false, modifier = SwingModifier.contentAreaFilled(false))
            }
            val button = onNodeOfType<JButton>().fetch()
            val box = onNodeOfType<JCheckBox>().fetch()
            assertTrue(button.isContentAreaFilled, "the button shows the declared fill")
            assertFalse(box.isContentAreaFilled, "the check box shows the declared fill")

            // A Synth look and feel installs this property, filling the content area unless a default
            // answers otherwise, so both components are offered an answer of their own.
            withLookAndFeelDefaults(BUTTON_CONTENT_AREA_FILLED to false) {
                UIManager.setLookAndFeel(NimbusLookAndFeel())
                SwingUtilities.updateComponentTreeUI(root)
            }

            assertFalse(button.isContentAreaFilled, "nothing was written, so the look and feel answers")
            assertFalse(box.isContentAreaFilled, "the declared fill outlasts a look and feel that fills")
        }
    }

    @Test
    fun aLookAndFeelChangeReachesADeclaredGapOnlyWhereItMatchedTheComponentsOwn() = runComposeSwingTest {
        underMetal {
            // A Basic look and feel installs one gap on everything built on a button, so the button
            // declares the gap it already carries while the check box declares one it does not.
            setContent {
                Button("Save", modifier = SwingModifier.iconTextGap(INSTALLED_GAP))
                CheckBox("Wrap", checked = false, modifier = SwingModifier.iconTextGap(DECLARED_GAP))
            }
            val button = onNodeOfType<JButton>().fetch()
            val box = onNodeOfType<JCheckBox>().fetch()
            assertEquals(INSTALLED_GAP, button.iconTextGap, "the button shows the declared gap")
            assertEquals(DECLARED_GAP, box.iconTextGap, "the check box shows the declared gap")

            // A Synth look and feel takes this gap from a default of its own, so both components are
            // offered a gap neither of them declared.
            withLookAndFeelDefaults(
                BUTTON_ICON_TEXT_GAP to CHANGED_GAP,
                CHECK_BOX_ICON_TEXT_GAP to CHANGED_GAP,
            ) {
                UIManager.setLookAndFeel(NimbusLookAndFeel())
                SwingUtilities.updateComponentTreeUI(root)
            }

            assertEquals(CHANGED_GAP, button.iconTextGap, "nothing was written, so the look and feel answers")
            assertEquals(DECLARED_GAP, box.iconTextGap, "the declared gap outlasts the look and feel's own")
        }
    }

    /**
     * Runs [body] with [defaults] answering for the properties a look and feel installs, and drops them
     * afterwards. A key put here outranks every look and feel's own answer and survives a change of one,
     * so it is process-wide in the way a look and feel is; dropping it leaves untouched whatever an
     * install already wrote, which is what a caller reads afterwards.
     */
    private inline fun <R> withLookAndFeelDefaults(
        vararg defaults: Pair<String, Any>,
        body: () -> R,
    ): R {
        defaults.forEach { (key, value) -> UIManager.put(key, value) }
        try {
            return body()
        } finally {
            defaults.forEach { (key, _) -> UIManager.put(key, null) }
        }
    }

    private companion object {
        /** The look-and-feel default a menu item takes its border painting from. */
        const val MENU_ITEM_BORDER_PAINTED = "MenuItem.borderPainted"

        /** The look-and-feel default a Synth look and feel fills a button's content area from. */
        const val BUTTON_CONTENT_AREA_FILLED = "Button.contentAreaFilled"

        /** The look-and-feel defaults a Synth look and feel takes these two components' gaps from. */
        const val BUTTON_ICON_TEXT_GAP = "Button.iconTextGap"
        const val CHECK_BOX_ICON_TEXT_GAP = "CheckBox.iconTextGap"

        /** The gap a Basic look and feel installs on everything built on a button. */
        const val INSTALLED_GAP = 4

        /** A gap no look and feel installs, so declaring it is a write. */
        const val DECLARED_GAP = 11

        /** The gap the look and feel installed after the change answers with. */
        const val CHANGED_GAP = 13
    }
}
