package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.BorderLayout
import java.awt.Component
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
 * A size query read from a Foundation container where no layout pass of it follows has no effect on any component:
 * nothing is moved, resized or laid out, and no size or placement is reported, now or on the next idle and paint, and a
 * stock child whose height follows its width returns a result at the size it holds. That holds once every layout has
 * settled,
 * and inside the container's own validation once it has laid its children out. Where a layout follows, as a stock
 * parent reads preferences before laying the container out, the child is sized to the width granted instead;
 * `HeightFollowsWidthTest` pins that side.
 */
class IntrinsicQuerySideEffectTest {
    @Test
    fun sizeReadsOutsideALayoutPassKeepBoundsValidityReportsAndDelayedLayout() {
        for (shape in Shapes) SizeReadCases.forEach { assertNoEffect(it, shape) }
    }

    @Test
    fun sizeReadsDuringValidationKeepBoundsValidityReportsAndDelayedLayout() {
        for (
        hook in
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
        ) {
            for (shape in Shapes) SizeReadCases.forEach { assertNoEffect(it, shape, hook) }
        }
    }

    @Test
    fun anIntrinsicHeightReadDuringAFoundationLayoutPassSizesAStockChildToTheSpecifiedWidth() =
        runComposeSwingTest(rootSize = Dimension(120, 600)) {
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
            val text = onNodeWithTag("text")

            root.setSize(480, 600)
            root.validate()
            captureToImage()
            awaitIdle()

            text.assertProperty(480, "the text is laid out at the width the window grants") { width }
            text.assertProperty(true, "and holds the height it wraps to at that width") {
                height == preferredSize.height
            }
            val textHeight = text.fetch<JComponent>().height
            onNodeWithTag("outer")
                .assertProperty(textHeight, "the outer box's minimum height was read at that width") { height }
        }

