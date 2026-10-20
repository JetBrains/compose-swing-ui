package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import java.awt.Color
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JMenuItem
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.nimbus.NimbusLookAndFeel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A flat button and a recolored text selection are properties of the components themselves, so they
 * are carried by modifiers rather than by each wrapper. These pin that the value is applied, that it
 * follows the state driving it, and that removing it restores the value the component carried before.
 *
 * Three of them - a button's border painting, its content-area fill and its icon-text gap - a component
 * built on a button takes from the look and feel only until something writes it. Declaring the value the
 * component already carries writes nothing, so a look and feel installed next still answers for the
 * property; declaring any other value writes it, and that value survives the change. Both are pinned.
 *
 * A Synth look and feel is one built on [javax.swing.plaf.synth.SynthLookAndFeel], which takes widget
 * values from a style of its own instead of publishing them under `UIManager` keys. Nimbus is the one
 * these cases install.
 */
class ButtonAndTextAppearanceModifierTest {
    @Test
    fun aButtonCanBeAskedToPaintNothingButItsContent() = runComposeSwingTest {
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    SwingModifier
                        .borderPainted(false)
                        .contentAreaFilled(false)
                        .focusPainted(false),
            )
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty(false, "borderPainted") { isBorderPainted }
        button.assertProperty(false, "contentAreaFilled") { isContentAreaFilled }
        button.assertProperty(false, "focusPainted") { isFocusPainted }
    }

    @Test
    fun droppingThePaintFlagsRestoresWhatTheButtonPainted() = runComposeSwingTest {
        var flat by mutableStateOf(true)
        setContent {
            Button("Save", onClick = { }, modifier = if (flat) SwingModifier.borderPainted(false) else SwingModifier)
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty(false, "the flag applies while declared") { isBorderPainted }

        flat = false
        awaitIdle()

        button.assertProperty(true, "dropping it restores the button's own painting") { isBorderPainted }
    }

    @Test
    fun aDeclaredOpaqueFlagStandsWhenTheFillTheLookAndFeelReadsItFromChanges() = runComposeSwingTest {
        underMetal {
            var filled by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    modifier = SwingModifier.contentAreaFilled(filled),
                    style = { opaque(true) },
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the declared flag is what the button carries") { isOpaque }

            filled = false
            awaitIdle()

            // A look and feel's button listener writes the opaque flag from the fill every time the fill
            // changes. Only the fill is declared anew on this pass, so nothing rewrites the flag unless
            // the declaration answers the button's own change.
            button.assertProperty(
                true,
                "the declared flag stands after the look and feel answers the new fill",
            ) { isOpaque }
        }
    }

    @Test
    fun droppingTheOpaqueFlagLeavesAButtonAtTheFillThatStands() = runComposeSwingTest {
        underMetal {
            var declared by mutableStateOf(true)
            setContent {
                Button(
                    "Save",
                    onClick = { },
                    modifier = SwingModifier.contentAreaFilled(false),
                    style = { if (declared) opaque(true) },
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the declared flag is what the button carries") { isOpaque }

            declared = false
            awaitIdle()

            // A look and feel keeps the flag in step with the fill, so the flag a button holds while
            // they agree is that rule's and not its own to hand back.
            button.assertProperty(false, "dropping the flag leaves the button at the fill it declares") { isOpaque }
        }
    }

    @Test
    fun droppingTheContentFillLeavesTheButtonAtTheOpacityItCarried() = runComposeSwingTest {
        underMetal {
            var declared by mutableStateOf(true)
            setContent {
                SwingNode(
                    // A look and feel is free to leave a button transparent over content it fills, which
                    // is what makes the flag the fill writes distinguishable from the fill itself.
                    factory = { JButton("Save").apply { isOpaque = false } },
                    modifier = if (declared) SwingModifier.contentAreaFilled(false) else SwingModifier,
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(false, "the declared fill is what the button carries") { isContentAreaFilled }
            button.assertProperty(false, "the button is left at the flag it carried") { isOpaque }

            declared = false
            awaitIdle()

            button.assertProperty(true, "dropping the fill restores the one the button had") { isContentAreaFilled }
            // Putting the fill back writes the flag kept in step with it, so the flag is put back
            // after it.
            button.assertProperty(false, "the flag nothing declares is left where the button carried it") { isOpaque }
        }
    }

    @Test
    fun twoPropertiesLeavingTogetherEachPutBackTheirOwn() = runComposeSwingTest {
        // Each property a modifier writes is captured under its own slot. Two properties reached through
        // one pair of accessors - which every property named for more than one kind of widget is - are
        // still two, and a pass dropping both has to put each back through its own.
        var declared by mutableStateOf(false)
        val declaredMargin = Insets(9, 9, 9, 9)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    if (declared) SwingModifier.mnemonic('S').margin(declaredMargin) else SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>().fetch()
        val carried = button.margin

        declared = true
        awaitIdle()
        assertEquals(KeyEvent.VK_S, button.mnemonic, "the declared mnemonic is what the button carries")
        assertEquals(declaredMargin, button.margin, "the declared margin is what the button carries")

        declared = false
        awaitIdle()

        assertEquals(0, button.mnemonic, "dropping the mnemonic leaves the button without one")
        assertEquals(carried, button.margin, "and dropping the margin puts back the one it came with")
    }

    @Test
    fun aFillDeclaredUnderAStandingFlagPutsBackTheOneTheButtonCameWith() = runComposeSwingTest {
        underMetal {
            var flag by mutableStateOf(false)
            var fill by mutableStateOf(false)
            setContent {
                SwingNode(
                    // A look and feel is free to leave a button transparent over content it fills.
                    factory = { JButton("Save").apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .let { if (fill) it.contentAreaFilled(false) else it }
                            .styleable { if (flag) opaque(true) },
                )
            }

            val button = onNodeOfType<JButton>()
            button.assertProperty(true, "the button starts filled") { isContentAreaFilled }
            button.assertProperty(false, "and transparent, which is not the flag the fill implies") { isOpaque }

            // The flag is declared first, so the fill joins the modifier over a flag that already stands
            // written. What the fill puts back has to be the flag the button came with, not that one.
            flag = true
            awaitIdle()
            fill = true
            awaitIdle()
            flag = false
            awaitIdle()
            fill = false
            awaitIdle()

            button.assertProperty(true, "dropping the fill restores the one the button had") { isContentAreaFilled }
            button.assertProperty(false, "and the flag it came with rather than the one that fill implies") { isOpaque }
        }
    }

    @Test
    fun applyingAndRemovingOpaqueAndContentAreaFilledTogetherRestoresTheOriginalFlag() = runComposeSwingTest {
        underMetal {
            // contentAreaFilled's handle names "opaque" in its alsoOverwrites, so its slot shares one
            // restore hold with opaque's own declared slot even though the two are independent
            // declarations - proving the hold is shared by name, not by which handle instance wrote it.
            var declared by mutableStateOf(false)
            setContent {
                SwingNode(
                    factory = { JButton("Save") },
                    modifier =
                        SwingModifier
                            .let { if (declared) it.contentAreaFilled(false) else it }
                            .styleable { if (declared) opaque(true) },
                )
            }

            val button = onNodeOfType<JButton>().fetch()
            val original = button.isOpaque

            declared = true
            awaitIdle()
            declared = false
            awaitIdle()

            assertEquals(original, button.isOpaque, "removing both restores the flag the button was built with")
        }
    }

    @Test
    fun droppingTheOpaqueFlagLeavesAMenuItemCarryingItsOwn() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        val popup =
            composeMenu {
                MenuItem(
                    "Open",
                    onClick = { },
                    modifier = SwingModifier.contentAreaFilled(false),
                    style = { if (declared) opaque(false) },
                )
            }
        val item = popup.getComponent(0) as JMenuItem
        assertFalse(item.isOpaque, "the declared flag is what the menu item carries")

        declared = false
        awaitIdle()

        // Nothing ties a menu item's opaque flag to the fill the way a look and feel ties a button's, so
        // what a menu item carries is its own and is what dropping the declaration puts back.
        assertEquals(
            JMenuItem("Open").isOpaque,
            item.isOpaque,
            "dropping the flag leaves the menu item where one carrying no declaration stands",
        )
    }

    @Test
    fun borderPaintingReachesTheComponentsOutsideAButtonsFamilyThatCarryIt() = runComposeSwingTest {
        // A progress bar and a tool bar declare border painting for themselves, sharing no supertype
        // that carries it with a button, so the modifier reaches each of them through its own accessor.
        setContent {
            ProgressBar(value = 50, modifier = SwingModifier.borderPainted(false))
            ToolBar(modifier = SwingModifier.borderPainted(false), floatable = false)
        }

        onNodeOfType<JProgressBar>().assertProperty(false, "progress bar") { isBorderPainted }
        onNodeOfType<JToolBar>().assertProperty(false, "tool bar") { isBorderPainted }
    }

    @Test
    fun textColorsReachATextField() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .caretColor(Color.RED)
                        .selectionColor(Color.GREEN)
                        .selectedTextColor(Color.BLUE)
                        .disabledTextColor(Color.GRAY),
            )
        }

        val field = onNodeOfType<JTextField>()
        field.assertProperty(Color.RED, "caretColor") { caretColor }
        field.assertProperty(Color.GREEN, "selectionColor") { selectionColor }
        field.assertProperty(Color.BLUE, "selectedTextColor") { selectedTextColor }
        field.assertProperty(Color.GRAY, "disabledTextColor") { disabledTextColor }
    }

    @Test
    fun aCaretColorFollowsTheStateDrivingIt() = runComposeSwingTest {
        var color by mutableStateOf(Color.RED)
        setContent { TextField(value = "", onValueChange = {}, modifier = SwingModifier.caretColor(color)) }

        val field = onNodeOfType<JTextField>()
        field.assertProperty(Color.RED, "the declared color") { caretColor }

        color = Color.BLUE
        awaitIdle()

        field.assertProperty(Color.BLUE, "the color follows its state") { caretColor }
    }

    @Test
    fun droppingACaretColorRestoresTheLookAndFeelsOwn() = runComposeSwingTest {
        var recolored by mutableStateOf(false)
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = if (recolored) SwingModifier.caretColor(Color.RED) else SwingModifier,
            )
        }

        val field = onNodeOfType<JTextField>().fetch()
        val installed = field.caretColor

        recolored = true
        awaitIdle()
        assertEquals(Color.RED, field.caretColor, "the color applies while declared")

        recolored = false
        awaitIdle()

        // The element captured the look and feel's own color before writing, so that is what returns.
        assertEquals(installed, field.caretColor, "dropping it restores the look and feel's color")
    }

    @Test
    fun aMarginReachesBothAButtonAndATextField() = runComposeSwingTest {
        val margin = Insets(3, 7, 3, 7)
        setContent {
            Button("Save", onClick = { }, modifier = SwingModifier.margin(margin))
            TextField(value = "", onValueChange = {}, modifier = SwingModifier.margin(margin))
        }

        onNodeOfType<JButton>().assertProperty(margin, "button") { this.margin }
        onNodeOfType<JTextField>().assertProperty(margin, "field") { this.margin }
    }

    @Test
    fun droppingAMarginRestoresTheLookAndFeelsOwn() = runComposeSwingTest {
        val declaredMargin = Insets(9, 9, 9, 9)
        // A button built the plain Swing way carries the look and feel's margin.
        val lookAndFeelsMargin = JButton().margin
        assertNotEquals(declaredMargin, lookAndFeelsMargin, "the declared margin must differ from the look and feel's")
        var inset by mutableStateOf(true)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier = if (inset) SwingModifier.margin(declaredMargin) else SwingModifier,
            )
        }

        val button = onNodeOfType<JButton>()
        button.assertProperty(declaredMargin, "the margin applies while declared") { margin }

        inset = false
        awaitIdle()

        button.assertProperty(lookAndFeelsMargin, "dropping it restores the look and feel's margin") { margin }
    }

    @Test
    fun declaringThePaintingAMenuItemAlreadyCarriesLeavesTheLookAndFeelInCharge() = runComposeSwingTest {
        underMetal {
            // A menu item is built on a button, so it is in the scope of this modifier, and unlike a
            // plain button it takes its border painting from a look-and-feel default that can be
            // changed under it.
            val popup = composeMenu { MenuItem("Open", onClick = { }, modifier = SwingModifier.borderPainted(true)) }
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
                Button("Save", onClick = { }, modifier = SwingModifier.contentAreaFilled(true))
                CheckBox(
                    "Wrap",
                    checked = false,
                    onCheckedChange = {},
                    modifier = SwingModifier.contentAreaFilled(false),
                )
            }
            val button = onNodeOfType<JButton>()
            val box = onNodeOfType<JCheckBox>()
            button.assertProperty(true, "the button shows the declared fill") { isContentAreaFilled }
            box.assertProperty(false, "the check box shows the declared fill") { isContentAreaFilled }

            // A Synth look and feel installs this property, filling the content area unless a default
            // answers otherwise, so both components are offered an answer of their own.
            withLookAndFeelDefaults(BUTTON_CONTENT_AREA_FILLED to false) {
                UIManager.setLookAndFeel(NimbusLookAndFeel())
                SwingUtilities.updateComponentTreeUI(root)
            }

            button.assertProperty(false, "nothing was written, so the look and feel answers") { isContentAreaFilled }
            box.assertProperty(false, "the declared fill outlasts a look and feel that fills") { isContentAreaFilled }
        }
    }

    @Test
    fun aLookAndFeelChangeReachesADeclaredGapOnlyWhereItMatchedTheComponentsOwn() = runComposeSwingTest {
        underMetal {
            // A Basic look and feel installs one gap on everything built on a button, so the button
            // declares the gap it already carries while the check box declares one it does not.
            setContent {
                Button("Save", onClick = { }, modifier = SwingModifier.iconTextGap(INSTALLED_GAP))
                CheckBox(
                    "Wrap",
                    checked = false,
                    onCheckedChange = {},
                    modifier = SwingModifier.iconTextGap(DECLARED_GAP),
                )
            }
            val button = onNodeOfType<JButton>()
            val box = onNodeOfType<JCheckBox>()
            button.assertProperty(INSTALLED_GAP, "the button shows the declared gap") { iconTextGap }
            box.assertProperty(DECLARED_GAP, "the check box shows the declared gap") { iconTextGap }

            // A Synth look and feel takes this gap from a default of its own, so both components are
            // offered a gap neither of them declared.
            withLookAndFeelDefaults(
                BUTTON_ICON_TEXT_GAP to CHANGED_GAP,
                CHECK_BOX_ICON_TEXT_GAP to CHANGED_GAP,
            ) {
                UIManager.setLookAndFeel(NimbusLookAndFeel())
                SwingUtilities.updateComponentTreeUI(root)
            }

            button.assertProperty(CHANGED_GAP, "nothing was written, so the look and feel answers") { iconTextGap }
            box.assertProperty(DECLARED_GAP, "the declared gap outlasts the look and feel's own") { iconTextGap }
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
