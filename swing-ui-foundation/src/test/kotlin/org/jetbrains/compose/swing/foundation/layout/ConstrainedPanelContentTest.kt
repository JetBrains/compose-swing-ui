package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Component
import java.awt.ComponentOrientation
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A [ConstrainedPanel] built outside a composition hosts Foundation content through its own `setContent`. */
class ConstrainedPanelContentTest {
    @Test
    fun disposingTheContentHandleDisposesTheCompositionAndRemovesItsChildren() {
        for (explicitParent in listOf(false, true)) {
            runComposeSwingTest {
                val panel = ConstrainedPanel()
                lateinit var context: CompositionContext
                var active = 0
                setContent {
                    context = rememberCompositionContext()
                    SwingNode(factory = { panel })
                }
                val content =
                    panel.setContent(parent = if (explicitParent) context else null) {
                        DisposableEffect(Unit) {
                            active++
                            onDispose { active-- }
                        }
                        Label("Content")
                    }
                try {
                    awaitIdle()
                    assertEquals(1, active)
                    assertEquals(1, panel.componentCount)
                    content.dispose()
                    awaitIdle()
                    assertEquals(0, active, "disposing the handle disposes the content composition")
                    assertEquals(0, panel.componentCount, "disposing the handle removes the content's children")
                } finally {
                    content.dispose()
                }
            }
        }
    }

