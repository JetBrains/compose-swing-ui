// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier

import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.displayedMnemonicIndex
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.DirectWrite
import org.jetbrains.compose.swing.modifier.Restoration
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.Target
import org.jetbrains.compose.swing.modifier.assertAPropertyListenerThatThrowsIsReportedAndALaterChangeStillApplies
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestButtonKind
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.JTextField
import javax.swing.text.DefaultCaret
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A declaration that writes its component during the apply - a modifier's own write, a property declaration
 * or a style - reports the throw of a property-change listener on the written bean instead of ending the
 * apply, and the write that withdraws it afterwards still lands.
 */
class DirectWriteListenerTest {
    companion object {
        @JvmStatic
        fun directWrites(): List<Arguments> = listOf(
            caretWrite(),
            mnemonicWrite(),
            defaultButtonWrite(),
        ).map { Arguments.of(it) }

        private fun mnemonicWrite() = DirectWrite(
            "displayedMnemonicIndex",
            failingProperty = "displayedMnemonicIndex",
            mount = { Button("Save As", onClick = {}, modifier = it) },
            writes =
                listOf(5, 3, 1).map { index ->
                    SwingModifier.styleable(TestButtonKind) { displayedMnemonicIndex(index) } to
                        { component: JComponent -> assertEquals(index, (component as JButton).displayedMnemonicIndex) }
                },
            restoration =
                Restoration(
                    snapshot = { },
                    assertRestored = {
                        component,
                        _,
                        ->
                        assertEquals(-1, (component as JButton).displayedMnemonicIndex)
                    },
                ),
        )

        private fun caretWrite() = DirectWrite(
            "caret",
            failingProperty = "caret",
            mount = { TextField(value = "", onValueChange = {}, modifier = it) },
            writes =
                List(3) { DefaultCaret() }.map { installed ->
                    SwingModifier.styleable(TestTextNodeKind) { caret(installed) } to
                        { component: JComponent -> assertSame(installed, (component as JTextField).caret) }
                },
            restoration =
                Restoration(
                    snapshot = { (it as JTextField).caret },
                    assertRestored = { component, original -> assertSame(original, (component as JTextField).caret) },
                ),
        )

        /** The button sits under a root pane, which is the bean whose `defaultButton` listener throws. */
        private fun defaultButtonWrite() = DirectWrite(
            "defaultButton",
            failingProperty = "defaultButton",
            mount = { modifier ->
                SwingNode(factory = { JRootPane() }) {
                    Button("OK", onClick = {}, modifier = modifier)
                }
            },
            writes =
                listOf(
                    SwingModifier.defaultButton() to { assertSame(it, it.rootPane.defaultButton) },
                    SwingModifier.defaultButton(default = false) to { assertNull(it.rootPane.defaultButton) },
                    SwingModifier.defaultButton() to { assertSame(it, it.rootPane.defaultButton) },
                ),
            restoration =
                Restoration(
                    snapshot = { },
                    assertRestored = { component, _ -> assertNull(component.rootPane.defaultButton) },
                ),
            target =
                Target(
                    find = { onNodeOfType<JButton>().fetch() },
                    listenerOn = { it.rootPane },
                ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directWrites")
    fun aPropertyListenerThatThrowsDuringTheWriteIsReportedAndALaterChangeStillApplies(case: DirectWrite<*>) =
        runComposeSwingTest { assertAPropertyListenerThatThrowsIsReportedAndALaterChangeStillApplies(case) }
}
