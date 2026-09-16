package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.extension.ExtendWith
import java.awt.Dimension
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Intrinsic size modifiers select one of their child's explicit min/max intrinsic hooks. Preferred
 * variants are constrained by the parent; required variants keep the selected raw extent, which the
 * parent reports and centers as overflow.
 */
@ExtendWith(ComposedPanels::class)
class IntrinsicModifierTest {
    @Test
    fun intrinsicWidthAndHeightReplaceBothSameAxisIntrinsicAnswers() {
        val minimumWidth = measurableWith(layoutChainOf { SwingModifier.width(IntrinsicSize.Min) })
        val maximumHeight = measurableWith(layoutChainOf { SwingModifier.height(IntrinsicSize.Max) })

        assertEquals(
            MINIMUM_WIDTH,
            minimumWidth.minIntrinsicWidth(Int.MAX_VALUE),
            "an intrinsic minimum-width modifier must select the child's minimum width",
        )
        assertEquals(
            MINIMUM_WIDTH,
            minimumWidth.maxIntrinsicWidth(Int.MAX_VALUE),
            "and make both width questions answer that exact selected extent",
        )
        assertEquals(
            PREFERRED_HEIGHT,
            maximumHeight.minIntrinsicHeight(Int.MAX_VALUE),
            "an intrinsic maximum-height modifier must select the child's maximum height",
        )
        assertEquals(
            PREFERRED_HEIGHT,
            maximumHeight.maxIntrinsicHeight(Int.MAX_VALUE),
            "and make both height questions answer that exact selected extent",
        )
    }

    @Test
    fun everyIntrinsicSizeBuilderReportsItsNameAndIntrinsicSize() {
        val declarations =
            listOf(
                with(BoxScopeInstance) { SwingModifier.width(IntrinsicSize.Min) } to "width",
                with(BoxScopeInstance) { SwingModifier.requiredWidth(IntrinsicSize.Max) } to "requiredWidth",
                with(BoxScopeInstance) { SwingModifier.height(IntrinsicSize.Max) } to "height",
                with(BoxScopeInstance) { SwingModifier.requiredHeight(IntrinsicSize.Min) } to "requiredHeight",
            )

        for ((modifier, name) in declarations) {
            assertEquals(name, modifier.lastElement().name, "$name must report its public name")
        }
        assertEquals(
            listOf(IntrinsicSize.Min, IntrinsicSize.Max, IntrinsicSize.Max, IntrinsicSize.Min),
            declarations.map { (modifier) -> modifier.lastElement().declaredValues["intrinsicSize"] },
            "each must report the intrinsic answer it selects",
        )
    }

    @Test
    fun intrinsicWidthAndHeightPassTheCrossAxisQuestionsThrough() {
        val width = measurableWith(layoutChainOf { SwingModifier.width(IntrinsicSize.Max).aspectRatio(1f) })
        val height = measurableWith(layoutChainOf { SwingModifier.height(IntrinsicSize.Max).aspectRatio(1f) })

        assertEquals(200, width.minIntrinsicHeight(200), "an intrinsic width must pass the height questions through")
        assertEquals(200, width.maxIntrinsicHeight(200), "and answer both with what the content answers")
        assertEquals(200, height.minIntrinsicWidth(200), "an intrinsic height must pass the width questions through")
        assertEquals(200, height.maxIntrinsicWidth(200), "and answer both with what the content answers")
    }

    @Test
    fun unmodifiedMeasurableAnswersIntrinsics() {
        val unmodified = measurableWith(emptyList())
        assertEquals(
            MINIMUM_WIDTH,
            unmodified.minIntrinsicWidth(Int.MAX_VALUE),
            "an unmodified measurable must answer its minimum width query with the child's raw minimum width",
        )
        assertEquals(
            PREFERRED_WIDTH,
            unmodified.maxIntrinsicWidth(Int.MAX_VALUE),
            "and its maximum width query with the child's raw preferred width",
        )
        assertEquals(
            MINIMUM_HEIGHT,
            unmodified.minIntrinsicHeight(Int.MAX_VALUE),
            "and its minimum height query with the child's raw minimum height",
        )
        assertEquals(
            PREFERRED_HEIGHT,
            unmodified.maxIntrinsicHeight(Int.MAX_VALUE),
            "and its maximum height query with the child's raw preferred height",
        )
    }

    @Test
    fun layoutModifierChainDelegatesIntrinsics() {
        val padded = measurableWith(layoutChainOf { SwingModifier.padding(4) })
        assertEquals(
            MINIMUM_WIDTH + 8,
            padded.minIntrinsicWidth(Int.MAX_VALUE),
            "a padding modifier must add its horizontal inset to the child's minimum width",
        )
        assertEquals(
            PREFERRED_WIDTH + 8,
            padded.maxIntrinsicWidth(Int.MAX_VALUE),
            "and to its preferred width",
        )
        assertEquals(
            MINIMUM_HEIGHT + 8,
            padded.minIntrinsicHeight(Int.MAX_VALUE),
            "a padding modifier must add its vertical inset to the child's minimum height",
        )
        assertEquals(
            PREFERRED_HEIGHT + 8,
            padded.maxIntrinsicHeight(Int.MAX_VALUE),
            "and to its preferred height",
        )
    }

