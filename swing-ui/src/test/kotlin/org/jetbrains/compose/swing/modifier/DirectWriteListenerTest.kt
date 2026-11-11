// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.displayedMnemonicIndex
import org.jetbrains.compose.swing.modifier.datatransfer.SharedTransferHandler
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import org.jetbrains.compose.swing.modifier.datatransfer.onExportDone
import org.jetbrains.compose.swing.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.BorderElement
import org.jetbrains.compose.swing.style.BorderSpec
import org.jetbrains.compose.swing.style.TestButtonKind
import org.jetbrains.compose.swing.style.TestNodeKind
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Insets
import java.beans.PropertyChangeListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.JTextField
import javax.swing.TransferHandler
import javax.swing.text.DefaultCaret
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A declaration that writes its component during the apply - a modifier's own write, a property declaration
 * or a style - reports the throw of a property-change listener on the written bean instead of ending the
 * apply, and the write that withdraws it afterwards still lands.
 */
class DirectWriteListenerTest {
    open class DirectWrite(
        private val label: String,
        val component: JComponent,
        val failingProperty: String,
        /**
         * The declarations the component carries in turn, each with the check of what it wrote. A site that
         * writes again on an update has three; a site that writes once on install has one.
         */
        val writes: List<Pair<SwingModifier, (JComponent) -> Unit>>,
        val assertRestored: (JComponent) -> Unit,
    ) {
        /** The bean whose [failingProperty] listener throws. */
        open val listenerOn: JComponent get() = component

        /** Shows the component, carrying the given modifier. */
        open val mount: @Composable (SwingModifier) -> Unit = { modifier ->
            SwingNode(factory = { component }, modifier = modifier)
        }

        override fun toString(): String = label
    }

