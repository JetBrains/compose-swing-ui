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
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.caret
import org.jetbrains.compose.swing.components.style.displayedMnemonicIndex
import org.jetbrains.compose.swing.components.text.TextField
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
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.onNodeOfType
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
    class Restoration<S>(
        /** Reads what the component holds before any declaration writes it. */
        val snapshot: (JComponent) -> S,
        val assertRestored: (JComponent, S) -> Unit,
    )

    class Target(
        /** Finds the mounted component through the harness once it is composed. */
        val find: ComposeSwingTest.() -> JComponent = { onRoot().onChild().fetch<JComponent>() },
        /** The bean whose property listener throws, derived from the mounted component. */
        val listenerOn: (JComponent) -> JComponent = { it },
    )

    class DirectWrite<S>(
        private val label: String,
        val failingProperty: String,
        /** Shows the component, carrying the given modifier. The component is built by the node's own factory. */
        val mount: @Composable (SwingModifier) -> Unit,
        /**
         * The declarations the component carries in turn, each with the check of what it wrote. A site that
         * writes again on an update has three; a site that writes once on install has one.
         */
        val writes: List<Pair<SwingModifier, (JComponent) -> Unit>>,
        val restoration: Restoration<S>,
        val target: Target = Target(),
    ) {
        override fun toString(): String = label
    }

    companion object {
        @JvmStatic
        fun directWrites(): List<Arguments> = listOf(
            DirectWrite(
                "inputVerifier",
                failingProperty = "inputVerifier",
                mount = { TextField(value = "", onValueChange = {}, modifier = it) },
                writes = listOf(SwingModifier.inputVerifier { true } to { assertNotNull(it.inputVerifier) }),
                restoration =
                    Restoration(
                        snapshot = { },
                        assertRestored = { component, _ -> assertNull(component.inputVerifier) },
                    ),
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
            failingProperty = "border",
            mount = { Label("", modifier = it) },
            writes =
                listOf(
                    SwingModifier then BorderElement(BorderSpec.Empty(1, 1, 1, 1)) to
                        { assertEquals(Insets(1, 1, 1, 1), it.insets) },
                    SwingModifier then BorderElement(BorderSpec.Empty(2, 2, 2, 2)) to
                        { assertEquals(Insets(2, 2, 2, 2), it.insets) },
                    SwingModifier then BorderElement(BorderSpec.Empty(3, 3, 3, 3)) to
                        { assertEquals(Insets(3, 3, 3, 3), it.insets) },
                ),
            restoration =
                Restoration(
                    snapshot = { },
                    assertRestored = { component, _ -> assertNull(component.border) },
                ),
        )

        private fun rewriteOnWrite(): DirectWrite<Unit> {
            val name =
                ComponentPropertyDescriptor<JLabel, String?>(
                    name = "name",
                    read = { it.name },
                    write = { label, value -> label.name = value },
                )
            return DirectWrite(
                "a rewriteOn declaration",
                failingProperty = "name",
                mount = { Label("", modifier = it) },
                writes =
                    listOf("declared", "changed", "changed again").map { value ->
                        SwingModifier.property(name, value, rewriteOn = "name") to
                            { component: JComponent -> assertEquals(value, component.name) }
                    },
                restoration =
                    Restoration(
                        snapshot = { },
                        assertRestored = { component, _ -> assertNull(component.name) },
                    ),
            )
        }

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

        private fun toolTipWrite() = DirectWrite(
            "toolTip",
            failingProperty = "ToolTipText",
            mount = { Label("", modifier = it) },
            writes =
                listOf("declared", "changed", "changed again").map { text ->
                    SwingModifier.styleable(TestNodeKind) { toolTip(text) } to
                        { component: JComponent -> assertEquals(text, (component as JLabel).toolTipText) }
                },
            restoration =
                Restoration(
                    snapshot = { },
                    assertRestored = { component, _ -> assertNull((component as JLabel).toolTipText) },
                ),
        )

        private fun declaredBackgroundWrite() = DirectWrite(
            "a declared property",
            failingProperty = "background",
            mount = { Label("", modifier = it) },
            writes =
                listOf(Color.RED, Color.BLUE, Color.GREEN).map { color ->
                    SwingModifier.property(BackgroundProperty, color) to
                        { component: JComponent -> assertEquals(color, component.background) }
                },
            restoration =
                Restoration(
                    snapshot = { it.background to it.isBackgroundSet },
                    assertRestored = { component, (background, wasSet) ->
                        assertEquals(background, component.background)
                        assertEquals(wasSet, component.isBackgroundSet)
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

        private fun transferWrite(
            label: String,
            declared: SwingModifier,
        ) = DirectWrite(
            "transferHandler, $label",
            failingProperty = "transferHandler",
            mount = { TextField(value = "", onValueChange = {}, modifier = it) },
            writes = listOf(declared to { assertIs<SharedTransferHandler>(it.transferHandler) }),
            restoration =
                Restoration(
                    snapshot = { it.transferHandler },
                    assertRestored = { component, original -> assertSame(original, component.transferHandler) },
                ),
        )

        /** Every one of the three writes lands although the listener on [failingProperty] throws. */
        private fun focusTraversalWrite(failingProperty: String) = DirectWrite(
            "orderedFocusTraversal, $failingProperty",
            failingProperty = failingProperty,
            mount = { Panel(modifier = it) {} },
            writes =
                listOf(
                    SwingModifier.orderedFocusTraversal() to {
                        assertTrue((it as JPanel).isFocusCycleRoot)
                        assertTrue(it.isFocusTraversalPolicyProvider)
                        assertTrue(it.isFocusTraversalPolicySet)
                    },
                ),
            restoration =
                Restoration(
                    snapshot = { },
                    assertRestored = { component, _ ->
                        assertEquals(false, (component as JPanel).isFocusCycleRoot)
                        assertEquals(false, component.isFocusTraversalPolicyProvider)
                        assertEquals(false, component.isFocusTraversalPolicySet)
                    },
                ),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directWrites")
    fun aPropertyListenerThatThrowsDuringTheWriteIsReportedAndALaterChangeStillApplies(case: DirectWrite<*>) =
        runComposeSwingTest { aPropertyListenerThatThrowsIsReported(case) }

    private suspend fun <S> ComposeSwingTest.aPropertyListenerThatThrowsIsReported(case: DirectWrite<S>) {
        val failure = IllegalStateException()
        val listener = mockk<PropertyChangeListener>(relaxed = true)
        var declaration by mutableStateOf<SwingModifier>(SwingModifier)
        setContent { case.mount(declaration) }
        awaitIdle()
        val component = case.target.find(this)
        val original = case.restoration.snapshot(component)
        case.target.listenerOn(component).addPropertyChangeListener(case.failingProperty, listener)

        every { listener.propertyChange(any()) } throws failure
        val (declared, assertDeclared) = case.writes.first()
        declaration = declared
        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
        verify(exactly = 1) { listener.propertyChange(any()) }
        assertDeclared(component)

        case.writes.getOrNull(1)?.let { (changed, assertChanged) ->
            declaration = changed
            assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
            verify(exactly = 2) { listener.propertyChange(any()) }
            assertChanged(component)
        }

        every { listener.propertyChange(any()) } just Runs
        case.writes.getOrNull(2)?.let { (changedAgain, assertChangedAgain) ->
            declaration = changedAgain
            awaitIdle()
            assertChangedAgain(component)
        }

        declaration = SwingModifier
        awaitIdle()
        case.restoration.assertRestored(component, original)
    }
}
