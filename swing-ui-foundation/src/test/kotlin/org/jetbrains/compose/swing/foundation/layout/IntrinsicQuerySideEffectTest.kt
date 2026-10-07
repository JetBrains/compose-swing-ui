package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.LayoutManager
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A size question asked of a Foundation container where no layout pass of it follows has no effect on any component:
 * nothing is moved, resized or laid out, and no size or placement is reported, now or on the next idle and paint, and a
 * stock child whose height follows its width answers at the size it holds. That holds once every layout has settled,
 * and inside the container's own validation once it has laid its children out. Where a layout follows, as a stock
 * parent asks before laying the container out, the child is sized to the width granted instead;
 * `HeightFollowsWidthTest` pins that side.
 */
class IntrinsicQuerySideEffectTest {
    @Test
    fun anIntrinsicWidthAskedOutsideALayoutPassChangesNoComponentAndAnswersTheChildsWidthAtTheSizeItHolds() {
        IntrinsicWidthQuestions.forEach { assertNoEffect(it) }
    }

    @Test
    fun anIntrinsicHeightAskedOutsideALayoutPassChangesNoComponentAndAnswersTheChildsHeightAtTheSizeItHolds() {
        IntrinsicHeightQuestions.forEach { assertNoEffect(it) }
    }

    @Test
    fun aSwingSizeAskedOutsideALayoutPassChangesNoComponentAndAnswersTheChildsSizeAtTheSizeItHolds() {
        SwingSizeQuestions.forEach { assertNoEffect(it) }
    }

    @Test
    fun anIntrinsicWidthAskedWhileTheContainerValidatesItsChildrenChangesNoComponentAndAnswersAtTheSizeItHolds() {
        for (hook in Hooks) IntrinsicWidthQuestions.forEach { assertNoEffect(it, hook) }
    }

    @Test
    fun anIntrinsicHeightAskedWhileTheContainerValidatesItsChildrenChangesNoComponentAndAnswersAtTheSizeItHolds() {
        for (hook in Hooks) IntrinsicHeightQuestions.forEach { assertNoEffect(it, hook) }
    }

    @Test
    fun aSwingSizeAskedWhileTheContainerValidatesItsChildrenChangesNoComponentAndAnswersAtTheSizeItHolds() {
        for (hook in Hooks) SwingSizeQuestions.forEach { assertNoEffect(it, hook) }
    }

    @Test
    fun anIntrinsicHeightAskedDuringAFoundationLayoutPassSizesAStockChildToTheWidthAsked() =
        runComposeSwingTest(rootSize = Dimension(120, ROOT_HEIGHT)) {
            root.layout = BorderLayout()
            setContent {
                Box {
                    Box(modifier = SwingModifier.testTag("outer").height(IntrinsicSize.Min)) {
                        Box {
                            TextArea(
                                WRAPPING_TEXT,
                                {},
                                modifier = SwingModifier.testTag("text").fillMaxWidth(),
                                lineWrap = true,
                                wrapStyleWord = true,
                            )
                        }
                    }
                }
            }
            captureToImage()
            awaitIdle()
            val outer = onNodeWithTag("outer").fetch<JComponent>()
            val text = onNodeWithTag("text").fetch<JComponent>()

            root.setSize(WIDE_WIDTH, ROOT_HEIGHT)
            root.validate()
            captureToImage()
            awaitIdle()

            assertEquals(WIDE_WIDTH, text.width, "the text is laid out at the width the window grants")
            assertEquals(text.preferredSize.height, text.height, "and holds the height it wraps to at that width")
            assertEquals(text.height, outer.height, "the outer box's minimum height was asked at that width")
        }

    /**
     * A size question [ask] asks of the container, answered by what [expected] reads off the child as it holds, with
     * the padding the layout modifiers around it add to each axis.
     */
    private class Question<T>(
        val name: String,
        val ask: ConstrainedPanel.() -> T,
        val expected: (child: JComponent, padding: Int) -> T,
    )

    /**
     * A component tagged "hook" beside the child, which calls its argument at one point of the container's validation
     * after the container has laid its children out. Where [runsOnlyWhileInvalid] holds, it does so only while
     * invalid, so it is invalidated to be reached.
     */
    private class Hook(
        val name: String,
        val runsOnlyWhileInvalid: Boolean,
        val content: @Composable (onValidation: () -> Unit) -> Unit,
    )

    /**
     * One way a Foundation container holds a stock child tagged "child", whose height follows its width: [padding] is
     * what the layout modifiers around the child add to each axis. [content] declares the child under the `reports`
     * modifier it is handed.
     */
    private class Shape(
        val name: String,
        val padding: Int = 0,
        val content: @Composable ConstrainedScope.(reports: SwingModifier) -> Unit,
    )

    /** A stock container counting how often it is laid out. */
    private class LayoutCountingPanel(
        layout: LayoutManager,
    ) : JPanel(layout) {
        var layouts = 0

        override fun doLayout() {
            layouts++
            super.doLayout()
        }
    }

