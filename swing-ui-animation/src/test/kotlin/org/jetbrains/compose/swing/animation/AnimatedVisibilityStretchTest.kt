// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.animation.Stretch.FRAME_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.FlowLayoutParent
import org.jetbrains.compose.swing.animation.Stretch.NARROW
import org.jetbrains.compose.swing.animation.Stretch.ParentCase
import org.jetbrains.compose.swing.animation.Stretch.RATIO
import org.jetbrains.compose.swing.animation.Stretch.ResizeCase
import org.jetbrains.compose.swing.animation.Stretch.ResizedFrames
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WIDE
import org.jetbrains.compose.swing.animation.Stretch.WideContent
import org.jetbrains.compose.swing.animation.Stretch.atAspect
import org.jetbrains.compose.swing.animation.Stretch.observeFrameSizes
import org.jetbrains.compose.swing.animation.Stretch.recordEntering
import org.jetbrains.compose.swing.animation.Stretch.resizedFrames
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How an [AnimatedVisibility] directly under a stock parent lays its content out while it enters. */
internal class AnimatedVisibilityStretchTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("stretchingParents")
    fun `without a size change an enter lays the content out in the space a stretching stock parent grants`(
        parent: ParentCase,
    ) {
        val entering = recordEntering(parent.parent, visibility(DelayedFadeIn.enter, WideContent))

        assertEquals(parent.settled, entering.whileEntering)
        assertLayoutsSettle(entering.frames)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stretchingParents")
    fun `a size change lays the content out within the space a stretching stock parent grants`(parent: ParentCase) {
        val frames = observeGrants(parent.parent, DelayedExpandIn)

        val entering = frames.dropLast(1)
        assertEquals(entering.map { parent.settled.width }, entering.map { it.container.width }, "the grant's width")
        assertEquals(entering.map { parent.settled }, entering.map { it.content }, "the content on each frame")
        assertLayoutsSettle(frames)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requestGrantingParents")
    fun `a size change keeps the content at its rest size in the animated request a stock parent grants`(
        parent: ParentCase,
    ) {
        val frames = observeGrants(parent.parent, DelayedExpandIn)

        val entering = frames.dropLast(1)
        val grants = entering.map { it.container }
        val roomy = entering.filter { it.container.width > 0 && it.container.height > 0 }
        assertTrue(roomy.size < entering.size, "precondition: the container began without room: $grants")
        assertTrue(
            grants.first().width < grants.last().width || grants.first().height < grants.last().height,
            "precondition: the grant grew: $grants",
        )
        assertTrue(
            roomy.any { it.container.width < parent.settled.width || it.container.height < parent.settled.height },
            "precondition: the container was never part-way: $grants",
        )
        assertEquals(
            roomy.map { parent.settled },
            roomy.map { it.content },
            "the content on each frame the container had room, whatever its size",
        )
        // The animated request is the content's natural size, so a region narrower than that grants more height than
        // the content needs once settled, until the size animation ends.
        val beforeSettling = grants.dropLastWhile { it == parent.settledContainer }
        assertTrue(beforeSettling.isNotEmpty(), "the grant never moved before settling: $grants")
        assertTrue(
            beforeSettling.none { it == parent.settledContainer },
            "the settled grants were not a suffix: $grants",
        )
        assertEquals(emptyList(), beforeSettling.shrinkages(), "the grant shrank")
        assertEquals(parent.settledContainer, frames.last().container, "the grant once settled")
        assertEquals(parent.settled, frames.last().content, "the content once settled")
        assertLayoutsSettle(frames)
    }

    @ParameterizedTest(name = "{0} under {1}")
    @MethodSource("wrappingTextEnters")
    fun `an enter keeps a wrapping text area at the width it rests at in a stock parent that follows the request`(
        enter: EnterCase,
        parent: ParentCase,
    ) {
        val leafSizes = mutableListOf<Dimension>()
        val frames =
            observeFrameSizes(
                parent.parent,
                visibility(enter.enter) {
                    SwingNode(factory = { wrappingTextPanel(100).recordSizesInto(leafSizes) }, modifier = it)
                },
                settle = true,
            )
        val rest = wrappingTextPanelPreferredAfter(100, leafSizes.toList())

        val roomy = frames.dropLast(1).filter { it.container.width > 0 }
        assertTrue(roomy.isNotEmpty(), "precondition: the container had room")
        assertEquals(
            setOf(rest.width),
            roomy.mapTo(mutableSetOf()) { it.content.width },
            "the text area's widths across frames",
        )
        assertEquals(
            emptyList(),
            roomy.filter { it.content.height > rest.height },
            "the frames taller than the rest height",
        )
        assertEquals(rest, frames.last().content, "the text area once settled")
        assertLayoutsSettle(frames)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resizes")
    fun `under a following stock parent a fade grants resized content what a plain box is granted`(
        resize: ResizeCase,
    ) {
        val plain = resizedWhile(resize) { content -> plainBox(content) }
        val fading = resizedWhile(resize) { content -> visibility(fadeIn(tween(TRANSITION_MILLIS)), content) }

        val plainFrames = plain.afterResize
        val fadingFrames = fading.afterResize
        assertEquals(
            plainFrames.map { atAspect(NARROW) },
            plainFrames.map { it.content },
            "precondition: the plain box's content",
        )
        assertEquals(plainFrames.map { it.container }, fadingFrames.map { it.container }, "the grant")
        assertEquals(plainFrames.map { it.content }, fadingFrames.map { it.content }, "the content")
        assertLayoutsSettle(plain.all)
        assertLayoutsSettle(fading.all)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resizes")
    fun `under a following stock parent an expanding enter keeps resized content at the size it rests at`(
        resize: ResizeCase,
    ) {
        val resized = resizedWhile(resize) { content -> visibility(expandIn(tween(TRANSITION_MILLIS)), content) }

        val expanding = resized.afterResize
        val grants = expanding.map { it.container }
        assertTrue(grants.first().width < grants.last().width, "precondition: the grant grew: $grants")
        assertTrue(grants.first().width < resize.settled.width, "precondition: the container was not part-way: $grants")
        assertEquals(emptyList(), grants.shrinkages(), "the grant shrank")
        assertEquals(
            expanding.map { resize.settled },
            expanding.map { it.content },
            "the content on each frame, in a container of the size granted on it",
        )
        assertLayoutsSettle(resized.all)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("widthKeepingEnters")
    fun `in a viewport an enter that keeps the width lays wide content out at its own width`(enter: EnterCase) {
        val entering = recordEntering(InViewport, visibility(enter.enter, WideContent))

        checkNotNull(entering.viewportExtent) { "precondition: the view was in a viewport" }
        assertEquals(atAspect(WIDE), entering.whileEntering, "the content's size while entering")
        assertEquals(atAspect(WIDE), entering.settled, "the content's size settled")
        assertLayoutsSettle(entering.frames)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("widthChangingEnters")
    fun `in a viewport an enter that changes the width lays wide content out at the viewport's width`(
        enter: EnterCase,
    ) {
        val entering = recordEntering(InViewport, visibility(enter.enter, WideContent))

        val extent = checkNotNull(entering.viewportExtent) { "precondition: the view was in a viewport" }
        assertEquals(atAspect(extent.width), entering.whileEntering, "the content's size while entering")
        assertEquals(atAspect(WIDE), entering.settled, "the content's size settled")
        assertLayoutsSettle(entering.frames)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enters")
    fun `in a viewport an enter lays content that fills its width out at the viewport's width`(enter: EnterCase) {
        val entering =
            recordEntering(
                InViewport,
                // Prefers the width of its text, and fills the width it is offered at a 16:9 aspect ratio.
                visibility(enter.enter) { modifier ->
                    Label("Preview", modifier = modifier.fillMaxWidth().aspectRatio(RATIO))
                },
            )

        val extent = checkNotNull(entering.viewportExtent) { "precondition: the view was in a viewport" }
        assertEquals(atAspect(extent.width), entering.whileEntering, "the content's size while entering")
        assertLayoutsSettle(entering.frames)
    }

    /**
     * The final request outlasts the size animation: once the container has the size the animation ends on, the fade
     * that outlasts it, and the fade ending, leave it there. The content keeps its rest size throughout.
     */
    @Test
    fun `a size animation shorter than the fade keeps its final request until the fade ends`() {
        val enter = expandIn(tween(TRANSITION_MILLIS / 4)) + fadeIn(tween(2 * TRANSITION_MILLIS))
        val fadeFrames = TRANSITION_MILLIS / FRAME_MILLIS
        val frames =
            observeFrameSizes(FlowLayoutParent.parent, visibility(enter, WideContent), frames = 3 * fadeFrames)

        val roomy = frames.filter { it.container.width > 0 && it.container.height > 0 }
        assertTrue(roomy.any { it.container.width < WIDE }, "precondition: the container was never part-way")
        assertEquals(roomy.map { atAspect(WIDE) }, roomy.map { it.content }, "the content while the container had room")
        val reached = frames.indexOfFirst { it.container == atAspect(WIDE) }
        assertTrue(reached in 0 until fadeFrames, "precondition: the size animation ended while the fade ran: $reached")
        val kept = frames.drop(reached)
        assertEquals(kept.map { atAspect(WIDE) }, kept.map { it.container }, "the final request through the fade")
        assertEquals(kept.map { atAspect(WIDE) }, kept.map { it.content }, "the content through the fade")
        assertLayoutsSettle(frames)
    }

    private fun plainBox(content: TaggedContent): EnteringContainer =
        { modifier, _, contentModifier -> Box(modifier) { content(contentModifier) } }

    private fun resizedWhile(
        resize: ResizeCase,
        container: (TaggedContent) -> EnteringContainer,
    ): ResizedFrames = resizedFrames(resize, container, TRANSITION_MILLIS / FRAME_MILLIS / 2)

    /** The size the container is granted and the size the content is laid out at, on each frame of [enter]. */
    private fun observeGrants(
        parent: StockParent,
        enter: EnterCase,
    ): List<FrameSizes> = observeFrameSizes(parent, visibility(enter.enter, WideContent), settle = true)

    /** An enter delayed by one transition's length. */
    class EnterCase(
        val name: String,
        val enter: EnterTransition,
    ) {
        override fun toString(): String = name
    }

    private companion object {
        val DelayedFadeIn = EnterCase("fadeIn", fadeIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)))
        val DelayedExpandIn = EnterCase("expandIn", expandIn(tween(TRANSITION_MILLIS, TRANSITION_MILLIS)))

        val WidthKeepingEnters =
            listOf(
                DelayedFadeIn,
                EnterCase("expandVertically", expandVertically(tween(TRANSITION_MILLIS, TRANSITION_MILLIS))),
            )
        val WidthChangingEnters =
            listOf(
                DelayedExpandIn,
                EnterCase("expandHorizontally", expandHorizontally(tween(TRANSITION_MILLIS, TRANSITION_MILLIS))),
            )

        @JvmStatic
        fun stretchingParents(): List<ParentCase> = Stretch.StretchingParents

        @JvmStatic
        fun requestGrantingParents(): List<ParentCase> =
            Stretch.FollowingParents +
                Stretch.NorthSlot +
                Stretch.WestSlot +
                ParentCase(
                    "GridBagLayout fill NONE",
                    { child ->
                        Panel(PanelLayout.Flow()) {
                            Panel(PanelLayout.GridBag, style = { preferredSize(WIDE + NARROW, WIDE) }) {
                                child(SwingModifier.item(fill = GridBagConstraints.NONE))
                            }
                        }
                    },
                    Dimension(320, 180),
                )

        @JvmStatic
        fun wrappingTextEnters(): Stream<Arguments> =
            Stream.of(DelayedExpandIn, WidthChangingEnters.last()).flatMap { enter ->
                Stream.of(FlowLayoutParent, Stretch.WestSlot).map { Arguments.of(enter, it) }
            }

        @JvmStatic
        fun resizes(): List<ResizeCase> = Stretch.ResizeCases

        @JvmStatic
        fun widthKeepingEnters(): List<EnterCase> = WidthKeepingEnters

        @JvmStatic
        fun widthChangingEnters(): List<EnterCase> = WidthChangingEnters

        @JvmStatic
        fun enters(): List<EnterCase> = WidthKeepingEnters + WidthChangingEnters
    }
}

private fun visibility(
    enter: EnterTransition,
    content: TaggedContent,
): EnteringContainer =
    { modifier, shown, contentModifier ->
        AnimatedVisibility(visible = shown, modifier = modifier, enter = enter, exit = ExitTransition.None) {
            content(contentModifier)
        }
    }

/** A viewport narrower than [WIDE] that scrolls both ways. */
private val InViewport: StockParent = { child ->
    Panel(PanelLayout.Flow()) {
        ScrollPane(style = { preferredSize(NARROW, NARROW + NARROW / 4) }) {
            Viewport { child(SwingModifier) }
        }
    }
}
