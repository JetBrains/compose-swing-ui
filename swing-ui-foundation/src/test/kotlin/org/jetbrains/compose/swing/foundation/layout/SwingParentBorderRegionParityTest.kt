// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.FlowView
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.BlockView
import javax.swing.text.html.ParagraphView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wrapped content in a BorderLayout region, compared with the same content as raw Swing.
 */
class SwingParentBorderRegionParityTest {
    /**
     * Pins the Swing height contract and its accepted validation cost. A wrapping component performs an unbounded
     * intrinsic height read at the width it holds. Under BorderLayout EAST, each granted width therefore changes the
     * Column's preferred size and can request another validation at paint time, including for an HTML JLabel.
     * Completing
     * the HTML in the layout pass keeps its row cost no higher than the same-pass raw Swing control.
     * AndroidX reads its interop View at an UNSPECIFIED width, so wrapping text measures on one line. Aligning that
     * intrinsic read with AndroidX turns this test red: the Column would keep twice one line's height as its width and
     * paint would request no validation. Preserve the Swing contract and the pinned cost when changing this path.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("eastLeaves")
    fun wrappingContentUnderBorderLayoutEastFollowsNativeWidths(kind: String) {
        val leaf = EastLeaves.getValue(kind)
        val plain = eastCycles(leaf, inColumn = false).drop(1)
        val column = eastCycles(leaf, inColumn = true).drop(1)
        assertTrue(plain.all { it.paintRequestsLayout }, "$kind: each raw paint requests layout")
        assertTrue(
            plain.zipWithNext().all { (before, after) -> before.width != after.width },
            "$kind: raw widths change",
        )
        assertEquals(plain.map { it.width }, column.map { it.width }, "$kind: same widths as raw Swing")
        assertEquals(
            plain.map { it.paintRequestsLayout },
            column.map { it.paintRequestsLayout },
            "$kind: same paint requests",
        )
    }

    @Test
    fun htmlUnderBorderLayoutEastPreservesNativePaintRequestsAndValidationCost() {
        val lazy = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = false)
        val completed = eastCycles(::RowCountingHtmlLabel, inColumn = false, samePass = true)
        val column = eastCycles(::RowCountingHtmlLabel, inColumn = true, samePass = true)
        assertEquals(completed.map { it.width }, lazy.map { it.width }, "lazy raw widths match native completion")
        assertEquals(completed.map { it.width }, column.map { it.width }, "Column widths match native completion")
        assertEquals(
            List(PrescribedWidths.lastIndex) { false },
            column.drop(1).map { it.paintRequestsLayout },
            "Column paints do not reconcile size preferences",
        )
        assertEquals(
            List(PrescribedWidths.lastIndex) { true },
            lazy.drop(1).map { it.paintRequestsLayout },
            "lazy raw paint requests",
        )
        assertEquals(
            List(PrescribedWidths.lastIndex) { false },
            completed.drop(1).map { it.paintRequestsLayout },
            "same-pass raw paint requests",
        )
        for ((actual, control) in column.zip(lazy)) {
            assertTrue(actual.validations <= control.validations, "validations: $actual vs lazy $control")
        }
        assertEquals(5, lazy.last().validations, "lazy raw: validation entries")
        assertTrue(lazy.last().rows < completed.last().rows, "lazy raw leaves the final width pending")
        for ((actual, control) in column.zip(completed)) {
            assertTrue(actual.rows <= control.rows, "rows at ${control.width}: $actual vs native $control")
            assertEquals(control.markupHeight, actual.markupHeight, "native completion at ${control.width}")
        }
        assertEquals(
            completed.last().rows,
            column.last().rows,
            "Column completes the native rows at the final width",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("htmlRegions")
    internal fun htmlCompletionAtPrescribedWidthsCostsNoMoreRowsThanNative(
        region: String,
        completesNativeRows: Boolean,
        nativeAtWidths: (SwingParentBorderRegionParityTest, List<Int>) -> List<MarkupCycle>,
        columnAtWidths: (SwingParentBorderRegionParityTest, List<Int>) -> List<MarkupCycle>,
    ) {
        val native = nativeAtWidths(this, PrescribedWidths)
        val column = columnAtWidths(this, PrescribedWidths)
        assertEquals(PrescribedWidths, native.map { it.width }, "native $region grants the prescribed widths")
        assertEquals(PrescribedWidths, column.map { it.width }, "Column $region grants the prescribed widths")
        var columnRows = 0
        var nativeRows = 0
        for ((actual, control) in column.zip(native)) {
            assertTrue(
                actual.rows - columnRows <= control.rows - nativeRows,
                "row cost through paint and resize to ${control.width}: $actual vs native $control",
            )
            assertEquals(control.markupHeight, actual.markupHeight, "native $region completion at ${control.width}")
            columnRows = actual.rows
            nativeRows = control.rows
        }
        if (completesNativeRows) {
            assertEquals(
                native.last().rows,
                column.last().rows,
                "Column completes the native rows at the final width",
            )
        }
    }

    @Test
    fun scriptedPreferredSizeQueriesFollowTheEntirePrescribedWidthHistory() {
        val leaf = {
            object : JComponent() {
                override fun getPreferredSize(): Dimension {
                    val nextWidth =
                        when (width) {
                            0 -> PrescribedWidths[0]
                            PrescribedWidths[0] -> PrescribedWidths[1]
                            PrescribedWidths[1] -> PrescribedWidths[2]
                            PrescribedWidths[2] -> PrescribedWidths[3]
                            PrescribedWidths[3] -> PrescribedWidths[4]
                            else -> error("Unexpected granted width: $width")
                        }
                    return Dimension(1, nextWidth / 2)
                }
            }
        }
        assertEquals(
            PrescribedWidths,
            eastCycles(leaf, inColumn = false).map { it.width },
            "precondition: native EAST grants the scripted widths",
        )
        assertEquals(
            PrescribedWidths,
            eastCycles(leaf, inColumn = true).map { it.width },
            "Column: all granted widths",
        )
    }

    internal data class MarkupCycle(
        val width: Int,
        val paintRequestsLayout: Boolean,
        val validations: Int,
        val rows: Int,
        val markupHeight: Float,
    )

    /** Counts rows completed by Swing's flow strategy without changing its layout. */
    private class RowCountingHtmlLabel : JLabel(WRAPPING_TEXT) {
        var rows = 0
        val markup: View = BasicHTML.createHTMLView(this, "<html>$WRAPPING_TEXT</html>")