    @Test
    fun aPolicyPlacingAChildWithALayerLaysOutAndPaintsOutsideAnyComposition() =
        runComposeSwingTest {
            val child =
                ConstrainedPanel().apply {
                    isOpaque = true
                    background = Color.RED
                }
            val parent =
                ConstrainedPanel(
                    MeasurePolicy { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                        layout(100, 60) { placeable.placeWithLayer(10, 5) { clip = true } }
                    },
                )
            parent.add(child)
            parent.setBounds(0, 0, 100, 60)

            parent.doLayout()

            assertEquals(Rectangle(10, 5, 40, 20), child.bounds, "the layered child is placed where the policy puts it")
            val image = BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB)
            parent.paint(image.createGraphics())
            assertEquals(Color.RED.rgb, image.getRGB(30, 15), "the layered child paints")
        }

    @Test
    fun aLayerAPolicyPlacesOnAPanelNoNodeHoldsFadesItsChildAndPaintsIt() =
        runComposeSwingTest {
            val child =
                ConstrainedPanel().apply {
                    isOpaque = true
                    background = Color.RED
                }
            val parent =
                ConstrainedPanel(
                    MeasurePolicy { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints.fixed(40, 20))
                        layout(100, 60) { placeable.placeWithLayer(10, 5) { alpha = 0.5f } }
                    },
                ).apply {
                    isOpaque = true
                    background = Color.WHITE
                }
            val host = JPanel(null)
            host.add(parent)
            parent.add(child)
            parent.setBounds(0, 0, 100, 60)

            parent.doLayout()

            assertEquals(Rectangle(10, 5, 40, 20), child.bounds, "the faded child is placed where the policy puts it")
            val image = BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB)
            parent.paint(image.createGraphics())
            assertEquals(
                Color(0xFF, 0x7F, 0x7F).rgb,
                image.getRGB(30, 15),
                "the layer's half alpha blends the red child with the white background",
            )
        }

    @Test
    fun layoutModifiersAtTheTopLevelApplyAgainstThePanelUnderAStockParent() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent { SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } }) }
            panel.setContent {
                Box(
                    modifier =
                        SwingModifier
                            .testTag("child")
                            .padding(10)
                            .width(120)
                            .height(30),
                )
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 140, 50), panel.bounds, "the stock parent grants the panel its preferred size")
            assertEquals(
                Rectangle(10, 10, 120, 30),
                onNodeWithTag("child").fetch().bounds,
                "the padding, width and height declared at the top level size and place the child in the panel",
            )
        }

    @Test
    fun aPanelUnderAFoundationParentIsMeasuredWithThatParentsConstraints() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent {
                Column(modifier = SwingModifier.preferredSize(300, 200)) {
                    SwingNode(factory = { panel })
                }
            }
            panel.setContent {
                Box(modifier = SwingModifier.testTag("filling").fillMaxWidth().height(20))
                Box(modifier = SwingModifier.testTag("fixed").width(120).height(50))
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 300, 50), panel.bounds, "the panel takes the largest of its children")
            assertEquals(
                Rectangle(0, 0, 300, 20),
                onNodeWithTag("filling").fetch().bounds,
                "fillMaxWidth fills the width the Column offers the panel",
            )
            assertEquals(
                Rectangle(0, 0, 120, 50),
                onNodeWithTag("fixed").fetch().bounds,
                "every child stands at the panel's origin",
            )
        }

    @Test
    fun eachChildIsMeasuredUnderThePanelsOwnConstraints() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent { SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } }) }
            panel.setContent {
                Box(modifier = SwingModifier.testTag("wide").size(40, 20))
                Box(modifier = SwingModifier.testTag("tall").size(30, 50))
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 40, 50), panel.bounds, "the panel prefers the largest of its children")
            assertEquals(
                Rectangle(0, 0, 40, 50),
                onNodeWithTag("wide").fetch().bounds,
                "laid out at a fixed size, the panel passes that size to each child, as androidx's root does",
            )
            assertEquals(
                Rectangle(0, 0, 40, 50),
                onNodeWithTag("tall").fetch().bounds,
                "and every child takes that same size",
            )
        }

    @Test
    fun aMeasurePolicyPassedToTheConstructorMeasuresAndPlacesTheContent() =
        runComposeSwingTest {
            val stacking =
                MeasurePolicy { measurables, constraints ->
                    val placeables = measurables.map { it.measure(constraints.copyMaxDimensions()) }
                    layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.sumOf { it.height }) {
                        var y = 0
                        placeables.forEach {
                            it.place(0, y)
                            y += it.height
                        }
                    }
                }
            val panel = ConstrainedPanel(stacking)
            setContent { SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } }) }
            panel.setContent {
                Box(modifier = SwingModifier.testTag("first").size(40, 20))
                Box(modifier = SwingModifier.testTag("second").size(30, 50))
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 40, 70), panel.bounds, "the stock parent grants the size the policy reports")
            assertEquals(
                Rectangle(0, 0, 40, 20),
                onNodeWithTag("first").fetch().bounds,
                "the policy places the first child at the top, at its own size",
            )
            assertEquals(
                Rectangle(0, 20, 30, 50),
                onNodeWithTag("second").fetch().bounds,
                "the policy stacks the children",
            )
        }

    @Test
    fun wrappingContentReportsPreferredAndNaturalMinimumHeightAsWidthsChange() {
        val cases =
            WrappingTexts.keys.flatMap { kind ->
                HeightQueries.keys.flatMap { query ->
                    val divisors = if (query == "preferred height") listOf(1, 2) else listOf(1)
                    divisors.flatMap { divisor ->
                        listOf(false, true).map { WrappingCase(kind, query, divisor, inColumn = it) }
                    }
                }
            }
        for (case in cases) assertGrantedLikeATextArea(case)
    }

    private fun assertGrantedLikeATextArea(case: WrappingCase) {
        val wrapping = WrappingTexts.getValue(case.kind)
        val widths = listOf(320, 160, 400)
        val inSlot = { HeightForWidthLayout({ it / case.askedWidthDivisor }, HeightQueries.getValue(case.query)) }
        val areaGrants = grants(inSlot(), widths, plainTextArea())
        val panel = ConstrainedPanel()
        val panelGrants =
            grants(inSlot(), widths, panel) {
                panel.setContent {
                    val modifier = SwingModifier.testTag("text").fillMaxWidth()
                    if (case.inColumn) Column { wrapping(modifier) } else wrapping(modifier)
                }
            }
        val boxGrants = composedBoxGrants(inSlot(), widths, case)
        for ((step, width) in widths.withIndex()) {
            val at = "$case at width $width"
            val panelGrant = panelGrants[step]
            val areaGrant = areaGrants[step]
            if (case.kind == "TextArea" && case.query != "minimum height" && areaGrant.settled) {
                assertEquals(
                    areaGrant.subject,
                    panelGrant.subject,
                    "$at: the parent grants the panel what it grants the text",
                )
            }
            assertEquals(width, panelGrant.text.width, "$at: the text fills the width")
            assertTrue(panelGrant.settled || !areaGrant.settled, "$at: the panel settles where a text area does")
            assertTrue(
                panelGrant.validations <= boxGrants[step].validations,
                "$at: the hosted panel needs ${panelGrant.validations} validations, a composed Box " +
                    "${boxGrants[step].validations}",
            )
            if (case.query == "minimum height") {
                assertEquals(
                    panelGrant.parentAskedHeight,
                    panelGrant.subject.height,
                    "$at: the panel keeps the minimum height its parent asked and granted",
                )
            } else if (case.askedWidthDivisor == 1) {
                assertEquals(panelGrant.wanted, panelGrant.text.height, "$at: the text takes the height it prefers")
            }
            if (!case.inColumn) assertEquals(panelGrant.subject, panelGrant.text, "$at: no gap or clip")
        }
    }

    @Test
    fun aQueryAtAnotherWidthLeavesASettledPanelAndItsContentAsTheyAre() {
        for ((kind, wrapping) in WrappingTexts) {
            for ((query, ask) in IntrinsicHeightQueries) {
                runComposeSwingTest {
                    val panel = ConstrainedPanel()
                    val parent = JPanel(HeightForWidthLayout { it.preferredSize.height }).apply { add(panel) }
                    hostInGrid(parent, 320, 600)
                    panel.setContent { wrapping(SwingModifier.testTag("text")) }
                    awaitIdle()
                    cyclesUntilStable(parent)
                    val text = onNodeWithTag("text").fetch<JComponent>()
                    val textBounds = text.bounds
                    val panelBounds = panel.bounds

                    withRecordedRepaints { recorder ->
                        val narrow = ask(panel, 160)
                        val wide = ask(panel, 480)
                        assertEquals(
                            panelBounds.height,
                            narrow,
                            "$kind: asked its $query at 160, a settled panel reports the height it holds",
                        )
                        assertEquals(panelBounds.height, wide, "$kind: and at 480")
                        captureToImage()
                        recorder.assertAskedForNoLayout(panel, "$kind: asking the panel its $query at other widths")
                    }

                    assertEquals(textBounds, text.bounds, "$kind: asking resizes the text nowhere")
                    assertEquals(panelBounds, panel.bounds, "$kind: nor the panel")
                    val ancestry = generateSequence<Component>(text) { it.parent }
                    assertTrue(ancestry.all { it.isValid }, "$kind: all stay valid")
                }
            }
        }
    }

    @Test
    fun contentWhoseHeightDoesNotFollowTheWidthAsksNoLayoutAsAHeightForWidthParentChangesThatWidth() {
        for ((query, ask) in HeightQueries) {
            runComposeSwingTest {
                val panel = ConstrainedPanel()
                val parent = JPanel(HeightForWidthLayout(height = ask)).apply { add(panel) }
                val width = hostInGrid(parent, 320, 600)
                panel.setContent { Label("x", modifier = SwingModifier.preferredSize(80, 80)) }
                awaitIdle()
                assertTrue(
                    panel.minIntrinsicHeight(320) < panel.maxIntrinsicHeight(320),
                    "the label prefers a height above its minimum",
                )

                width.intValue = 160
                awaitIdle()

                withRecordedRepaints { recorder ->
                    captureToImage()
                    recorder.assertAskedForNoLayout(panel, "asked its $query, the first paint at the new width")
                }
            }
        }
    }

    @Test
    fun aParentAskingTheHeightAtAnotherWidthThanItGrantsCostsNoMoreValidationsThanForATextArea() {
        val widths = listOf(320, 160, 400, 320)
        val inSlot = { HeightForWidthLayout({ it / 4 }) { it.preferredSize.height } }
        val areaGrants = grants(inSlot(), widths, plainTextArea())
        val panel = ConstrainedPanel()
        val panelGrants =
            grants(inSlot(), widths, panel) {
                panel.setContent { Label("x", modifier = SwingModifier.testTag("text").fillMaxWidth().aspectRatio(2f)) }
            }
        for ((step, width) in widths.withIndex()) {
            assertTrue(
                panelGrants[step].settled || !areaGrants[step].settled,
                "at width $width: the panel settles where a text area does",
            )
            assertTrue(
                panelGrants[step].validations <= areaGrants[step].validations,
                "at width $width: the panel needs ${panelGrants[step].validations} validations, a text area " +
                    "${areaGrants[step].validations}",
            )
            assertEquals(width, panelGrants[step].subject.width, "at width $width: the parent grants the width")
            assertEquals(Dimension(width, width / 8), panelGrants[step].subject, "at width $width: the parent's grant")
            assertEquals(
                Dimension(width, width / 2),
                panelGrants[step].text,
                "at width $width: the aspect ratio can exceed the granted height",
            )
        }
    }

    @Test
    fun contentWhoseHeightFollowsTheWidthWithoutRevalidatingItselfIsGrantedTheHeightItPrefersAtItsWidth() {
        for ((query, ask) in HeightQueries - "minimum height") {
            for (askedWidthDivisor in listOf(1, 2)) {
                val case = "asked its $query at 1/$askedWidthDivisor of the width"
                val widths = listOf(320, 160, 400)
                val inSlot = { HeightForWidthLayout({ it / askedWidthDivisor }, ask) }
                val plainGrants = grants(inSlot(), widths, HalfAsTallAsItHolds(preferredWidth = 640))
                val areaGrants = grants(inSlot(), widths, plainTextArea())
                val panel = ConstrainedPanel()
                val panelGrants =
                    grants(inSlot(), widths, panel) {
                        panel.setContent {
                            SwingNode(
                                factory = { HalfAsTallAsItHolds(preferredWidth = 640) },
                                modifier = SwingModifier.testTag("text").fillMaxWidth(),
                            )
                        }
                    }
                for ((step, width) in widths.withIndex()) {
                    val at = "$case at width $width"
                    assertEquals(
                        plainGrants[step].subject,
                        panelGrants[step].subject,
                        "$at: the parent grants the panel the size it grants the content",
                    )
                    assertEquals(
                        Dimension(width, width / askedWidthDivisor / 2),
                        panelGrants[step].subject,
                        "$at: the parent grants the height preferred at the width it asks",
                    )
                    assertEquals(panelGrants[step].subject, panelGrants[step].text, "$at: the content fills the panel")
                    assertTrue(panelGrants[step].settled, "$at: the panel settles")
                    assertTrue(
                        panelGrants[step].validations <= areaGrants[step].validations,
                        "$at: the panel needs ${panelGrants[step].validations} validations, a text area " +
                            "${areaGrants[step].validations}",
                    )
                }
            }
        }
    }

    @Test
    fun aMinimumHeightReadAtAnEarlierWidthIsNotCheckedByAPaintAtAWidthNobodyAskedAt() =
        runComposeSwingTest {
            var height: (Component) -> Int = { child -> child.preferredSize.height.also { child.minimumSize } }
            val panel = ConstrainedPanel()
            val parent = JPanel(HeightForWidthLayout(height = { height(it) })).apply { add(panel) }
            setContent {
                Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(400, 600)) {
                    SwingNode(factory = { JPanel(null).apply { add(parent) } })
                }
            }
            val content = MinimumFollowsWidth()
            panel.setContent { SwingNode(factory = { content }, modifier = SwingModifier.fillMaxWidth()) }
            parent.setBounds(0, 0, 320, 600)
            awaitIdle()
            captureToImage()
            awaitIdle()
            assertEquals(Dimension(320, 40), panel.size, "the parent lays the panel out at the height it asked")

            height = { child -> child.preferredSize.height }
            parent.setSize(160, 600)
            content.revalidate()
            awaitIdle()
            captureToImage()
            awaitIdle()
            assertEquals(Dimension(160, 40), panel.size, "the parent lays the panel out at the width it asked")

            height = { child -> child.height }
            parent.setSize(400, 600)
            parent.revalidate()
            awaitIdle()
            withRecordedRepaints { recorder ->
                captureToImage()
                recorder.assertAskedForNoLayout(
                    panel,
                    "the paint at a width the parent asked nothing at, where the preferred height is unchanged and " +
                        "the minimum height was last asked at an earlier width,",
                )
            }
            assertEquals(Dimension(400, 40), panel.size, "the parent grants the height the panel has")
        }

    @Test
    fun aPaintAtAnotherWidthDoesNotValidateThePanel() {
        for ((query, ask) in HeightQueries) {
            val askedForLayout =
                listOf(false, true).map { inPanel ->
                    var asked = false
                    runComposeSwingTest {
                        val panel = ConstrainedPanel()
                        val subject: JComponent = if (inPanel) panel else plainTextArea()
                        val parent = JPanel(null).apply { add(subject) }
                        hostInGrid(parent, 400, 600)
                        if (inPanel) {
                            panel.setContent {
                                TextArea(
                                    WRAPPING_TEXT,
                                    {},
                                    SwingModifier.fillMaxWidth(),
                                    lineWrap = true,
                                    wrapStyleWord = true,
                                )
                            }
                        }
                        subject.setBounds(0, 0, 320, 300)
                        awaitIdle()
                        captureToImage()
                        awaitIdle()

                        ask(subject)
                        subject.setSize(160, 300)
                        awaitIdle()

                        withRecordedRepaints { recorder ->
                            captureToImage()
                            asked = subject in recorder.relayouts
                        }
                    }
                    asked
                }
            assertTrue(askedForLayout[0], "asked its $query, a text area painted at another width asks for layout")
            assertFalse(askedForLayout[1], "asked its $query, painting the panel does not request validation")
        }
    }

    @Test
    fun aSizeSetOnThePanelReportsTheIntrinsicFunctionsOfItsKind() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent { SwingNode(factory = { panel }) }
            panel.setContent { Box(modifier = SwingModifier.size(40, 20)) }
            awaitIdle()

            panel.minimumSize = Dimension(10, 77)
            panel.preferredSize = Dimension(333, 88)

            for (extent in listOf(320, Constraints.Infinity)) {
                assertEquals(10, panel.minIntrinsicWidth(extent), "a minimum size reports the min width at $extent")
                assertEquals(77, panel.minIntrinsicHeight(extent), "and the min height at $extent")
                assertEquals(333, panel.maxIntrinsicWidth(extent), "a preferred size reports the max width at $extent")
                assertEquals(88, panel.maxIntrinsicHeight(extent), "and the max height at $extent")
            }
        }

    @Test
    fun aLayoutConstraintTheDefaultPolicyDoesNotReadIsIgnoredAtTheTopLevel() =
        runComposeSwingTest {
            val panel = ConstrainedPanel().apply { preferredSize = Dimension(100, 50) }
            setContent { SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } }) }
            panel.setContent {
                Box(
                    modifier =
                        SwingModifier
                            .testTag("child")
                            .layoutConstraint("trailing")
                            .wrapContentSize(Alignment.TopStart)
                            .size(40, 20),
                )
                Box(modifier = SwingModifier.testTag("control").wrapContentSize(Alignment.TopStart).size(40, 20))
            }
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 40, 20),
                onNodeWithTag("child").fetch().bounds,
                "the default policy places the constrained child at the start",
            )
            assertEquals(Dimension(100, 50), panel.size)
            assertEquals(
                onNodeWithTag("control").fetch().bounds,
                onNodeWithTag("child").fetch().bounds,
                "unread parent data leaves placement unchanged",
            )
        }

    @Test
    fun theDefaultPolicyPlacesChildrenAtTheStartInEitherOrientation() =
        runComposeSwingTest {
            val panel = ConstrainedPanel().apply { preferredSize = Dimension(100, 50) }
            setContent { SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } }) }
            val content =
                panel.setContent {
                    Box(modifier = SwingModifier.testTag("child").wrapContentSize(Alignment.TopStart).size(40, 20))
                }
            try {
                for ((orientation, x) in listOf(
                    ComponentOrientation.LEFT_TO_RIGHT to 0,
                    ComponentOrientation.RIGHT_TO_LEFT to 60,
                )) {
                    panel.applyComponentOrientation(orientation)
                    panel.revalidate()
                    awaitIdle()
                    assertEquals(Rectangle(x, 0, 40, 20), onNodeWithTag("child").fetch().bounds)
                }
            } finally {
                content.dispose()
            }
        }

    @Test
    fun aScopedModifierOfAnotherContainerIsRefusedAtTheTopLevel() =
        runComposeSwingTest {
            var context: CompositionContext? = null
            setContent { context = rememberCompositionContext() }
            val rowWeight = with(RowScopeInstance) { SwingModifier.weight(1f) }

            val failure =
                assertFailsWith<IllegalStateException> {
                    ConstrainedPanel().setContent(parent = checkNotNull(context)) { Label("x", modifier = rowWeight) }
                    awaitIdle()
                }

            assertTrue(
                "linear parent data" in failure.message.orEmpty(),
                "the refusal names the linear declaration under the panel: ${failure.message}",
            )
        }

    @Test
    fun anotherLayoutManagerIsRefused() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()

            val failure = assertFailsWith<IllegalArgumentException> { panel.layout = FlowLayout() }

            assertTrue(
                "pass a MeasurePolicy to its constructor" in failure.message.orEmpty(),
                "the refusal says how to change the layout: ${failure.message}",
            )
            assertTrue(panel.layout !is FlowLayout, "the panel keeps laying out by its policy")
        }

    private companion object {
        /** The height a stock parent works out for its child at the child's width, by Swing's sizes. */
        val HeightQueries: Map<String, (Component) -> Int> =
            mapOf(
                "preferred height" to { child -> child.preferredSize.height },
                "minimum height" to { child -> child.minimumSize.height },
                "preferred then minimum height" to { child -> child.preferredSize.height.also { child.minimumSize } },
            )

        /** The [Constrainable] height functions. */
        val IntrinsicHeightQueries: Map<String, (Constrainable, Int) -> Int> =
            mapOf(
                "max intrinsic height" to Constrainable::maxIntrinsicHeight,
                "min intrinsic height" to Constrainable::minIntrinsicHeight,
            )

        fun plainTextArea() =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }
    }
}

