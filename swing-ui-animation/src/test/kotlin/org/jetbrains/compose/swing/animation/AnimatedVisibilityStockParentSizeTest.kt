package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.animation.Stretch.RATIO
import org.jetbrains.compose.swing.animation.Stretch.atAspect
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.WRAPPING_TEXT
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.layoutConstraint
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.BorderLayout
import java.awt.BorderLayout.NORTH
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.LayoutManager
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * An [AnimatedVisibility] and an [AnimatedContent] under a stock parent, which reads its sizes with no constraints.
 *
 * A wrapping text area prefers a width that follows the width it has, so for some text lengths, which ones depends on
 * the font, no width is the one it prefers at itself. A container resizing to it under a stock parent still settles:
 * the composition goes idle, and the container rests at the size plain Swing gives the text area after the sizes the
 * container gave it.
 */
class AnimatedVisibilityStockParentSizeTest {
    /**
     * A container that starts to shrink reports the content's own size before its first layout, which has no extent
     * for the ratio.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(ShrinkingExit::class)
    fun `under a stock parent a container that starts to shrink first prefers the content's own size`(
        shrinking: ShrinkingExit,
    ) = runComposeSwingTest {
        mainClock.autoAdvance = false
        val parent =
            object : FlowLayout() {
                val preferredSizes = mutableListOf<Dimension>()

                override fun preferredLayoutSize(target: Container): Dimension {
                    record(target)
                    return super.preferredLayoutSize(target)
                }

                override fun layoutContainer(target: Container) {
                    record(target)
                    super.layoutContainer(target)
                }

                private fun record(target: Container) {
                    if (target.componentCount > 0) preferredSizes += target.getComponent(0).preferredSize
                }
            }
        setContent {
            SwingNode(factory = { JPanel(parent) }) {
                InitiallyExiting(shrinking.exit, SwingModifier) {
                    Label("Preview", modifier = SwingModifier.aspectRatio(RATIO))
                }
            }
        }
        awaitIdle()

        assertEquals(
            atAspect(JLabel("Preview").preferredSize.width),
            parent.preferredSizes.first(),
            "the first size the parent read",
        )
    }

    /**
     * A container shrinking under a parent that grants no width lays the content out at its own width, whatever width
     * the parent read.
     */
    @ParameterizedTest(name = "{0}")
    @EnumSource(PreferenceReads::class)
    fun `without a stock parent width grant content keeps its own width regardless of prior preference reads`(
        reads: PreferenceReads,
    ) = runComposeSwingTest {
        val ownWidth = 320
        mainClock.autoAdvance = false
        val layout =
            object : LayoutManager {
                override fun layoutContainer(parent: Container) {
                    val child = parent.getComponent(0)
                    for (width in reads.widths) {
                        if (width != null) child.setSize(width, 0)
                        child.preferredSize
                    }
                    child.setBounds(0, 0, 0, child.preferredSize.height)
                }

                override fun preferredLayoutSize(parent: Container): Dimension = Dimension()

                override fun minimumLayoutSize(parent: Container): Dimension = Dimension()

                override fun addLayoutComponent(
                    name: String?,
                    component: Component,
                ) = Unit

                override fun removeLayoutComponent(component: Component) = Unit
            }
        setContent {
            SwingNode(factory = { JPanel(layout) }) {
                InitiallyExiting(shrinkOut(), SwingModifier) {
                    Label(
                        "Preview",
                        modifier =
                            SwingModifier
                                .testTag(CONTENT)
                                .fillMaxWidth()
                                .aspectRatio(RATIO)
                                .preferredSize(ownWidth, 20),
                    )
                }
            }
        }
        awaitIdle()

        onNodeWithTag(CONTENT)
            .assertProperty(ownWidth, "the zero grant leaves the content at its own width") { width }
    }

    @ParameterizedTest(name = "{0}, {1} characters")
    @MethodSource("containersByTextLength")
    fun `a resizing container settles around a text area whose preferred width follows its width`(
        container: ResizingContainer,
        length: Int,
    ) = runComposeSwingTest {
        val leafSizes = mutableListOf<Dimension>()
        var shown by mutableStateOf(false)
        setContent {
            UnderStockParent {
                container.Around(shown, SwingModifier.testTag(CONTAINER)) {
                    SwingNode(factory = { wrappingTextPanel(length).recordSizesInto(leafSizes) })
                }
            }
        }
        mainClock.autoAdvance = false
        shown = true
        repeat((RESIZE_MILLIS.milliseconds / mainClock.frameDuration).toInt() + 1) { driveOneFrame() }
        awaitIdle()

        onNodeWithTag(CONTAINER).assertProperty(
            wrappingTextPanelPreferredAfter(length, leafSizes.toList()),
            "the size the container rests at",
        ) { size }
    }

