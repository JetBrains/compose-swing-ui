package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.components.style.actionCommand
import org.jetbrains.compose.swing.components.style.borderPainted
import org.jetbrains.compose.swing.components.style.contentAreaFilled
import org.jetbrains.compose.swing.components.style.disabledIcon
import org.jetbrains.compose.swing.components.style.disabledSelectedIcon
import org.jetbrains.compose.swing.components.style.displayedMnemonicIndex
import org.jetbrains.compose.swing.components.style.focusPainted
import org.jetbrains.compose.swing.components.style.horizontalAlignment
import org.jetbrains.compose.swing.components.style.horizontalTextPosition
import org.jetbrains.compose.swing.components.style.icon
import org.jetbrains.compose.swing.components.style.iconTextGap
import org.jetbrains.compose.swing.components.style.margin
import org.jetbrains.compose.swing.components.style.mnemonic
import org.jetbrains.compose.swing.components.style.pressedIcon
import org.jetbrains.compose.swing.components.style.rolloverEnabled
import org.jetbrains.compose.swing.components.style.rolloverIcon
import org.jetbrains.compose.swing.components.style.rolloverSelectedIcon
import org.jetbrains.compose.swing.components.style.selectedIcon
import org.jetbrains.compose.swing.components.style.verticalAlignment
import org.jetbrains.compose.swing.components.style.verticalTextPosition
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.appearance.borderPainted
import org.jetbrains.compose.swing.modifier.appearance.contentAreaFilled
import org.jetbrains.compose.swing.modifier.appearance.disabledIcon
import org.jetbrains.compose.swing.modifier.appearance.disabledSelectedIcon
import org.jetbrains.compose.swing.modifier.appearance.focusPainted
import org.jetbrains.compose.swing.modifier.appearance.horizontalAlignment
import org.jetbrains.compose.swing.modifier.appearance.horizontalTextPosition
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.iconTextGap
import org.jetbrains.compose.swing.modifier.appearance.margin
import org.jetbrains.compose.swing.modifier.appearance.pressedIcon
import org.jetbrains.compose.swing.modifier.appearance.rolloverEnabled
import org.jetbrains.compose.swing.modifier.appearance.rolloverIcon
import org.jetbrains.compose.swing.modifier.appearance.rolloverSelectedIcon
import org.jetbrains.compose.swing.modifier.appearance.selectedIcon
import org.jetbrains.compose.swing.modifier.appearance.verticalAlignment
import org.jetbrains.compose.swing.modifier.appearance.verticalTextPosition
import org.jetbrains.compose.swing.modifier.interaction.actionCommand
import org.jetbrains.compose.swing.style.SwingStyle
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.SwingConstants.BOTTOM
import javax.swing.SwingConstants.LEFT
import javax.swing.SwingConstants.RIGHT
import javax.swing.SwingConstants.TOP

// The test calls the deprecated modifiers on purpose to pin their forwarding.

/**
 * A deprecated modifier leaves the component with what the style setter of the same name writes, and removing it
 * restores the value the component held before.
 */
