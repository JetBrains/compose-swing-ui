// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.datatransfer.SharedTransferHandler
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import org.jetbrains.compose.swing.modifier.datatransfer.onExportDone
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.modifier.interaction.orderedFocusTraversal
import org.jetbrains.compose.swing.style.BorderElement
import org.jetbrains.compose.swing.style.BorderSpec
import org.jetbrains.compose.swing.style.TestNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.style.toolTip
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.TransferHandler
import kotlin.test.assertEquals
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
            toolTipWrite(),
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
        runComposeSwingTest { assertAPropertyListenerThatThrowsIsReportedAndALaterChangeStillApplies(case) }
}