    /**
     * A stock panel that fills the width of an expanding container at an aspect ratio takes the width the container
     * grants, and a wrapping text area in it prefers a width that follows the width it has. The container still
     * settles: the composition goes idle, and the container rests at the ratio's height for the width it rests at.
     */
    @ParameterizedTest(name = "{0} characters")
    @MethodSource("textLengths")
    fun `an expanding container settles around a stock panel that fills its width at an aspect ratio`(length: Int) =
        runComposeSwingTest {
            val ratio = 2f
            val leafSizes = mutableListOf<Dimension>()
            setContent {
                UnderStockParent {
                    val state = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        state,
                        SwingModifier.testTag(CONTAINER),
                        expandIn(tween(RESIZE_MILLIS)),
                        ExitTransition.None,
                    ) {
                        SwingNode(
                            factory = { wrappingTextPanel(length).recordSizesInto(leafSizes) },
                            modifier = SwingModifier.fillMaxWidth().aspectRatio(ratio),
                        )
                    }
                }
            }
            awaitIdle()

            // Swing's rest width follows the leaf's allocation history; the ratio then gives the height.
            val restWidth = wrappingTextPanelPreferredAfter(length, leafSizes.toList()).width
            onNodeWithTag(CONTAINER).assertProperty(
                Dimension(restWidth, (restWidth / ratio).roundToInt()),
                "the size the container rests at",
            ) { size }
        }

    /** A stock border panel holding a flow panel in its north, which is where its content reads sizes with no limit. */
    @Composable
    private fun UnderStockParent(content: @Composable () -> Unit) {
        SwingNode(factory = { JPanel(BorderLayout()) }) {
            SwingNode(factory = { JPanel(FlowLayout()) }, modifier = SwingModifier.layoutConstraint(NORTH)) {
                content()
            }
        }
    }

    /** An exit under which the container starts to shrink. */
    enum class ShrinkingExit(
        val exit: ExitTransition,
    ) {
        ShrinkVertically(shrinkVertically()),
        ShrinkOut(shrinkOut()),
    }

    /** The widths the parent has the content read its preferred size at, in order; `null` leaves the width as it is. */
    enum class PreferenceReads(
        val widths: List<Int?>,
    ) {
        WidthOfItsOwnAfterNone(listOf(null, 160)),
        NoWidthAfterWidthOfItsOwn(listOf(160, 0)),
    }

    /** A container that resizes around the content it enters. */
    enum class ResizingContainer {
        ExpandingVisibility {
            @Composable
            override fun Around(
                shown: Boolean,
                modifier: SwingModifier,
                leaf: @Composable () -> Unit,
            ) {
                AnimatedVisibility(shown, modifier, expandIn(tween(RESIZE_MILLIS)), ExitTransition.None) { leaf() }
            }
        },
        ResizingContent {
            @Composable
            override fun Around(
                shown: Boolean,
                modifier: SwingModifier,
                leaf: @Composable () -> Unit,
            ) {
                AnimatedContent(
                    shown,
                    modifier,
                    transitionSpec = {
                        EnterTransition.None togetherWith ExitTransition.None using
                            SizeTransform { _, _ -> tween(RESIZE_MILLIS) }
                    },
                ) {
                    if (it) leaf() else Label("Hi")
                }
            }
        },
        ;

        /** This kind of container resizing around [leaf] over [RESIZE_MILLIS] once [shown]. */
        @Composable
        abstract fun Around(
            shown: Boolean,
            modifier: SwingModifier,
            leaf: @Composable () -> Unit,
        )
    }

    private companion object {
        const val RESIZE_MILLIS = 1000

        @JvmStatic
        fun textLengths(): List<Int> = listOf(1, 5, 25, 60, 100, WRAPPING_TEXT.length)

        @JvmStatic
        fun containersByTextLength(): List<Arguments> =
            ResizingContainer.entries.flatMap { container -> textLengths().map { arguments(container, it) } }
    }
}