/**
 * Shows [subject] as the one child of a `JPanel` laid out by [layout] in a grid cell as wide as each of [widths] in
 * turn, after [populate] gives the subject its content, and returns what the parent granted at each width once the
 * paints that ask for a validation have settled.
 */
private fun grants(
    layout: HeightForWidthLayout,
    widths: List<Int>,
    subject: JComponent,
    populate: ComposeSwingTest.() -> Unit = {},
): List<Grant> {
    val steps = mutableListOf<Grant>()
    runComposeSwingTest {
        val parent = JPanel(layout).apply { add(subject) }
        val width = hostInGrid(parent, widths.first(), 600)
        populate()
        awaitIdle()
        steps += recordGrants(layout, widths, parent, width, subject)
    }
    return steps
}

private fun composedBoxGrants(
    layout: HeightForWidthLayout,
    widths: List<Int>,
    case: WrappingCase,
): List<Grant> {
    val steps = mutableListOf<Grant>()
    val wrapping = WrappingTexts.getValue(case.kind)
    runComposeSwingTest {
        val parent = JPanel(layout)
        val width = mutableIntStateOf(widths.first())
        setContent {
            Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(width.intValue, 600)) {
                SwingNode(factory = { parent }) {
                    Box(propagateMinConstraints = true) {
                        val modifier = SwingModifier.testTag("text").fillMaxWidth()
                        if (case.inColumn) Column { wrapping(modifier) } else wrapping(modifier)
                    }
                }
            }
        }
        awaitIdle()
        steps += recordGrants(layout, widths, parent, width, parent.getComponent(0) as JComponent)
    }
    return steps
}