    /**
     * The components that asked for a layout pass, in order, how often each stock container was laid out, and the
     * sizes and placements the child reported.
     */
    private data class LayoutRequests(
        val relayouts: List<JComponent>,
        val layouts: List<Int>,
        val reports: List<String>,
    )

    /** Logs each move and resize of the components it listens to. */
    private class BoundsEventLog : ComponentListener {
        val events = mutableListOf<String>()

        override fun componentResized(event: ComponentEvent) {
            events += "${event.component.javaClass.simpleName} resized"
        }

        override fun componentMoved(event: ComponentEvent) {
            events += "${event.component.javaClass.simpleName} moved"
        }

        override fun componentShown(event: ComponentEvent): Unit = Unit

        override fun componentHidden(event: ComponentEvent): Unit = Unit
    }

    private companion object {
        const val WIDE_WIDTH = 480
        const val ROOT_HEIGHT = 600

        /** A finite extent narrower than any the child holds, and an unbounded one, each named. */
        val Extents = listOf("80" to 80, "Infinity" to Constraints.Infinity)

        val Shapes =
            listOf(
                Shape("a stock leaf") { reports ->
                    SwingNode(factory = { wrappingArea() }, modifier = SwingModifier.testTag("child") then reports)
                },
                Shape("a stock container") { reports ->
                    SwingNode(
                        factory = { LayoutCountingPanel(BorderLayout()).apply { add(wrappingArea()) } },
                        modifier = SwingModifier.testTag("child") then reports,
                    )
                },
                Shape("a stock leaf under fillMaxWidth and padding", padding = 8) { reports ->
                    SwingNode(
                        factory = { wrappingArea() },
                        modifier = SwingModifier.testTag("child").fillMaxWidth().padding(4) then reports,
                    )
                },
                Shape("a weighted stock leaf in a Row") { reports ->
                    Row {
                        SwingNode(
                            factory = { wrappingArea() },
                            modifier = SwingModifier.testTag("child").weight(1f) then reports,
                        )
                    }
                },
                Shape("a weighted stock leaf in a Column") { reports ->
                    Column {
                        SwingNode(
                            factory = { wrappingArea() },
                            modifier = SwingModifier.testTag("child").weight(1f) then reports,
                        )
                    }
                },
                Shape("a stock leaf filling the width of a Column") { reports ->
                    Column {
                        SwingNode(
                            factory = { wrappingArea() },
                            modifier = SwingModifier.testTag("child").fillMaxWidth() then reports,
                        )
                    }
                },
                Shape("a stock leaf in a nested Box") { reports ->
                    Box {
                        SwingNode(
                            factory = { wrappingArea() },
                            modifier = SwingModifier.testTag("child").fillMaxWidth() then reports,
                        )
                    }
                },
                Shape("an HTML label") { reports ->
                    Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.testTag("child") then reports)
                },
            )