    companion object {
        @JvmStatic
        fun directWrites(): List<Arguments> = listOf(
            DirectWrite(
                "inputVerifier",
                JTextField(),
                "inputVerifier",
                listOf(SwingModifier.inputVerifier { true } to { assertNotNull(it.inputVerifier) }),
                { assertNull(it.inputVerifier) },
            ),
            borderWrite(),
            focusTraversalWrite("focusCycleRoot"),
            focusTraversalWrite("focusTraversalPolicyProvider"),
            focusTraversalWrite("focusTraversalPolicy"),
            declaredBackgroundWrite(),
            rewriteOnWrite(),
            caretWrite(),
            mnemonicWrite(),
            toolTipWrite(),
            defaultButtonWrite(),
            transferWrite("draggable", SwingModifier.draggable(TransferHandler.COPY) { null }),
            transferWrite("dropTarget", SwingModifier.dropTarget(TransferHandler.COPY, onDrop = { true })),
            transferWrite("clipboard", SwingModifier.clipboard(transferable = { null }, onPaste = { true })),
            transferWrite("onExportDone", SwingModifier.onExportDone { _, _ -> }),
        ).map { Arguments.of(it) }

        private fun borderWrite() = DirectWrite(
            "border",
            JLabel(),
            "border",
            listOf(
                SwingModifier then BorderElement(BorderSpec.Empty(1, 1, 1, 1)) to
                    { assertEquals(Insets(1, 1, 1, 1), it.insets) },
                SwingModifier then BorderElement(BorderSpec.Empty(2, 2, 2, 2)) to
                    { assertEquals(Insets(2, 2, 2, 2), it.insets) },
                SwingModifier then BorderElement(BorderSpec.Empty(3, 3, 3, 3)) to
                    { assertEquals(Insets(3, 3, 3, 3), it.insets) },
            ),
            { assertNull(it.border) },
        )

        private fun rewriteOnWrite(): DirectWrite {
            val name =
                ComponentPropertyDescriptor<JLabel, String?>(
                    name = "name",
                    read = { it.name },
                    write = { label, value -> label.name = value },
                )
            return DirectWrite(
                "a rewriteOn declaration",
                JLabel(),
                "name",
                listOf("declared", "changed", "changed again").map { value ->
                    SwingModifier.property(name, value, rewriteOn = "name") to
                        { component: JComponent -> assertEquals(value, component.name) }
                },
                { assertNull(it.name) },
            )
        }

        private fun mnemonicWrite() = DirectWrite(
            "displayedMnemonicIndex",
            JButton("Save As"),
            "displayedMnemonicIndex",
            listOf(5, 3, 1).map { index ->
                SwingModifier.styleable(TestButtonKind) { displayedMnemonicIndex(index) } to
                    { component: JComponent -> assertEquals(index, (component as JButton).displayedMnemonicIndex) }
            },
            { assertEquals(-1, (it as JButton).displayedMnemonicIndex) },
        )

        private fun toolTipWrite() = DirectWrite(
            "toolTip",
            JLabel(),
            "ToolTipText",
            listOf("declared", "changed", "changed again").map { text ->
                SwingModifier.styleable(TestNodeKind) { toolTip(text) } to
                    { component: JComponent -> assertEquals(text, (component as JLabel).toolTipText) }
            },
            { assertNull((it as JLabel).toolTipText) },
        )

        private fun declaredBackgroundWrite(): DirectWrite {
            val label = JLabel()
            val original = label.background
            val wasSet = label.isBackgroundSet
            return DirectWrite(
                "a declared property",
                label,
                "background",
                listOf(Color.RED, Color.BLUE, Color.GREEN).map { color ->
                    SwingModifier.property(BackgroundProperty, color) to
                        { component: JComponent -> assertEquals(color, component.background) }
                },
                {
                    assertEquals(original, it.background)
                    assertEquals(wasSet, it.isBackgroundSet)
                },
            )
        }

        private fun caretWrite(): DirectWrite {
            val field = JTextField()
            val original = field.caret
            return DirectWrite(
                "caret",
                field,
                "caret",
                List(3) { DefaultCaret() }.map { installed ->
                    SwingModifier.styleable(TestTextNodeKind) { caret(installed) } to
                        { component: JComponent -> assertSame(installed, (component as JTextField).caret) }
                },
                { assertSame(original, (it as JTextField).caret) },
            )
        }

        /** The button sits under a root pane, which is the bean whose `defaultButton` listener throws. */
        private fun defaultButtonWrite(): DirectWrite {
            val rootPane = JRootPane()
            val button = JButton("OK")
            return object : DirectWrite(
                "defaultButton",
                button,
                "defaultButton",
                listOf(
                    SwingModifier.defaultButton() to { assertSame(button, rootPane.defaultButton) },
                    SwingModifier.defaultButton(default = false) to { assertNull(rootPane.defaultButton) },
                    SwingModifier.defaultButton() to { assertSame(button, rootPane.defaultButton) },
                ),
                { assertNull(rootPane.defaultButton) },
            ) {
                override val listenerOn: JComponent get() = rootPane

                override val mount: @Composable (SwingModifier) -> Unit = { modifier ->
                    SwingNode(factory = { rootPane }) {
                        SwingNode(factory = { button }, modifier = modifier)
                    }
                }
            }
        }

        private fun transferWrite(
            label: String,
            declared: SwingModifier,
        ): DirectWrite {
            val field = JTextField()
            val original = field.transferHandler
            return DirectWrite(
                "transferHandler, $label",
                field,
                "transferHandler",
                listOf(declared to { assertIs<SharedTransferHandler>(it.transferHandler) }),
                { assertSame(original, it.transferHandler) },
            )
        }

        /** Every one of the three writes lands although the listener on [failingProperty] throws. */
        private fun focusTraversalWrite(failingProperty: String) = DirectWrite(
            "orderedFocusTraversal, $failingProperty",
            JPanel(),
            failingProperty,
            listOf(
                SwingModifier.orderedFocusTraversal() to {
                    assertTrue((it as JPanel).isFocusCycleRoot)
                    assertTrue(it.isFocusTraversalPolicyProvider)
                    assertTrue(it.isFocusTraversalPolicySet)
                },
            ),
            {
                assertEquals(false, (it as JPanel).isFocusCycleRoot)
                assertEquals(false, it.isFocusTraversalPolicyProvider)
                assertEquals(false, it.isFocusTraversalPolicySet)
            },
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directWrites")
    fun aPropertyListenerThatThrowsDuringTheWriteIsReportedAndALaterChangeStillApplies(case: DirectWrite) =
        runComposeSwingTest {
            val failure = IllegalStateException()
            val listener = mockk<PropertyChangeListener>(relaxed = true)
            case.listenerOn.addPropertyChangeListener(case.failingProperty, listener)
            var declaration by mutableStateOf<SwingModifier>(SwingModifier)
            setContent { case.mount(declaration) }
            awaitIdle()

            every { listener.propertyChange(any()) } throws failure
            val (declared, assertDeclared) = case.writes.first()
            declaration = declared
            assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
            verify(exactly = 1) { listener.propertyChange(any()) }
            assertDeclared(case.component)

            case.writes.getOrNull(1)?.let { (changed, assertChanged) ->
                declaration = changed
                assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
                verify(exactly = 2) { listener.propertyChange(any()) }
                assertChanged(case.component)
            }

            every { listener.propertyChange(any()) } just Runs
            case.writes.getOrNull(2)?.let { (changedAgain, assertChangedAgain) ->
                declaration = changedAgain
                awaitIdle()
                assertChangedAgain(case.component)
            }

            declaration = SwingModifier
            awaitIdle()
            case.assertRestored(case.component)
        }
}
