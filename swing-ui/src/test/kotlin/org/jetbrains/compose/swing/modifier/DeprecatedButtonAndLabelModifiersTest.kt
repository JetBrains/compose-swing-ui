// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.TestButtonKind
import org.jetbrains.compose.swing.style.styleable
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
import javax.swing.JMenuBar
import javax.swing.JPopupMenu
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
internal class DeprecatedButtonAndLabelModifiersTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun modifierAndStyleApplyAndRestore(cases: List<DeprecatedModifierCase<*>>) {
        cases.forEach { runDeprecatedModifierCase(it) }
    }

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
            buttonIconStateCases() + buttonPaintingCases()

        private fun <V> label(
            name: String,
            values: List<V>,
            read: (JLabel) -> V,
            modifier: (V) -> SwingModifier,
            style: LabelStyleScope.(V) -> Unit,
        ): Named<*> = Named.of(
            "Label $name",
            values.map { value ->
                DeprecatedModifierCase<LabelStyleScope>(
                    "Label $name",
                    value,
                    { read(it as JLabel) },
                    modifier(value),
                    SwingStyle { style(value) },
                ) { hostModifier, hostStyle ->
                    Label("Save As", hostModifier, hostStyle)
                }
            },
        )

        private fun <V> button(
            name: String,
            values: List<V>,
            read: (AbstractButton) -> V,
            modifier: (V) -> SwingModifier,
            style: ButtonStyleScope.(V) -> Unit,
        ): Named<*> = Named.of(
            "Button $name",
            values.map { value ->
                DeprecatedModifierCase<ButtonStyleScope>(
                    "Button $name",
                    value,
                    { read(it as AbstractButton) },
                    modifier(value),
                    SwingStyle { style(value) },
                ) { hostModifier, hostStyle ->
                    Button("Save As", {}, hostModifier, hostStyle)
                }
            },
        )

        private fun labelPlacementCases(): List<Named<*>> = listOf(
            label("icon", listOf(testIcon), { it.icon }, { SwingModifier.icon(it) }) { icon(it) },
            label(
                "horizontalAlignment",
                listOf(RIGHT),
                { it.horizontalAlignment },
                { SwingModifier.horizontalAlignment(it) },
            ) { horizontalAlignment(it) },
            label(
                "verticalAlignment",
                listOf(TOP),
                { it.verticalAlignment },
                { SwingModifier.verticalAlignment(it) },
            ) { verticalAlignment(it) },
            label(
                "horizontalTextPosition",
                listOf(LEFT),
                { it.horizontalTextPosition },
                { SwingModifier.horizontalTextPosition(it) },
            ) { horizontalTextPosition(it) },
            label(
                "verticalTextPosition",
                listOf(TOP, BOTTOM),
                { it.verticalTextPosition },
                { SwingModifier.verticalTextPosition(it) },
            ) { verticalTextPosition(it) },
            label("iconTextGap", listOf(9, 12), { it.iconTextGap }, { SwingModifier.iconTextGap(it) }) {
                iconTextGap(it)
            },
        )

        private fun labelMnemonicCases(): List<Named<*>> = listOf(
            label(
                "mnemonic(keyCode)",
                listOf(KeyEvent.VK_S),
                { it.displayedMnemonic },
                { SwingModifier.mnemonic(it) },
            ) { mnemonic(it) },
            label(
                "mnemonic(char)",
                listOf(KeyEvent.VK_S, KeyEvent.VK_A),
                { it.displayedMnemonic },
                { SwingModifier.mnemonic(if (it == KeyEvent.VK_S) 'S' else 'a') },
            ) { mnemonic(if (it == KeyEvent.VK_S) 'S' else 'a') },
            label(
                "displayedMnemonicIndex",
                listOf(5),
                { it.displayedMnemonicIndex },
                { SwingModifier.displayedMnemonicIndex(it) },
            ) { displayedMnemonicIndex(it) },
        )

        private fun buttonPlacementCases(): List<Named<*>> = listOf(
            button(
                "horizontalAlignment",
                listOf(RIGHT, LEFT),
                { it.horizontalAlignment },
                { SwingModifier.horizontalAlignment(it) },
            ) { horizontalAlignment(it) },
            button(
                "verticalAlignment",
                listOf(TOP),
                { it.verticalAlignment },
                { SwingModifier.verticalAlignment(it) },
            ) { verticalAlignment(it) },
            button(
                "horizontalTextPosition",
                listOf(LEFT),
                { it.horizontalTextPosition },
                { SwingModifier.horizontalTextPosition(it) },
            ) { horizontalTextPosition(it) },
            button(
                "verticalTextPosition",
                listOf(TOP, BOTTOM),
                { it.verticalTextPosition },
                { SwingModifier.verticalTextPosition(it) },
            ) { verticalTextPosition(it) },
            button("iconTextGap", listOf(9, 12), { it.iconTextGap }, { SwingModifier.iconTextGap(it) }) {
                iconTextGap(it)
            },
            button(
                "mnemonic(keyCode)",
                listOf(KeyEvent.VK_S),
                { it.mnemonic },
                { SwingModifier.mnemonic(it) },
            ) { mnemonic(it) },
            button(
                "mnemonic(char)",
                listOf(KeyEvent.VK_S, KeyEvent.VK_A),
                { it.mnemonic },
                { SwingModifier.mnemonic(if (it == KeyEvent.VK_S) 'S' else 'a') },
            ) { mnemonic(if (it == KeyEvent.VK_S) 'S' else 'a') },
            button(
                "displayedMnemonicIndex",
                listOf(5),
                { it.displayedMnemonicIndex },
                { SwingModifier.displayedMnemonicIndex(it) },
            ) { displayedMnemonicIndex(it) },
        )

        private fun buttonIconStateCases(): List<Named<*>> = listOf(
            button("icon", listOf(testIcon), { it.icon }, { SwingModifier.icon(it) }) { icon(it) },
            button("pressedIcon", listOf(testIcon), { it.pressedIcon }, { SwingModifier.pressedIcon(it) }) {
                pressedIcon(it)
            },
            button("selectedIcon", listOf(testIcon), { it.selectedIcon }, { SwingModifier.selectedIcon(it) }) {
                selectedIcon(it)
            },
            button("disabledIcon", listOf(testIcon), { it.disabledIcon }, { SwingModifier.disabledIcon(it) }) {
                disabledIcon(it)
            },
            button(
                "disabledSelectedIcon",
                listOf(testIcon),
                { it.disabledSelectedIcon },
                { SwingModifier.disabledSelectedIcon(it) },
            ) { disabledSelectedIcon(it) },
            button("rolloverIcon", listOf(testIcon), { it.rolloverIcon }, { SwingModifier.rolloverIcon(it) }) {
                rolloverIcon(it)
            },
            button(
                "rolloverSelectedIcon",
                listOf(testIcon),
                { it.rolloverSelectedIcon },
                { SwingModifier.rolloverSelectedIcon(it) },
            ) { rolloverSelectedIcon(it) },
        )

        private fun buttonPaintingCases(): List<Named<*>> = listOf(
            button(
                "actionCommand",
                listOf("save"),
                { it.model.actionCommand },
                { SwingModifier.actionCommand(it) },
            ) { actionCommand(it) },
            button(
                "borderPainted",
                listOf(false),
                { it.isBorderPainted },
                { SwingModifier.borderPainted(it) },
            ) { borderPainted(it) },
        ) + appearancePaintingCases()

        private fun appearancePaintingCases(): List<Named<*>> = listOf(
            Named.of(
                "JMenuBar borderPainted",
                listOf(
                    DeprecatedModifierCase<ButtonStyleScope>(
                        "JMenuBar borderPainted",
                        false,
                        { (it as JMenuBar).isBorderPainted },
                        SwingModifier.borderPainted(false),
                        SwingStyle { borderPainted(false) },
                    ) { hostModifier, hostStyle ->
                        SwingNode(
                            factory = { JMenuBar() },
                            modifier = hostModifier.styleable(TestButtonKind, hostStyle),
                        )
                    },
                ),
            ),
            Named.of(
                "JPopupMenu borderPainted",
                listOf(
                    DeprecatedModifierCase<ButtonStyleScope>(
                        "JPopupMenu borderPainted",
                        false,
                        { (it as JPopupMenu).isBorderPainted },
                        SwingModifier.borderPainted(false),
                        SwingStyle { borderPainted(false) },
                    ) { hostModifier, hostStyle ->
                        SwingNode(
                            factory = { JPopupMenu() },
                            modifier = hostModifier.styleable(TestButtonKind, hostStyle),
                        )
                    },
                ),
            ),
            button(
                "contentAreaFilled",
                listOf(false to false),
                { it.isContentAreaFilled to it.isOpaque },
                { SwingModifier.contentAreaFilled(it.first) },
            ) { contentAreaFilled(it.first) },
            button(
                "focusPainted",
                listOf(false),
                { it.isFocusPainted },
                { SwingModifier.focusPainted(it) },
            ) { focusPainted(it) },
            button(
                "rolloverEnabled",
                listOf(true, false),
                { it.isRolloverEnabled },
                { SwingModifier.rolloverEnabled(it) },
            ) { rolloverEnabled(it) },
            button(
                "margin",
                listOf(Insets(1, 2, 3, 4), Insets(9, 9, 9, 9)),
                { it.margin },
                { SwingModifier.margin(it) },
            ) { margin(it) },
        )
    }
}
