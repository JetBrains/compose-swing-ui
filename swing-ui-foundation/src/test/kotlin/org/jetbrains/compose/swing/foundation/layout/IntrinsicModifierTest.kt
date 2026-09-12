package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Intrinsic size modifiers select one of their child's explicit min/max intrinsic hooks. Preferred
 * variants are constrained by the parent; required variants keep the selected raw extent, which the
 * parent reports and centers as overflow.
 */
class IntrinsicModifierTest {
    @Test
    fun intrinsicWidthAndHeightReplaceBothSameAxisIntrinsicAnswers() {
        val minimumWidth = measurableWith(inScope { SwingModifier.width(IntrinsicSize.Min) })
        val maximumHeight = measurableWith(inScope { SwingModifier.height(IntrinsicSize.Max) })

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
    fun unmodifiedMeasurableAnswersIntrinsicsAndBaseline() {
        val unmodified = measurableWith(SwingModifier)
        assertEquals(MINIMUM_WIDTH, unmodified.minIntrinsicWidth(Int.MAX_VALUE))
        assertEquals(PREFERRED_WIDTH, unmodified.maxIntrinsicWidth(Int.MAX_VALUE))
        assertEquals(MINIMUM_HEIGHT, unmodified.minIntrinsicHeight(Int.MAX_VALUE))
        assertEquals(PREFERRED_HEIGHT, unmodified.maxIntrinsicHeight(Int.MAX_VALUE))
        assertEquals(-1, unmodified.baseline(PREFERRED_WIDTH, PREFERRED_HEIGHT))
    }

    @Test
    fun layoutModifierChainDelegatesIntrinsicsAndBaseline() {
        val padded = measurableWith(inScope { SwingModifier.padding(4) })
        assertEquals(MINIMUM_WIDTH + 8, padded.minIntrinsicWidth(Int.MAX_VALUE))
        assertEquals(PREFERRED_WIDTH + 8, padded.maxIntrinsicWidth(Int.MAX_VALUE))
        assertEquals(MINIMUM_HEIGHT + 8, padded.minIntrinsicHeight(Int.MAX_VALUE))
        assertEquals(PREFERRED_HEIGHT + 8, padded.maxIntrinsicHeight(Int.MAX_VALUE))
        assertEquals(-1, padded.baseline(PREFERRED_WIDTH, PREFERRED_HEIGHT))
    }

    @Test
    fun preferredIntrinsicExtentsRespectTheOfferWhileRequiredOnesOverflowIt() {
        val offer = Constraints(maxWidth = OFFER_WIDTH, maxHeight = OFFER_HEIGHT)

        assertEquals(
            Rectangle(0, 0, OFFER_WIDTH, OFFER_HEIGHT),
            boundsAt(inScope { SwingModifier.width(IntrinsicSize.Max) }, offer),
            "a preferred intrinsic width must be held inside the incoming constraints",
        )
        assertEquals(
            Rectangle(0, 0, OFFER_WIDTH, OFFER_HEIGHT),
            boundsAt(inScope { SwingModifier.height(IntrinsicSize.Max) }, offer),
            "and a preferred intrinsic height must be held there too",
        )
        assertEquals(
            Rectangle(
                (OFFER_WIDTH - PREFERRED_WIDTH) / 2,
                (OFFER_HEIGHT - PREFERRED_HEIGHT) / 2,
                PREFERRED_WIDTH,
                PREFERRED_HEIGHT,
            ),
            boundsAt(inScope { SwingModifier.requiredWidth(IntrinsicSize.Max) }, offer),
            "a required intrinsic width must retain the child's raw size and centered overflow",
        )
        assertEquals(
            Rectangle(
                (OFFER_WIDTH - PREFERRED_WIDTH) / 2,
                (OFFER_HEIGHT - PREFERRED_HEIGHT) / 2,
                PREFERRED_WIDTH,
                PREFERRED_HEIGHT,
            ),
            boundsAt(inScope { SwingModifier.requiredHeight(IntrinsicSize.Max) }, offer),
            "and a required intrinsic height must retain the same raw size and overflow behavior",
        )
    }

    private fun measurableWith(modifier: SwingModifier): Measurable {
        val layout = TestPolicyLayout(MeasurePolicy { _, _ -> error("an intrinsic question must not measure") })
        val child = intrinsicChild()
        JPanel(layout).add(child)
        layout.declareLayoutChain(child, layoutChain(modifier))
        return layout.measurables.of(child)
    }

    private fun boundsAt(
        modifier: SwingModifier,
        offer: Constraints,
    ): Rectangle {
        val layout =
            TestPolicyLayout { measurables, _ ->
                val placeable = measurables.single().measure(offer)
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
        val child = intrinsicChild()
        val panel = JPanel(layout)
        panel.add(child)
        layout.declareLayoutChain(child, layoutChain(modifier))
        panel.setSize(PANEL_SIZE, PANEL_SIZE)
        panel.doLayout()
        return child.bounds
    }

    private fun intrinsicChild(): FixedSizeChild =
        FixedSizeChild(PREFERRED_WIDTH, PREFERRED_HEIGHT).also {
            it.minimumSize = Dimension(MINIMUM_WIDTH, MINIMUM_HEIGHT)
        }

    private fun layoutChain(modifier: SwingModifier): List<LayoutModifier> =
        modifier.foldIn(mutableListOf<LayoutModifier>()) { chain, element ->
            chain.also { if (element is LayoutModifier) it.add(element) }
        }

    private fun inScope(declaration: ConstrainedScope.() -> SwingModifier): SwingModifier =
        with(BoxScopeInstance) { declaration() }

    private companion object {
        const val MINIMUM_WIDTH = 20
        const val MINIMUM_HEIGHT = 30
        const val PREFERRED_WIDTH = 100
        const val PREFERRED_HEIGHT = 120
        const val OFFER_WIDTH = 40
        const val OFFER_HEIGHT = 50
        const val PANEL_SIZE = 500
    }
}