@Suppress("DEPRECATION")
class DeprecatedButtonAndLabelModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun modifierAndStyleApplyAndRestore(case: DeprecatedModifierCase<*>) = runDeprecatedModifierCase(case)

    private class TestIcon : Icon {
        override fun paintIcon(
            c: Component?,
            g: Graphics?,
            x: Int,
            y: Int,
        ) = Unit

        override fun getIconWidth(): Int = 8

        override fun getIconHeight(): Int = 8
    }

    companion object {
        private val testIcon: Icon = TestIcon()

        @JvmStatic
        fun cases(): List<Named<*>> = labelPlacementCases() + labelMnemonicCases() + buttonPlacementCases() +
            buttonIconStateCases() + buttonPaintingCases() + alternateValues()

        private fun label(
            name: String,
            expected: Any?,
            read: (JLabel) -> Any?,
            modifier: SwingModifier,
            style: SwingStyle<LabelStyleScope>,
        ): Named<*> = named(
            DeprecatedModifierCase<LabelStyleScope>(
                "Label $name",
                expected,
                { m, s -> Label("Save As", m, s) },
                { read(it as JLabel) },
                modifier,
                style,
            ),
        )

        private fun button(
            name: String,
            expected: Any?,
            read: (AbstractButton) -> Any?,
            modifier: SwingModifier,
            style: SwingStyle<ButtonStyleScope>,
        ): Named<*> = named(
            DeprecatedModifierCase<ButtonStyleScope>(
                "Button $name",
                expected,
                { m, s -> Button("Save As", {}, m, s) },
                { read(it as AbstractButton) },
                modifier,
                style,
            ),
        )

        private fun named(case: DeprecatedModifierCase<*>): Named<*> = Named.of(case.name, case)

        private fun labelPlacementCases(): List<Named<*>> = listOf(
            label("icon", testIcon, { it.icon }, SwingModifier.icon(testIcon)) { icon(testIcon) },
            label("horizontalAlignment", RIGHT, { it.horizontalAlignment }, SwingModifier.horizontalAlignment(RIGHT)) {
                horizontalAlignment(RIGHT)
            },
            label("verticalAlignment", TOP, { it.verticalAlignment }, SwingModifier.verticalAlignment(TOP)) {
                verticalAlignment(TOP)
            },
            label(
                "horizontalTextPosition",
                LEFT,
                { it.horizontalTextPosition },
                SwingModifier.horizontalTextPosition(LEFT),
            ) { horizontalTextPosition(LEFT) },
            label("verticalTextPosition", TOP, { it.verticalTextPosition }, SwingModifier.verticalTextPosition(TOP)) {
                verticalTextPosition(TOP)
            },
            label("iconTextGap", 9, { it.iconTextGap }, SwingModifier.iconTextGap(9)) { iconTextGap(9) },
        )

        private fun labelMnemonicCases(): List<Named<*>> = listOf(
            label("mnemonic(keyCode)", KeyEvent.VK_S, { it.displayedMnemonic }, SwingModifier.mnemonic(KeyEvent.VK_S)) {
                mnemonic(KeyEvent.VK_S)
            },
            label("mnemonic(char)", KeyEvent.VK_S, { it.displayedMnemonic }, SwingModifier.mnemonic('S')) {
                mnemonic('S')
            },
            label("displayedMnemonicIndex", 5, { it.displayedMnemonicIndex }, SwingModifier.displayedMnemonicIndex(5)) {
                displayedMnemonicIndex(5)
            },
        )

        private fun buttonPlacementCases(): List<Named<*>> = listOf(
            button("horizontalAlignment", RIGHT, { it.horizontalAlignment }, SwingModifier.horizontalAlignment(RIGHT)) {
                horizontalAlignment(RIGHT)
            },
            button("verticalAlignment", TOP, { it.verticalAlignment }, SwingModifier.verticalAlignment(TOP)) {
                verticalAlignment(TOP)
            },
            button(
                "horizontalTextPosition",
                LEFT,
                { it.horizontalTextPosition },
                SwingModifier.horizontalTextPosition(LEFT),
            ) { horizontalTextPosition(LEFT) },
            button("verticalTextPosition", TOP, { it.verticalTextPosition }, SwingModifier.verticalTextPosition(TOP)) {
                verticalTextPosition(TOP)
            },
            button("iconTextGap", 9, { it.iconTextGap }, SwingModifier.iconTextGap(9)) { iconTextGap(9) },
            button("mnemonic(keyCode)", KeyEvent.VK_S, { it.mnemonic }, SwingModifier.mnemonic(KeyEvent.VK_S)) {
                mnemonic(KeyEvent.VK_S)
            },
            button("mnemonic(char)", KeyEvent.VK_S, { it.mnemonic }, SwingModifier.mnemonic('S')) { mnemonic('S') },
            button(
                "displayedMnemonicIndex",
                5,
                { it.displayedMnemonicIndex },
                SwingModifier.displayedMnemonicIndex(5),
            ) { displayedMnemonicIndex(5) },
        )

        private fun buttonIconStateCases(): List<Named<*>> = listOf(
            button("icon", testIcon, { it.icon }, SwingModifier.icon(testIcon)) { icon(testIcon) },
            button("pressedIcon", testIcon, { it.pressedIcon }, SwingModifier.pressedIcon(testIcon)) {
                pressedIcon(testIcon)
            },
            button("selectedIcon", testIcon, { it.selectedIcon }, SwingModifier.selectedIcon(testIcon)) {
                selectedIcon(testIcon)
            },
            button("disabledIcon", testIcon, { it.disabledIcon }, SwingModifier.disabledIcon(testIcon)) {
                disabledIcon(testIcon)
            },
            button(
                "disabledSelectedIcon",
                testIcon,
                { it.disabledSelectedIcon },
                SwingModifier.disabledSelectedIcon(testIcon),
            ) { disabledSelectedIcon(testIcon) },
            button("rolloverIcon", testIcon, { it.rolloverIcon }, SwingModifier.rolloverIcon(testIcon)) {
                rolloverIcon(testIcon)
            },
            button(
                "rolloverSelectedIcon",
                testIcon,
                { it.rolloverSelectedIcon },
                SwingModifier.rolloverSelectedIcon(testIcon),
            ) { rolloverSelectedIcon(testIcon) },
        )

        private fun alternateValues(): List<Named<*>> = listOf(
            label(
                "verticalTextPosition bottom",
                BOTTOM,
                { it.verticalTextPosition },
                SwingModifier.verticalTextPosition(BOTTOM),
            ) { verticalTextPosition(BOTTOM) },
            label("iconTextGap 12", 12, { it.iconTextGap }, SwingModifier.iconTextGap(12)) { iconTextGap(12) },
            label("mnemonic lowercase a", KeyEvent.VK_A, { it.displayedMnemonic }, SwingModifier.mnemonic('a')) {
                mnemonic('a')
            },
            button(
                "horizontalAlignment left",
                LEFT,
                { it.horizontalAlignment },
                SwingModifier.horizontalAlignment(LEFT),
            ) { horizontalAlignment(LEFT) },
            button(
                "verticalTextPosition bottom",
                BOTTOM,
                { it.verticalTextPosition },
                SwingModifier.verticalTextPosition(BOTTOM),
            ) { verticalTextPosition(BOTTOM) },
            button("iconTextGap 12", 12, { it.iconTextGap }, SwingModifier.iconTextGap(12)) { iconTextGap(12) },
            button("mnemonic lowercase a", KeyEvent.VK_A, { it.mnemonic }, SwingModifier.mnemonic('a')) {
                mnemonic('a')
            },
            button("margin 9", Insets(9, 9, 9, 9), { it.margin }, SwingModifier.margin(Insets(9, 9, 9, 9))) {
                margin(Insets(9, 9, 9, 9))
            },
            button("rollover disabled", false, { it.isRolloverEnabled }, SwingModifier.rolloverEnabled(false)) {
                rolloverEnabled(false)
            },
            button("contentAreaFilled opaque", false, { it.isOpaque }, SwingModifier.contentAreaFilled(false)) {
                contentAreaFilled(false)
            },
        )

        private fun buttonPaintingCases(): List<Named<*>> = listOf(
            button("actionCommand", "save", { it.model.actionCommand }, SwingModifier.actionCommand("save")) {
                actionCommand("save")
            },
            button("borderPainted", false, { it.isBorderPainted }, SwingModifier.borderPainted(false)) {
                borderPainted(false)
            },
            button("contentAreaFilled", false, { it.isContentAreaFilled }, SwingModifier.contentAreaFilled(false)) {
                contentAreaFilled(false)
            },
            button("focusPainted", false, { it.isFocusPainted }, SwingModifier.focusPainted(false)) {
                focusPainted(false)
            },
            button("rolloverEnabled", true, { it.isRolloverEnabled }, SwingModifier.rolloverEnabled(true)) {
                rolloverEnabled(true)
            },
            button("margin", Insets(1, 2, 3, 4), { it.margin }, SwingModifier.margin(Insets(1, 2, 3, 4))) {
                margin(Insets(1, 2, 3, 4))
            },
        )
    }
}