    /**
     * A size query performed by [read] on the container, provided by what [expected] reads off the child as it holds,
     * with
     * the padding the layout modifiers around it add to each axis.
     */
    private class SizeReadCase<T>(
        val name: String,
        val read: ConstrainedPanel.() -> T,
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
     * The components that requested a layout pass, in order, how often each stock container was laid out, and the
     * sizes and placements the child reported.
     */
    private data class LayoutRequests(
        val relayouts: List<JComponent>,
        val layouts: List<Int>,
        val reports: List<String>,
        val events: List<String> = emptyList(),
    )

    private class GeometrySnapshot(
        val tree: List<Component>,
        val child: JComponent,
    ) {
        private val bounds = tree.map { it.bounds }
        private val validity = tree.map { it.isValid }
        private val preferred = child.preferredSize
        private val minimum = child.minimumSize

        fun assertUnchanged(case: String) {
            assertEquals(bounds, tree.map { it.bounds }, "$case: every component keeps its bounds")
            assertEquals(validity, tree.map { it.isValid }, "$case: and stays as valid as it was")
            assertEquals(preferred, child.preferredSize, "$case: the child prefers what it preferred")
            assertEquals(minimum, child.minimumSize, "$case: and can shrink to what it could")
        }
    }

    private companion object {
        fun wrappingArea(): JTextArea =
            JTextArea(WRAPPING_TEXT).apply {
                lineWrap = true
                wrapStyleWord = true
            }

        @Composable
        fun WrappingChild(modifier: SwingModifier) {
            SwingNode(factory = { wrappingArea() }, modifier = SwingModifier.testTag("child") then modifier)
        }

        val SizeReadCases: List<SizeReadCase<*>> =
            listOf(80, Constraints.Infinity).flatMap { extent ->
                listOf(
                    SizeReadCase("minIntrinsicWidth($extent)", { minIntrinsicWidth(extent) }) { child, padding ->
                        child.minimumSize.width + padding
                    },
                    SizeReadCase("maxIntrinsicWidth($extent)", { maxIntrinsicWidth(extent) }) { child, padding ->
                        child.preferredSize.width + padding
                    },
                    SizeReadCase("minIntrinsicHeight($extent)", { minIntrinsicHeight(extent) }) { child, padding ->
                        child.minimumSize.height + padding
                    },
                    SizeReadCase("maxIntrinsicHeight($extent)", { maxIntrinsicHeight(extent) }) { child, padding ->
                        child.preferredSize.height + padding
                    },
                )
            } +
                listOf(
                    SizeReadCase("getMinimumSize()", { minimumSize }) { child, padding ->
                        Dimension(child.minimumSize.width + padding, child.minimumSize.height + padding)
                    },
                    SizeReadCase("getPreferredSize()", { preferredSize }) { child, padding ->
                        Dimension(child.preferredSize.width + padding, child.preferredSize.height + padding)
                    },
                )

        val Shapes =
            listOf(
                Shape("a stock leaf") { reports ->
                    WrappingChild(reports)
                },
                Shape("a stock container") { reports ->
                    SwingNode(
                        factory = { LayoutCountingPanel(BorderLayout()).apply { add(wrappingArea()) } },
                        modifier = SwingModifier.testTag("child") then reports,
                    )
                },
                Shape("a stock leaf under fillMaxWidth and padding", padding = 8) { reports ->
                    WrappingChild(SwingModifier.fillMaxWidth().padding(4) then reports)
                },
                Shape("a weighted stock leaf in a Row") { reports ->
                    Row {
                        WrappingChild(SwingModifier.weight(1f) then reports)
                    }
                },
                Shape("a weighted stock leaf in a Column") { reports ->
                    Column {
                        WrappingChild(SwingModifier.weight(1f) then reports)
                    }
                },
                Shape("a stock leaf filling the width of a Column") { reports ->
                    Column {
                        WrappingChild(SwingModifier.fillMaxWidth() then reports)
                    }
                },
                Shape("a stock leaf in a nested Box") { reports ->
                    Box {
                        WrappingChild(SwingModifier.fillMaxWidth() then reports)
                    }
                },
                Shape("an HTML label") { reports ->
                    Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.testTag("child") then reports)
                },
            )

        /**
         * Asserts, for each shape in a Box under a stock parent, that [sizeRead] read from the Box moves, resizes
         * and lays out no component and reports no size or placement, now or on the next idle and paint, and returns a
         * result
         * what it expects. Without a [hook]
         * it is read once every layout has settled. With one, the [hook] performs the read as the Box is validated
         * again, and
         * every component is held to what the same validation does without the query.
         */
        fun <T> assertNoEffect(
            sizeRead: SizeReadCase<T>,
            shape: Shape,
            hook: Hook? = null,
        ) {
            val case = listOfNotNull(shape.name, sizeRead.name, hook?.name).joinToString(", ")
            runComposeSwingTest {
                var pending: (() -> Unit)? = null
                val reports = mutableListOf<String>()
                setContent {
                    IntrinsicQueryContent(shape, reports::add, hook) {
                        pending?.also { pending = null }?.invoke()
                    }
                }
                captureToImage()
                awaitIdle()
                val subjectNode =
                    onAllNodesOfType<ConstrainedPanel>()
                        .filterToOne(SwingMatcher.hasTestTag("subject"))
                val parent = SwingMatcher.hasTestTag("parent")
                val tree =
                    onAllNodesOfType<Component>()
                        .filter(parent or SwingMatcher.hasAnyAncestor(parent))
                        .fetchAll()
                val child = onNodeWithTag("child").fetch<JComponent>()
                val stockContainers = tree.filterIsInstance<LayoutCountingPanel>()
                val withoutSizeRead =
                    hook?.let {
                        layoutRequestsOf(stockContainers, reports) { validateAgain(subjectNode.fetch(), it) }
                    } ?: LayoutRequests(emptyList(), List(stockContainers.size) { 0 }, emptyList())
                val geometry = GeometrySnapshot(tree, child)
                val expected = sizeRead.expected(child, shape.padding)
                val readExpectedSize: () -> Unit = {
                    subjectNode.assertProperty(
                        expected,
                        "$case: the result is the child's size as it holds it",
                    ) {
                        sizeRead.read(this)
                    }
                }

                val withSizeRead =
                    layoutRequestsOf(stockContainers, reports) {
                        if (hook == null) {
                            readExpectedSize()
                            awaitIdle()
                        } else {
                            pending = readExpectedSize
                            validateAgain(subjectNode.fetch(), hook)
                            assertEquals(null, pending, "$case: the hook performs the size read")
                        }
                        geometry.assertUnchanged(case)
                    }
                geometry.assertUnchanged(case)
                assertEquals(emptyList(), withSizeRead.events, "$case: no component moves or resizes, even after paint")
                assertEquals(withoutSizeRead, withSizeRead, "$case: no further layout pass is requested or run")
            }
        }

        suspend fun ComposeSwingTest.validateAgain(
            subject: ConstrainedPanel,
            hook: Hook,
        ) {
            if (hook.runsOnlyWhileInvalid) onNodeWithTag("hook").fetch<JComponent>().invalidate()
            subject.revalidate()
            awaitIdle()
        }

        @Composable
        private fun IntrinsicQueryContent(
            shape: Shape,
            report: (String) -> Unit,
            hook: Hook?,
            onValidation: () -> Unit,
        ) {
            SwingNode(
                factory = { LayoutCountingPanel(BorderLayout()) },
                modifier = SwingModifier.testTag("parent").preferredSize(320, 600),
            ) {
                Box(modifier = SwingModifier.testTag("subject")) {
                    shape.content(
                        this,
                        SwingModifier.onSizeChanged { report("size $it") }.onPlaced {
                            report("placed $it")
                        },
                    )
                    hook?.content(onValidation)
                }
            }
        }

        /**
         * The layout passes [block] and the paint after it request, how often each of [stockContainers] is laid out,
         * and what the child writes to [reports] meanwhile.
         */
        suspend fun ComposeSwingTest.layoutRequestsOf(
            stockContainers: List<LayoutCountingPanel>,
            reports: MutableList<String>,
            block: suspend () -> Unit,
        ): LayoutRequests =
            withRecordedRepaints { recorder ->
                val events = mutableListOf<String>()
                val listener =
                    object : ComponentListener {
                        override fun componentResized(event: ComponentEvent) {
                            events += "${event.component.javaClass.simpleName} resized"
                        }

                        override fun componentMoved(event: ComponentEvent) {
                            events += "${event.component.javaClass.simpleName} moved"
                        }

                        override fun componentShown(event: ComponentEvent): Unit = Unit

                        override fun componentHidden(event: ComponentEvent): Unit = Unit
                    }
                val layoutCountingPanel = SwingMatcher.isOfType<LayoutCountingPanel>()
                val parent = SwingMatcher.hasTestTag("parent")
                val inParentTree = parent or SwingMatcher.hasAnyAncestor(parent)
                val inStockSubtree = layoutCountingPanel or SwingMatcher.hasAnyAncestor(layoutCountingPanel)
                val tree =
                    onAllNodesOfType<Component>()
                        .filter(inParentTree and inStockSubtree)
                        .fetchAll()
                tree.forEach { it.addComponentListener(listener) }
                stockContainers.forEach { it.layouts = 0 }
                reports.clear()
                block()
                captureToImage()
                awaitIdle()
                tree.forEach { it.removeComponentListener(listener) }
                LayoutRequests(
                    recorder.relayouts.toList(),
                    stockContainers.map { it.layouts },
                    reports.toList(),
                    events.toList(),
                )
            }
    }
}