private suspend fun ComposeSwingTest.recordGrants(
    layout: HeightForWidthLayout,
    widths: List<Int>,
    parent: JComponent,
    width: MutableIntState,
    subject: JComponent,
): List<Grant> {
    val steps = mutableListOf<Grant>()
    for (granted in widths) {
        val before = layout.layouts
        width.intValue = granted
        awaitIdle()
        repeat(6) { settleWithPaint() }
        val validations = layout.layouts - before
        val settled =
            withRecordedRepaints { recorder ->
                captureToImage()
                recorder.relayouts.none { it === parent || it.isAncestorOf(parent) || parent.isAncestorOf(it) }
            }
        val text =
            if (onAllNodesWithTag("text").fetchSize() == 0) {
                subject
            } else {
                onNodeWithTag("text").fetch<JComponent>()
            }
        steps +=
            Grant(
                subject.size,
                text.size,
                text.preferredSize.height,
                layout.lastAskedHeight,
                validations,
                settled,
            )
    }
    return steps
}

private fun ComposeSwingTest.hostInGrid(
    parent: JComponent,
    width: Int,
    height: Int,
): MutableIntState {
    val cellWidth = mutableIntStateOf(width)
    setContent {
        Panel(PanelLayout.Grid(), modifier = SwingModifier.preferredSize(cellWidth.intValue, height)) {
            SwingNode(factory = { parent })
        }
    }
    return cellWidth
}