    @Test
    fun preferredIntrinsicExtentsRespectTheOfferWhileRequiredOnesOverflowIt() {
        val offer = Constraints(maxWidth = 40, maxHeight = 50)

        assertEquals(
            Rectangle(0, 0, 40, 50),
            boundsAt(layoutChainOf { SwingModifier.width(IntrinsicSize.Max) }, offer),
            "a preferred intrinsic width must be held inside the incoming constraints",
        )
        assertEquals(
            Rectangle(0, 0, 40, 50),
            boundsAt(layoutChainOf { SwingModifier.height(IntrinsicSize.Max) }, offer),
            "and a preferred intrinsic height must be held there too",
        )
        assertEquals(
            Rectangle(-30, -35, PREFERRED_WIDTH, PREFERRED_HEIGHT),
            boundsAt(layoutChainOf { SwingModifier.requiredWidth(IntrinsicSize.Max) }, offer),
            "a required intrinsic width must retain the child's raw size and centered overflow",
        )
        assertEquals(
            Rectangle(-30, -35, PREFERRED_WIDTH, PREFERRED_HEIGHT),
            boundsAt(layoutChainOf { SwingModifier.requiredHeight(IntrinsicSize.Max) }, offer),
            "and a required intrinsic height must retain the same raw size and overflow behavior",
        )
    }

    @Test
    fun placingAnIntrinsicStandInPlacesNothing() {
        val policy =
            MeasurePolicy { measurables, constraints ->
                val standIn = measurables.single().intrinsicPlaceable(5, 5)!!
                val placeable = measurables.single().measure(constraints)
                layout(placeable.width, placeable.height) {
                    standIn.place(10, 10)
                    placeable.place(0, 0)
                }
            }
        val layerNode =
            layoutChainOf {
                SwingModifier.layout { measurable, constraints ->
                    val standIn = measurable.intrinsicPlaceable(5, 5)!!
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        standIn.placeWithLayer(10, 10)
                        placeable.place(0, 0)
                    }
                }
            }

        assertEquals(
            Rectangle(0, 0, PANEL_EXTENT, PANEL_EXTENT),
            boundsPlacedBy(policy, emptyList()),
            "a policy placing an unmodified child's stand-in must leave the child where its real placement puts it",
        )
        assertEquals(
            Rectangle(4, 4, PANEL_EXTENT - 8, PANEL_EXTENT - 8),
            boundsPlacedBy(policy, layoutChainOf { SwingModifier.padding(4) }),
            "a policy placing a padded child's stand-in must leave the child where its real placement puts it",
        )
        assertEquals(
            Rectangle(0, 0, PANEL_EXTENT, PANEL_EXTENT),
            boundsAt(layerNode, Constraints.fixed(PANEL_EXTENT, PANEL_EXTENT)),
            "a layout node placing its content's stand-in with a layer must leave the child where its real " +
                "placement puts it",
        )
    }

    @Test
    fun aLayoutNodePlacingItsContentsStandInPlainlyPlacesNothing() =
        runComposeSwingTest {
            var failure: IllegalStateException? = null
            var standInX by mutableIntStateOf(10)
            setContent {
                Box(modifier = containerModifier(PANEL_EXTENT, PANEL_EXTENT)) {
                    Box(
                        modifier =
                            SwingModifier
                                .layout { measurable, constraints ->
                                    val standIn = measurable.intrinsicPlaceable(5, 5)!!
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        try {
                                            standIn.place(standInX, 10)
                                        } catch (e: IllegalStateException) {
                                            failure = e
                                        }
                                        placeable.placeWithLayer(0, 0)
                                    }
                                }.padding(4),
                    ) {}
                }
            }
            awaitIdle()

            standInX = 20
            awaitIdle()

            assertNull(failure, "a layout node placing its content's stand-in with place must place nothing")
        }

    @Test
    fun defaultIntrinsicMeasurableIgnoresALayoutModifiersFixedMeasureConstraints() {
        val fixed =
            measurableWith(
                layoutChainOf {
                    SwingModifier.layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints(30, 30, 30, 30))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                },
            )

        assertEquals(
            PREFERRED_WIDTH,
            fixed.maxIntrinsicWidth(Int.MAX_VALUE),
            "the default intrinsic measurable must answer with the child's raw preferred width, not the fixed 30",
        )
        assertEquals(
            PREFERRED_HEIGHT,
            fixed.maxIntrinsicHeight(Int.MAX_VALUE),
            "and with its raw preferred height too",
        )
    }

    private fun measurableWith(chain: List<LayoutModifierNode>): Measurable {
        val layout =
            MeasurePolicyLayout(
                MeasurePolicy {
                    _,
                    _,
                    ->
                    error("an intrinsic question must not measure")
                },
                null,
            )
        val child = intrinsicChild()
        composed(ConstrainedPanel(layout)).add(child)
        layout.declareLayoutChain(child, chain)
        return layout.measurables.of(child)
    }

    private fun boundsAt(
        chain: List<LayoutModifierNode>,
        offer: Constraints,
    ): Rectangle =
        boundsPlacedBy(
            MeasurePolicy { measurables, _ ->
                val placeable = measurables.single().measure(offer)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            },
            chain,
        )

    /** Where [policy] places the one child, measured through [chain], in a panel [PANEL_EXTENT] square. */
    private fun boundsPlacedBy(
        policy: MeasurePolicy,
        chain: List<LayoutModifierNode>,
    ): Rectangle {
        val layout = MeasurePolicyLayout(policy, null)
        val child = intrinsicChild()
        val panel = composed(ConstrainedPanel(layout))
        panel.add(child)
        layout.declareLayoutChain(child, chain)
        panel.setSize(PANEL_EXTENT, PANEL_EXTENT)
        panel.doLayout()
        return child.bounds
    }

    private fun intrinsicChild(): FixedSizeChild =
        FixedSizeChild(PREFERRED_WIDTH, PREFERRED_HEIGHT).also {
            it.minimumSize = Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT)
        }

    private companion object {
        const val MINIMUM_WIDTH = 20
        const val MINIMUM_HEIGHT = 30
        const val PREFERRED_WIDTH = 100
        const val PREFERRED_HEIGHT = 120
        const val PANEL_EXTENT = 500
    }
}
