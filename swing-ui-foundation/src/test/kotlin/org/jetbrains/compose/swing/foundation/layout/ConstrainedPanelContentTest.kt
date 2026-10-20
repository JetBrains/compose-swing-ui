package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A [ConstrainedPanel] built outside a composition hosts Foundation content through its own `setContent`. */
class ConstrainedPanelContentTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("parents")
    fun disposingTheContentHandleDisposesTheCompositionAndRemovesItsChildren(explicitParent: Boolean) =
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("layers")
    internal fun aPolicyLayerLaysOutAndPaintsItsChildOutsideAnyComposition(case: LayerCase) =
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
                        layout(100, 60) {
                            placeable.placeWithLayer(10, 5) {
                                clip = case.clip
                                alpha = case.alpha
                            }
                        }
                    },
                )
            if (case.overOpaqueHost) {
                parent.isOpaque = true
                parent.background = Color.WHITE
                JPanel(null).add(parent)
            }
            parent.add(child)
            parent.setBounds(0, 0, 100, 60)
            parent.doLayout()
            assertEquals(
                Rectangle(10, 5, 40, 20),
                child.bounds,
                "the layer places the child at the policy's coordinates",
            )
            val image = BufferedImage(100, 60, BufferedImage.TYPE_INT_ARGB)
            parent.paint(image.createGraphics())

            image.assertPixelColor(case.expected, 30, 15, "${case.name}: the child's center")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("flowContents")
    internal fun topLevelContentIsMeasuredAndPlacedUnderAStockParent(case: FlowContentCase) =
        runComposeSwingTest {
            val panel = case.createPanel()
            setContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { add(panel) } })
            }
            val content = panel.setContent(content = case.content)
            try {
                awaitIdle()
                assertEquals(case.panel, panel.bounds, "${case.name}: ${case.panelReason}")
                for ((tag, bounds, reason) in case.children) {
                    onNodeWithTag(tag).assertProperty(bounds, "${case.name}: $reason") { this.bounds }
                }
            } finally {
                content.dispose()
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

            onNodeWithTag("child").assertProperty(
                Rectangle(0, 0, 40, 20),
                "the default policy places the constrained child at the start",
            ) { bounds }
            assertEquals(Dimension(100, 50), panel.size)
            onNodeWithTag("control").assertProperty(
                Rectangle(0, 0, 40, 20),
                "unread parent data leaves placement unchanged",
            ) { bounds }
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
                awaitIdle()
                assertEquals(Dimension(100, 50), panel.size, "the parent grants the preferred size")

                panel.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT)
                panel.revalidate()
                awaitIdle()
                onNodeWithTag("child").assertProperty(
                    Rectangle(0, 0, 40, 20),
                    "the child starts at the leading edge",
                ) { bounds }

                panel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT)
                panel.revalidate()
                awaitIdle()
                onNodeWithTag("child").assertProperty(
                    Rectangle(60, 0, 40, 20),
                    "the child follows the orientation after layout",
                ) { bounds }
            } finally {
                content.dispose()
            }
        }

    @Test
    fun aPanelUnderAFoundationParentIsMeasuredWithThatParentsConstraints() =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent {
                Column(modifier = SwingModifier.styleable { preferredSize(300, 200) }) {
                    SwingNode(factory = { panel })
                }
            }
            panel.setContent {
                Box(modifier = SwingModifier.testTag("filling").fillMaxWidth().height(20))
                Box(modifier = SwingModifier.testTag("fixed").width(120).height(50))
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 300, 50), panel.bounds, "the panel takes the largest of its children")
            onNodeWithTag("filling").assertProperty(
                Rectangle(0, 0, 300, 20),
                "fillMaxWidth fills the width the Column offers the panel",
            ) { bounds }
            onNodeWithTag("fixed").assertProperty(
                Rectangle(0, 0, 120, 50),
                "every child stands at the panel's origin",
            ) { bounds }
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappingCases")
    internal fun wrappingContentReportsPreferredAndNaturalMinimumHeightAsWidthsChange(case: WrappingCase) {
        val wrapping = WrappingTexts.getValue(case.kind)
        val widths = listOf(320, 160, 400)
        val inSlot = { HeightForWidthLayout({ it / case.askedWidthDivisor }, HeightQueries.getValue(case.query)) }
        val areaGrants = grants(inSlot(), widths, plainTextArea())
        val panel = ConstrainedPanel()
        val panelGrants =
            grants(
                inSlot(),
                widths,
                panel,
                assertText = { granted, subjectSize ->
                    val text = onNodeWithTag("text")
                    text.assertProperty(granted, "$case at width $granted: the text fills the width") { width }
                    if (case.expectations.textTakesPreferredHeight) {
                        val live = text.fetch()
                        assertEquals(
                            live.preferredSize.height,
                            live.height,
                            "$case at width $granted: the text takes the height it prefers",
                        )
                    }
                    if (!case.inColumn) {
                        text.assertProperty(subjectSize, "$case at width $granted: no gap or clip") { size }
                    }
                },
            ) {
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
            if (case.expectations.grantMatchesTextArea && areaGrant.settled) {
                assertEquals(
                    areaGrant.subject,
                    panelGrant.subject,
                    "$at: the parent grants the panel what it grants the text",
                )
            }
            assertTrue(panelGrant.settled || !areaGrant.settled, "$at: the panel settles where a text area does")
            assertTrue(
                panelGrant.validations <= boxGrants[step].validations,
                "$at: the hosted panel needs ${panelGrant.validations} validations, a composed Box " +
                    "${boxGrants[step].validations}",
            )
            if (case.expectations.panelKeepsAskedHeight) {
                assertEquals(
                    panelGrant.parentAskedHeight,
                    panelGrant.subject.height,
                    "$at: the panel keeps the minimum height its parent asked and granted",
                )
            }
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("wrappingQueries")
    fun aQueryAtAnotherWidthLeavesASettledPanelAndItsContentAsTheyAre(
        kind: String,
        query: String,
    ) = runComposeSwingTest {
        val wrapping = WrappingTexts.getValue(kind)
        val ask = IntrinsicHeightQueries.getValue(query)
        val panel = ConstrainedPanel()
        val parent = JPanel(HeightForWidthLayout { it.preferredSize.height }).apply { add(panel) }
        hostInGrid(parent, 320, 600)
        panel.setContent { wrapping(SwingModifier.testTag("text")) }
        awaitIdle()
        cyclesUntilStable(parent)
        val text = onNodeWithTag("text").fetch<JComponent>()
        val textBounds = text.bounds
        val panelBounds = panel.bounds

        assertPaintAsksForNoLayout(panel, "$kind: asking the panel its $query at other widths") {
            val narrow = ask(panel, 160)
            val wide = ask(panel, 480)
            assertEquals(
                panelBounds.height,
                narrow,
                "$kind: asked its $query at 160, a settled panel reports the height it holds",
            )
            assertEquals(panelBounds.height, wide, "$kind: and at 480")
        }

        assertSame(text, onNodeWithTag("text").fetch<JComponent>(), "$kind: asking keeps the same text component")
        onNodeWithTag("text").assertProperty(textBounds, "$kind: asking resizes the text nowhere") { bounds }
        assertEquals(panelBounds, panel.bounds, "$kind: nor the panel")
        onNodeWithTag("text").assertProperty(true, "$kind: all stay valid") {
            generateSequence<Component>(this) { it.parent }.all { it.isValid }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("heightQueries")
    fun fixedHeightContentNeedsNoLayoutWhenTheParentChangesWidth(query: String) =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            val parent = JPanel(HeightForWidthLayout(height = HeightQueries.getValue(query))).apply { add(panel) }
            val width = hostInGrid(parent, 320, 600)
            panel.setContent { Label("x", style = { preferredSize(80, 80) }) }
            awaitIdle()
            assertTrue(
                panel.minIntrinsicHeight(320) < panel.maxIntrinsicHeight(320),
                "the label prefers a height above its minimum",
            )

            width.intValue = 160
            awaitIdle()

            assertPaintAsksForNoLayout(panel, "asked its $query, the first paint at the new width")
        }

    @Test
    fun aParentAskingTheHeightAtAnotherWidthThanItGrantsCostsNoMoreValidationsThanForATextArea() {
        val widths = listOf(320, 160, 400, 320)
        val heights = listOf(40, 20, 50, 40)
        val inSlot = { HeightForWidthLayout({ it / 4 }) { it.preferredSize.height } }
        val areaGrants = grants(inSlot(), widths, plainTextArea())
        val panel = ConstrainedPanel()
        val panelGrants =
            grants(
                inSlot(),
                widths,
                panel,
                assertText = { granted, _ ->
                    onNodeWithTag("text").assertProperty(
                        Dimension(granted, granted / 2),
                        "at width $granted: the aspect ratio can exceed the granted height",
                    ) { size }
                },
            ) {
                panel.setContent { Label("x", modifier = SwingModifier.testTag("text").fillMaxWidth().aspectRatio(2f)) }
            }
        for ((step, width) in widths.withIndex()) {
            val actual = panelGrants[step]
            val area = areaGrants[step]
            val at = "at width $width"
            assertTrue(actual.settled || !area.settled, "$at: the panel settles where a text area does")
            assertTrue(
                actual.validations <= area.validations,
                "$at: panel validations ${actual.validations} exceed native ${area.validations}",
            )
            assertEquals(Dimension(width, heights[step]), actual.subject, "$at: the parent's grant")
        }
    }

    @ParameterizedTest(name = "{0}, divisor={1}")
    @MethodSource("widthDependentQueries")
    fun aWidthDependentContentIsGrantedTheHeightItPrefersAtItsQueriedWidth(
        query: String,
        askedWidthDivisor: Int,
    ) {
        val widths = listOf(320, 160, 400)
        val inSlot = { HeightForWidthLayout({ it / askedWidthDivisor }, HeightQueries.getValue(query)) }
        val plainGrants = grants(inSlot(), widths, HalfAsTallAsItHolds(preferredWidth = 640))
        val areaGrants = grants(inSlot(), widths, plainTextArea())
        val panel = ConstrainedPanel()
        val panelGrants =
            grants(
                inSlot(),
                widths,
                panel,
                assertText = { granted, subjectSize ->
                    onNodeWithTag("text").assertProperty(
                        subjectSize,
                        "$query at 1/$askedWidthDivisor of width $granted: the content fills the panel",
                    ) { size }
                },
            ) {
                panel.setContent {
                    SwingNode(
                        factory = { HalfAsTallAsItHolds(preferredWidth = 640) },
                        modifier = SwingModifier.testTag("text").fillMaxWidth(),
                    )
                }
            }
        for ((step, width) in widths.withIndex()) {
            val at = "$query at 1/$askedWidthDivisor of width $width"
            val actual = panelGrants[step]
            val plain = plainGrants[step]
            val area = areaGrants[step]
            assertEquals(plain.subject, actual.subject, "$at: the parent grants the native size")
            assertEquals(
                Dimension(width, width / askedWidthDivisor / 2),
                actual.subject,
                "$at: the parent grants the height preferred at its queried width",
            )
            assertTrue(actual.settled, "$at: the panel settles")
            assertTrue(
                actual.validations <= area.validations,
                "$at: panel validations ${actual.validations} exceed native ${area.validations}",
            )
        }
    }

    @Test
    fun aMinimumHeightReadAtAnEarlierWidthIsNotCheckedByAPaintAtAWidthNobodyAskedAt() =
        runComposeSwingTest {
            var height: (Component) -> Int = { child -> child.preferredSize.height.also { child.minimumSize } }
            val panel = ConstrainedPanel()
            val parent = JPanel(HeightForWidthLayout(height = { height(it) })).apply { add(panel) }
            setContent {
                Panel(PanelLayout.Grid(), style = { preferredSize(400, 600) }) {
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
            assertPaintAsksForNoLayout(
                panel,
                "the paint at a width the parent asked nothing at, where the preferred height is unchanged and " +
                    "the minimum height was last asked at an earlier width,",
            )
            assertEquals(Dimension(400, 40), panel.size, "the parent grants the height the panel has")
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("heightQueries")
    fun aPaintAtAnotherWidthDoesNotValidateThePanel(query: String) {
        val ask = HeightQueries.getValue(query)
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

    @ParameterizedTest(name = "intrinsic extent {0}")
    @MethodSource("intrinsicExtents")
    fun aSizeSetOnThePanelReportsTheIntrinsicFunctionsOfItsKind(extent: Int) =
        runComposeSwingTest {
            val panel = ConstrainedPanel()
            setContent { SwingNode(factory = { panel }) }
            panel.setContent { Box(modifier = SwingModifier.size(40, 20)) }
            awaitIdle()

            panel.minimumSize = Dimension(10, 77)
            panel.preferredSize = Dimension(333, 88)

            assertEquals(10, panel.minIntrinsicWidth(extent), "a minimum size reports the min width at $extent")
            assertEquals(77, panel.minIntrinsicHeight(extent), "and the min height at $extent")
            assertEquals(333, panel.maxIntrinsicWidth(extent), "a preferred size reports the max width at $extent")
            assertEquals(88, panel.maxIntrinsicHeight(extent), "and the max height at $extent")
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

    private fun ComposeSwingTest.assertPaintAsksForNoLayout(
        panel: ConstrainedPanel,
        message: String,
        beforePaint: () -> Unit = {},
    ) {
        withRecordedRepaints { recorder ->
            beforePaint()
            captureToImage()
            recorder.assertAskedForNoLayout(panel, message)
        }
    }

    companion object {
        /** The height a stock parent works out for its child at the child's width, by Swing's sizes. */
        private val HeightQueries: Map<String, (Component) -> Int> =
            mapOf(
                "preferred height" to { child -> child.preferredSize.height },
                "minimum height" to { child -> child.minimumSize.height },
                "preferred then minimum height" to { child -> child.preferredSize.height.also { child.minimumSize } },
            )

        /** The [Constrainable] height functions. */
        private val IntrinsicHeightQueries: Map<String, (Constrainable, Int) -> Int> =
            mapOf(
                "max intrinsic height" to Constrainable::maxIntrinsicHeight,
                "min intrinsic height" to Constrainable::minIntrinsicHeight,
            )

        private fun plainTextArea() =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }

        @JvmStatic
        fun parents(): List<Arguments> =
            listOf(
                Arguments.of(Named.of("implicit parent", false)),
                Arguments.of(Named.of("explicit parent", true)),
            )

        @JvmStatic
        fun layers(): List<Arguments> =
            listOf(
                LayerCase("a clipping layer", clip = true, alpha = 1f, overOpaqueHost = false, expected = Color.RED),
                LayerCase(
                    "a half-transparent layer over an opaque host",
                    clip = false,
                    alpha = 0.5f,
                    overOpaqueHost = true,
                    expected = Color(0xFF, 0x7F, 0x7F),
                ),
            ).map { Arguments.of(Named.of(it.name, it)) }

        @JvmStatic
        fun flowContents(): List<Arguments> =
            listOf(topLevelModifiersCase(), panelConstraintsCase(), constructorPolicyCase())
                .map { Arguments.of(Named.of(it.name, it)) }

        private fun topLevelModifiersCase(): FlowContentCase =
            FlowContentCase(
                "layout modifiers at the top level",
                content = {
                    val modifier =
                        SwingModifier
                            .testTag("child")
                            .padding(10)
                            .width(120)
                            .height(30)
                    Box(modifier = modifier)
                },
                panel = Rectangle(0, 0, 140, 50),
                panelReason = "the stock parent grants the panel its preferred size",
                children =
                    listOf(
                        Triple(
                            "child",
                            Rectangle(10, 10, 120, 30),
                            "the padding, width and height declared at the top level size and place the child",
                        ),
                    ),
            )

        private fun panelConstraintsCase(): FlowContentCase =
            FlowContentCase(
                "each child gets the panel constraints",
                content = {
                    Box(modifier = SwingModifier.testTag("wide").size(40, 20))
                    Box(modifier = SwingModifier.testTag("tall").size(30, 50))
                },
                panel = Rectangle(0, 0, 40, 50),
                panelReason = "the panel prefers the largest of its children",
                children =
                    listOf(
                        Triple(
                            "wide",
                            Rectangle(0, 0, 40, 50),
                            "laid out at a fixed size, the panel passes that size to each child, " +
                                "as androidx's root does",
                        ),
                        Triple("tall", Rectangle(0, 0, 40, 50), "and every child takes that same size"),
                    ),
            )

        private fun constructorPolicyCase(): FlowContentCase =
            FlowContentCase(
                "constructor policy stacks the content",
                createPanel = {
                    ConstrainedPanel(
                        MeasurePolicy { measurables, constraints ->
                            val placeables = measurables.map { it.measure(constraints.copyMaxDimensions()) }
                            layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.sumOf { it.height }) {
                                var y = 0
                                placeables.forEach {
                                    it.place(0, y)
                                    y += it.height
                                }
                            }
                        },
                    )
                },
                content = {
                    Box(modifier = SwingModifier.testTag("first").size(40, 20))
                    Box(modifier = SwingModifier.testTag("second").size(30, 50))
                },
                panel = Rectangle(0, 0, 40, 70),
                panelReason = "the stock parent grants the size the policy reports",
                children =
                    listOf(
                        Triple(
                            "first",
                            Rectangle(0, 0, 40, 20),
                            "the policy places the first child at the top, at its own size",
                        ),
                        Triple("second", Rectangle(0, 20, 30, 50), "the policy stacks the children"),
                    ),
            )

        @JvmStatic
        fun wrappingCases(): List<Arguments> =
            wrappingRows().flatMap { row ->
                listOf(false, true).map {
                    Arguments.of(
                        Named.of(
                            "${row.kind}, ${row.query}, 1/${row.askedWidthDivisor}" + if (it) ", in a Column" else "",
                            row.copy(inColumn = it),
                        ),
                    )
                }
            }

        @JvmStatic
        fun wrappingQueries(): List<Arguments> =
            WrappingTexts.keys.flatMap { kind -> IntrinsicHeightQueries.keys.map { Arguments.of(kind, it) } }

        @JvmStatic
        fun heightQueries(): List<Arguments> = HeightQueries.keys.map { Arguments.of(it) }

        @JvmStatic
        fun widthDependentQueries(): List<Arguments> =
            (HeightQueries.keys - "minimum height").flatMap { query -> listOf(1, 2).map { Arguments.of(query, it) } }

        @JvmStatic
        fun intrinsicExtents(): List<Arguments> =
            listOf(Arguments.of(Named.of("320", 320)), Arguments.of(Named.of("unbounded", Constraints.Infinity)))
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
    assertText: ComposeSwingTest.(width: Int, subjectSize: Dimension) -> Unit = { _, _ -> },
    populate: ComposeSwingTest.() -> Unit = {},
): List<Grant> {
    val steps = mutableListOf<Grant>()
    runComposeSwingTest {
        val parent = JPanel(layout).apply { add(subject) }
        val width = hostInGrid(parent, widths.first(), 600)
        populate()
        awaitIdle()
        steps += recordGrants(layout, WidthSweep(width, widths), parent, subject, assertText)
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
            Panel(PanelLayout.Grid(), style = { preferredSize(width.intValue, 600) }) {
                SwingNode(factory = { parent }) {
                    Box(propagateMinConstraints = true) {
                        val modifier = SwingModifier.testTag("text").fillMaxWidth()
                        if (case.inColumn) Column { wrapping(modifier) } else wrapping(modifier)
                    }
                }
            }
        }
        awaitIdle()
        steps +=
            recordGrants(
                layout,
                WidthSweep(width, widths),
                parent,
                assertIs<JComponent>(parent.getComponent(0), "expected a Swing child"),
            )
    }
    return steps
}

/** The state holding the width a parent grants its child, and the widths it takes one after another. */
private class WidthSweep(
    val width: MutableIntState,
    val widths: List<Int>,
)

private suspend fun ComposeSwingTest.recordGrants(
    layout: HeightForWidthLayout,
    sweep: WidthSweep,
    parent: JComponent,
    subject: JComponent,
    assertText: ComposeSwingTest.(width: Int, subjectSize: Dimension) -> Unit = { _, _ -> },
): List<Grant> {
    val steps = mutableListOf<Grant>()
    for (granted in sweep.widths) {
        val before = layout.layouts
        sweep.width.intValue = granted
        awaitIdle()
        settleWithPaint()
        val validations = layout.layouts - before
        val settled =
            withRecordedRepaints { recorder ->
                captureToImage()
                recorder.relayouts.none { it === parent || it.isAncestorOf(parent) || parent.isAncestorOf(it) }
            }
        assertText(granted, subject.size)
        steps += Grant(subject.size, layout.lastAskedHeight, validations, settled)
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
        Panel(PanelLayout.Grid(), style = { preferredSize(cellWidth.intValue, height) }) {
            SwingNode(factory = { parent })
        }
    }
    return cellWidth
}

/** A parent asking a panel holding the [kind] of wrapping text its [query] at 1/[askedWidthDivisor] of its width. */
internal data class WrappingCase(
    val kind: String,
    val query: String,
    val askedWidthDivisor: Int,
    val inColumn: Boolean,
    val expectations: WrappingExpectations,
)

internal data class WrappingExpectations(
    val textTakesPreferredHeight: Boolean,
    val grantMatchesTextArea: Boolean,
    val panelKeepsAskedHeight: Boolean,
)

/**
 * Every kind of wrapping text under every height query and divisor it is asked at, outside a [Column], with the flags
 * the query and the kind expect of the panel.
 */
private fun wrappingRows(): List<WrappingCase> = textAreaRows() + htmlLabelRows() + htmlEditorPaneRows()

private fun textAreaRows(): List<WrappingCase> =
    listOf(
        WrappingCase(
            kind = "TextArea",
            query = "preferred height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = true,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "TextArea",
            query = "preferred height",
            askedWidthDivisor = 2,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = true,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "TextArea",
            query = "minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = true,
                ),
        ),
        WrappingCase(
            kind = "TextArea",
            query = "preferred then minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = true,
                    panelKeepsAskedHeight = false,
                ),
        ),
    )

private fun htmlLabelRows(): List<WrappingCase> =
    listOf(
        WrappingCase(
            kind = "HTML Label",
            query = "preferred height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "HTML Label",
            query = "preferred height",
            askedWidthDivisor = 2,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "HTML Label",
            query = "minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = true,
                ),
        ),
        WrappingCase(
            kind = "HTML Label",
            query = "preferred then minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
    )

private fun htmlEditorPaneRows(): List<WrappingCase> =
    listOf(
        WrappingCase(
            kind = "HTML EditorPane",
            query = "preferred height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "HTML EditorPane",
            query = "preferred height",
            askedWidthDivisor = 2,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
        WrappingCase(
            kind = "HTML EditorPane",
            query = "minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = false,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = true,
                ),
        ),
        WrappingCase(
            kind = "HTML EditorPane",
            query = "preferred then minimum height",
            askedWidthDivisor = 1,
            inColumn = false,
            expectations =
                WrappingExpectations(
                    textTakesPreferredHeight = true,
                    grantMatchesTextArea = false,
                    panelKeepsAskedHeight = false,
                ),
        ),
    )

/**
 * What a parent granted its child at one width: the child's size, the height the parent last asked and granted, the
 * validations the parent ran over six paints after the width changed, and whether the next paint asks for none.
 */
private class Grant(
    val subject: Dimension,
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

/**
 * Content set on a panel under a stock parent, the panel's expected bounds, and each tagged child's, each with why.
 */
internal class FlowContentCase(
    val name: String,
    val createPanel: () -> ConstrainedPanel = { ConstrainedPanel() },
    val content: @Composable ConstrainedScope.() -> Unit,
    val panel: Rectangle,
    val panelReason: String,
    val children: List<Triple<String, Rectangle, String>>,
)

/** A layer a policy places a red child with onto a panel no node holds, and the color painted at the child's center. */
internal class LayerCase(
    val name: String,
    val clip: Boolean,
    val alpha: Float,
    val overOpaqueHost: Boolean,
    val expected: Color,
)