        init {
            putClientProperty(BasicHTML.propertyKey, markup)
            val root = markup.getView(0)
            val body = root.getView(1)
            assertEquals("body", body.element.name, "the HTML fixture has one body")
            val factory = markup.viewFactory
            root.replace(
                1,
                1,
                arrayOf(
                    object : BlockView(body.element, View.Y_AXIS) {
                        override fun getViewFactory(): ViewFactory =
                            ViewFactory { element ->
                                if (element.name == "p-implied") {
                                    object : ParagraphView(element) {
                                        init {
                                            strategy =
                                                object : FlowView.FlowStrategy() {
                                                    override fun layoutRow(
                                                        view: FlowView,
                                                        rowIndex: Int,
                                                        position: Int,
                                                    ): Int {
                                                        rows++
                                                        return super.layoutRow(view, rowIndex, position)
                                                    }
                                                }
                                        }
                                    }
                                } else {
                                    factory.create(element)
                                }
                            }
                    },
                ),
            )
            markup.setSize(markup.getPreferredSpan(View.X_AXIS), markup.getPreferredSpan(View.Y_AXIS))
        }
    }

    private fun nativeEastHtmlAtWidths(widths: List<Int>): List<MarkupCycle> =
        eastHtmlAtWidths(widths) { modifier, width ->
            SwingNode(factory = {
                JPanel(
                    SwingParentLayout(preferred = { Dimension() }) { parent ->
                        val component = parent.getComponent(0)
                        component.setBounds(0, 0, parent.width, parent.width / 2)
                        component.getBaseline(component.width, component.height)
                    },
                ).apply { add(RowCountingHtmlLabel()) }
            }, modifier = modifier.styleable { preferredSize(width, width / 2) })
        }

    private fun columnEastHtmlAtWidths(widths: List<Int>): List<MarkupCycle> =
        eastHtmlAtWidths(widths) { modifier, width ->
            Column(modifier = modifier.styleable { preferredSize(width, width / 2) }) {
                SwingNode(
                    factory = { RowCountingHtmlLabel() },
                    modifier = SwingModifier.fillMaxWidth().aspectRatio(2f),
                )
            }
        }