/** A parent asking a panel holding the [kind] of wrapping text its [query] at 1/[askedWidthDivisor] of its width. */
private data class WrappingCase(
    val kind: String,
    val query: String,
    val askedWidthDivisor: Int,
    val inColumn: Boolean,
)

/**
 * What a parent granted its child at one width: the child's size, the size of the text in it, the height the text
 * prefers at its width, the height the parent last asked and granted, the validations the parent ran over six paints
 * after the width changed, and whether the next paint asks for none.
 */
private class Grant(
    val subject: Dimension,
    val text: Dimension,
    val wanted: Int,
    val parentAskedHeight: Int,
    val validations: Int,
    val settled: Boolean,
)

/**
 * Lays its one child out at the container's width and the height [height] works out for it after the child is sized to
 * the width [asksAt] maps the container's to, as a stock parent asks a wrapping text its height.
 */
private class HeightForWidthLayout(
    private val asksAt: (Int) -> Int = { it },
    private val height: (Component) -> Int,
) : LayoutManager {
    var layouts = 0
    var lastAskedHeight = 0

    override fun layoutContainer(parent: Container) {
        layouts++
        val child = parent.getComponent(0)
        child.setSize(asksAt(parent.width), child.height)
        lastAskedHeight = height(child)
        child.setBounds(0, 0, parent.width, lastAskedHeight)
    }

    override fun preferredLayoutSize(parent: Container): Dimension = parent.getComponent(0).preferredSize

    override fun minimumLayoutSize(parent: Container): Dimension = parent.getComponent(0).minimumSize

    override fun addLayoutComponent(
        name: String?,
        component: Component,
    ) = Unit

    override fun removeLayoutComponent(component: Component) = Unit
}

/** Prefers a height of half the width it holds, and never revalidates itself as that width changes. */
private class HalfAsTallAsItHolds(
    private val preferredWidth: Int = 10,
) : JComponent() {
    override fun getPreferredSize(): Dimension = Dimension(preferredWidth, width / 2)

    override fun getMinimumSize(): Dimension = Dimension(10, 10)
}

/** Prefers a fixed size, and can shrink to a height of a quarter of the width it holds. */
private class MinimumFollowsWidth : JComponent() {
    override fun getPreferredSize(): Dimension = Dimension(10, 40)

    override fun getMinimumSize(): Dimension = Dimension(10, width / 4)
}