        fun wrappingArea(): JTextArea =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }

        val IntrinsicWidthQuestions =
            Extents.flatMap { (extent, height) ->
                listOf(
                    Question("minIntrinsicWidth($extent)", { minIntrinsicWidth(height) }) { child, padding ->
                        child.minimumSize.width + padding
                    },
                    Question("maxIntrinsicWidth($extent)", { maxIntrinsicWidth(height) }) { child, padding ->
                        child.preferredSize.width + padding
                    },
                )
            }

        val IntrinsicHeightQuestions =
            Extents.flatMap { (extent, width) ->
                listOf(
                    Question("minIntrinsicHeight($extent)", { minIntrinsicHeight(width) }) { child, padding ->
                        child.minimumSize.height + padding
                    },
                    Question("maxIntrinsicHeight($extent)", { maxIntrinsicHeight(width) }) { child, padding ->
                        child.preferredSize.height + padding
                    },
                )
            }

        val SwingSizeQuestions =
            listOf(
                Question("getMinimumSize()", { minimumSize }) { child, padding ->
                    Dimension(child.minimumSize.width + padding, child.minimumSize.height + padding)
                },
                Question("getPreferredSize()", { preferredSize }) { child, padding ->
                    Dimension(child.preferredSize.width + padding, child.preferredSize.height + padding)
                },
            )

        val Hooks =
            listOf(
                Hook("from a stock child's layout", runsOnlyWhileInvalid = true) { onValidation ->
                    SwingNode(
                        factory = {
                            object : JComponent() {
                                override fun doLayout() = onValidation()
                            }
                        },
                        modifier = SwingModifier.testTag("hook"),
                    )
                },
                Hook("from a stock child's validate", runsOnlyWhileInvalid = false) { onValidation ->
                    SwingNode(
                        factory = {
                            object : JComponent() {
                                override fun validate() {
                                    super.validate()
                                    onValidation()
                                }
                            }
                        },
                        modifier = SwingModifier.testTag("hook"),
                    )
                },
                Hook("from a nested container's layout pass", runsOnlyWhileInvalid = true) { onValidation ->
                    Layout(
                        measurePolicy = { _, _ -> layout(0, 0) { onValidation() } },
                        modifier = SwingModifier.testTag("hook"),
                    )
                },
            )

        /**
         * Asserts, for each of [Shapes] in a Box under a stock parent, that [question] asked of the Box moves, resizes
         * and lays out no component and reports no size or placement, now or on the next idle and paint, and answers
         * what it expects. Without a [hook]
         * it is asked once every layout has settled. With one, the [hook] asks it as the Box is validated again, and
         * every component is held to what the same validation does without the question.
         */
        fun <T> assertNoEffect(
            question: Question<T>,
            hook: Hook? = null,
        ) {
            for (shape in Shapes) {
                val case = listOfNotNull(shape.name, question.name, hook?.name).joinToString(", ")
                runComposeSwingTest {
                    var pending: (() -> Unit)? = null
                    val reports = mutableListOf<String>()
                    val reporting =
                        SwingModifier
                            .onSizeChanged { reports += "size $it" }
                            .onPlaced { reports += "placed $it" }
                    setBoxContent(shape, hook, reporting) { pending?.also { pending = null }?.invoke() }
                    val subject = onNodeWithTag("subject").fetch<ConstrainedPanel>()
                    val tree = onNodeWithTag("parent").fetch<JComponent>().withDescendants()
                    val child = onNodeWithTag("child").fetch<JComponent>()
                    val stockContainers = tree.filterIsInstance<LayoutCountingPanel>()
                    val validateAgain =
                        hook?.let {
                            val component = onNodeWithTag("hook").fetch<JComponent>()
                            suspend {
                                if (it.runsOnlyWhileInvalid) component.invalidate()
                                subject.revalidate()
                                awaitIdle()
                            }
                        }
                    val withoutQuestion =
                        if (validateAgain == null) {
                            LayoutRequests(emptyList(), List(stockContainers.size) { 0 }, emptyList())
                        } else {
                            layoutRequestsOf(stockContainers, reports) { validateAgain() }
                        }
                    val log = BoundsEventLog()
                    tree.forEach { it.addComponentListener(log) }
                    val bounds = tree.map { it.bounds }
                    val validity = tree.map { it.isValid }
                    val preferred = child.preferredSize
                    val minimum = child.minimumSize
                    val expected = question.expected(child, shape.padding)

                    val withQuestion =
                        layoutRequestsOf(stockContainers, reports) {
                            var answer: T? = null
                            if (validateAgain == null) {
                                answer = question.ask(subject)
                                awaitIdle()
                            } else {
                                pending = { answer = question.ask(subject) }
                                validateAgain()
                                assertEquals(null, pending, "$case: the hook asks the question")
                            }
                            assertEquals(emptyList(), log.events, "$case: no component is moved or resized")
                            assertEquals(bounds, tree.map { it.bounds }, "$case: every component keeps its bounds")
                            assertEquals(validity, tree.map { it.isValid }, "$case: and stays as valid as it was")
                            assertEquals(preferred, child.preferredSize, "$case: the child prefers what it preferred")
                            assertEquals(minimum, child.minimumSize, "$case: and can shrink to what it could")
                            assertEquals(expected, answer, "$case: the answer is the child's size as it holds it")
                        }
                    assertEquals(withoutQuestion, withQuestion, "$case: no further layout pass is asked for or run")
                }
            }
        }

        /**
         * Sets [shape] reporting through [reports] in a Box tagged "subject" under a stock parent tagged "parent",
         * [hook] beside it calling [onValidation], and waits for the first paint.
         */
        suspend fun ComposeSwingTest.setBoxContent(
            shape: Shape,
            hook: Hook?,
            reports: SwingModifier,
            onValidation: () -> Unit,
        ) {
            setContent {
                SwingNode(
                    factory = { LayoutCountingPanel(BorderLayout()) },
                    modifier = SwingModifier.testTag("parent").preferredSize(320, 600),
                ) {
                    Box(modifier = SwingModifier.testTag("subject")) {
                        shape.content(this, reports)
                        hook?.content(onValidation)
                    }
                }
            }
            captureToImage()
            awaitIdle()
        }

        /**
         * The layout passes [block] and the paint after it ask for, how often each of [stockContainers] is laid out,
         * and what the child writes to [reports] meanwhile.
         */
        suspend fun ComposeSwingTest.layoutRequestsOf(
            stockContainers: List<LayoutCountingPanel>,
            reports: MutableList<String>,
            block: suspend () -> Unit,
        ): LayoutRequests =
            withRecordedRepaints { recorder ->
                stockContainers.forEach { it.layouts = 0 }
                reports.clear()
                block()
                captureToImage()
                awaitIdle()
                LayoutRequests(recorder.relayouts.toList(), stockContainers.map { it.layouts }, reports.toList())
            }
    }
}