    private fun eastHtmlAtWidths(
        widths: List<Int>,
        subject: @Composable (SwingModifier, Int) -> Unit,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            var width by mutableIntStateOf(widths.first())
            setContent {
                Panel(PanelLayout.Border(), style = { preferredSize(1000, 600) }) {
                    subject(
                        SwingModifier.layoutConstraint(BorderLayout.EAST),
                        width,
                    )
                }
            }
            val component = onNodeOfType<RowCountingHtmlLabel>().onParent().fetch<JComponent>()
            val child = onNodeOfType<RowCountingHtmlLabel>().fetch()

            fun record() =
                MarkupCycle(
                    component.width,
                    false,
                    0,
                    child.rows,
                    child.markup.getPreferredSpan(View.Y_AXIS),
                )
            cycles += record()
            for (nextWidth in widths.drop(1)) {
                captureToImage()
                width = nextWidth
                component.revalidate()
                awaitIdle()
                cycles += record()
            }
        }
        return cycles
    }

    /** The native control is a standalone label sized at each width, as an independent oracle. */
    private fun nativeNorthHtmlAtWidths(widths: List<Int>): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            val label = RowCountingHtmlLabel()
            for (width in widths) {
                label.setSize(width, width / 2)
                label.getBaseline(width, width / 2)
                cycles += MarkupCycle(label.width, false, 0, label.rows, label.markup.getPreferredSpan(View.Y_AXIS))
            }
        }
        return cycles
    }

    private fun columnNorthHtmlAtWidths(widths: List<Int>): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            var width by mutableIntStateOf(widths.first())
            setContent {
                borderLayoutSlot(BorderLayout.NORTH)(width) { modifier ->
                    Column(modifier = modifier) {
                        SwingNode(
                            factory = { RowCountingHtmlLabel() },
                            modifier = SwingModifier.fillMaxWidth().aspectRatio(2f),
                        )
                    }
                }
            }
            val label = onNodeOfType<RowCountingHtmlLabel>().fetch()
            for (offered in widths) {
                width = offered
                awaitIdle()
                cycles += MarkupCycle(label.width, false, 0, label.rows, label.markup.getPreferredSpan(View.Y_AXIS))
            }
        }
        return cycles
    }

    private fun eastCycles(
        leaf: () -> JComponent,
        inColumn: Boolean,
        samePass: Boolean = false,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()
        runComposeSwingTest {
            var validations = 0
            setContent {
                SwingNode(
                    factory = {
                        object : JPanel(BorderLayout()) {
                            override fun validateTree() {
                                validations++
                                super.validateTree()
                            }
                        }
                    },
                    modifier = SwingModifier.styleable { preferredSize(320, 600) },
                ) {
                    val modifier = SwingModifier.layoutConstraint(BorderLayout.EAST)
                    if (inColumn) {
                        Column(modifier = modifier) {
                            SwingNode(
                                factory = { leaf() },
                                modifier = SwingModifier.fillMaxWidth().aspectRatio(2f),
                            )
                        }
                    } else {
                        SwingNode(factory = {
                            JPanel(
                                SwingParentLayout(preferred = { parent ->
                                    val height = parent.getComponent(0).preferredSize.height
                                    Dimension(height * 2, height)
                                }) { parent ->
                                    val component = parent.getComponent(0)
                                    component.setBounds(0, 0, parent.width, parent.width / 2)
                                    if (samePass) component.getBaseline(component.width, component.height)
                                },
                            ).apply { add(leaf()) }
                        }, modifier = modifier)
                    }
                }
            }
            val subject = onRoot().onChild().onChild()
            val component = subject.fetch<JComponent>()
            val child: Component =
                if (inColumn) subject.onChild().fetch<JComponent>() else component.getComponent(0)

            cycles += relayoutCycles(component, child) { validations }
        }
        return cycles
    }

    private suspend fun ComposeSwingTest.relayoutCycles(
        component: JComponent,
        child: Component,
        validations: () -> Int,
    ): List<MarkupCycle> {
        val cycles = mutableListOf<MarkupCycle>()

        fun record(layoutRequested: Boolean) =
            MarkupCycle(
                component.width,
                layoutRequested,
                validations(),
                (child as? RowCountingHtmlLabel)?.rows ?: 0,
                (child as? RowCountingHtmlLabel)?.markup?.getPreferredSpan(View.Y_AXIS) ?: 0f,
            )
        cycles += record(false)
        repeat(PrescribedWidths.lastIndex) {
            val layoutRequested =
                withRecordedRepaints { recorder ->
                    captureToImage()
                    recorder.relayouts.any {
                        it === component || it.isAncestorOf(component) ||
                            component.isAncestorOf(it)
                    }
                }
            component.revalidate()
            awaitIdle()
            cycles += record(layoutRequested)
        }
        return cycles
    }

    companion object {
        private val PrescribedWidths = listOf(32, 864, 64, 800, 64)

        private val EastLeaves: Map<String, () -> JComponent> =
            mapOf(
                "JTextArea" to ::wrappingTextArea,
                "JPanel holding a JTextArea" to { JPanel(BorderLayout()).apply { add(wrappingTextArea()) } },
                "JEditorPane showing HTML" to { JEditorPane("text/html", "<html>$WRAPPING_TEXT</html>") },
            )

        private fun wrappingTextArea() =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }

        @JvmStatic
        fun htmlRegions(): List<Arguments> =
            listOf(
                Arguments.of(
                    BorderLayout.EAST,
                    true,
                    SwingParentBorderRegionParityTest::nativeEastHtmlAtWidths,
                    SwingParentBorderRegionParityTest::columnEastHtmlAtWidths,
                ),
                Arguments.of(
                    BorderLayout.NORTH,
                    false,
                    SwingParentBorderRegionParityTest::nativeNorthHtmlAtWidths,
                    SwingParentBorderRegionParityTest::columnNorthHtmlAtWidths,
                ),
            )

        @JvmStatic
        fun eastLeaves(): List<Arguments> = EastLeaves.keys.map { Arguments.of(it) }
    }
}
