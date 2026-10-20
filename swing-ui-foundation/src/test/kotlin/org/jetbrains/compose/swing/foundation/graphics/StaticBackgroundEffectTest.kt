package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.foundation.Canvas as FoundationCanvas

/** A shadow or blur on a background keeps its image until a content change is reported. */
class StaticBackgroundEffectTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("effectsHostsAndSequences")
    fun aPaintShowsTheCurrentContentAndPaintsItOnlyAfterAChange(
        effect: BackgroundEffect,
        host: EffectHost,
        sequence: EffectSequence,
    ) = runComposeSwingTest {
        val fixture = setUpFixture(effect, host)
        fixture.paint()
        sequence.events(fixture)
        val paints = fixture.probe.paints
        val view = sequence.view(Rectangle(fixture.subject.size), fixture.content)
        val frame = fixture.paint(view)

        fixture.probe.assertShownBy(frame, fixture.content.intersection(view).apply { translate(-view.x, -view.y) })
        if (sequence.reuses) assertEquals(paints, fixture.probe.paints, "A paint nobody requested painted the content.")
    }

    // Swing repaints a text area that is not opaque from its opaque ancestor, here the stack holding both.
    @ParameterizedTest(name = "{0}")
    @EnumSource(BackgroundEffect::class)
    fun editsOverTheBackgroundReuseTheEffect(effect: BackgroundEffect) =
        runComposeSwingTest {
            var text by mutableStateOf("one")
            var contentPaints = 0
            // One instance, so the text changing recomposes nothing the background declares.
            val countPaints: DrawScope.() -> Unit = { contentPaints++ }
            setContent {
                Row {
                    Box(SwingModifier.testTag("stack").padding(30)) {
                        Box(SwingModifier.effectBackground(effect).drawBehind(countPaints))
                        TextArea(
                            text,
                            {},
                            SwingModifier.testTag("editor").opaque(false).align(Alignment.Center),
                            rows = 2,
                            columns = 10,
                        )
                    }
                }
            }
            val stack = onNodeWithTag("stack").fetch<JComponent>()
            val editor = onNodeWithTag("editor").fetch<JTextArea>()
            val before = stack.paintOnto(stack.width, stack.height)
            val painted = contentPaints
            assertTrue(painted > 0, "The first paint paints the background's content.")

            val caret = editor.modelToView2D(editor.caretPosition).bounds.apply { width = 2 }
            repeat(3) { stack.paintOnto(stack.width, stack.height, clip = editor.areaIn(stack, caret)) }
            text = "two"
            awaitIdle()
            val edited = editor.areaIn(stack, Rectangle(editor.size))
            val levelsOff = stack.levelsOffAWholeRepaint(before, listOf(edited))

            assertEquals(
                painted,
                contentPaints,
                "A caret blinking and text changing over a static ${effect.name} background paint its content again.",
            )
            assertTrue(levelsOff <= 1, "Repainting the text area alone is $levelsOff levels off the whole stack.")
        }

    // A recording discarded once drawn counts as drawn: that paint asks for a repaint, whose paint records again.
    @ParameterizedTest(name = "{0}")
    @MethodSource("lostSurfaceRows")
    fun aDiscardedRecordingIsMadeAgainOnce(
        step: LostSurfaceStep,
        loss: SurfaceLoss,
    ) = runComposeSwingTest {
        val screen = FakeScreen(1.0, losable = true)
        val fixture = setUpLostSurfaceFixture(step)
        fixture.paintOn(screen)
        assertTrue(screen.surfaces.isNotEmpty(), "the ${step.name} kept no recording on a discardable surface")

        if (loss == SurfaceLoss.WithAContentChange) {
            fixture.probe.version++
            fixture.subject.repaint()
        }
        if (step == LostSurfaceStep.Fade) {
            fixture.opacity.floatValue = 0.5f
            awaitIdle()
        }
        screen.surfaces.forEach {
            if (loss.whenDrawn) it.losesWhenDrawn = true else it.lost = true
        }
        val paintsBefore = fixture.probe.paints
        val repaints = RecordedRepaints()

        val frame = withRecordedRepaints(repaints) { fixture.paintOn(screen) }

        val paintsOfTheFrame = fixture.probe.paints - paintsBefore
        if (loss.whenDrawn) {
            val recorded = fixture.paintOn(screen)
            val paintsOfTheRecording = fixture.probe.paints - paintsBefore - paintsOfTheFrame
            fixture.subject.assertKeptShowsWhatFreshDoes(frame) { fixture.paintOn(screen) }
            fixture.probe.assertShownBy(frame, fixture.content, fixture.opacity.floatValue)
            assertEquals(0, paintsOfTheFrame, "a recording discarded once it was drawn counts as drawn")
            repaints.assertAskedToRepaint(fixture.subject, "A recording discarded once it was drawn")
            assertEquals(1, paintsOfTheRecording, "the repaint it asks for makes the recording again once")
            fixture.subject.assertKeptShowsWhatFreshDoes(recorded) { fixture.paintOn(screen) }
            return@runComposeSwingTest
        }
        fixture.probe.assertShownBy(frame, fixture.content, fixture.opacity.floatValue)
        assertEquals(1, paintsOfTheFrame, "a discarded recording is made again once")
        if (loss.thenUnrequested) {
            val paintsAfterTheRecording = fixture.probe.paints
            fixture.probe.assertShownBy(fixture.paintOn(screen), fixture.content, fixture.opacity.floatValue)
            assertEquals(paintsAfterTheRecording, fixture.probe.paints, "the new recording is drawn without painting")
        }
    }

    enum class EffectHost {
        Box,
        Canvas,
    }

    /** A sequence that [reuses] reports no change to the content. */
    enum class EffectSequence(
        internal val reuses: Boolean = false,
        internal val view: (component: Rectangle, content: Rectangle) -> Rectangle = { component, _ -> component },
        internal val events: (EffectFixture) -> Unit = {},
    ) {
        Repaint(events = { it.changeContent { repaint() } }),
        PaintImmediately(events = { it.changeContent { paintImmediately(Rectangle(size)) } }),
        TakingGraphics(events = { it.changeContent { graphics?.dispose() } }),
        RepaintDuringPaint(events = { it.changeContentWhilePainting { repaint() } }),
        TakingGraphicsDuringPaint(events = { it.changeContentWhilePainting { graphics?.dispose() } }),
        UnrequestedPaint(reuses = true),
        WholePixelShift(reuses = true, view = { component, _ -> Rectangle(-5, -3, component.width, component.height) }),
        PartOfTheContent(reuses = true, view = { _, content -> Rectangle(content.x + 70, content.y + 40, 50, 30) }),
    }

    // A blur draws its own filtered image, never a volatile recording.
    enum class LostSurfaceStep {
        Shadow,
        Fade,
    }

    /** The device discards the recording once the next paint copied its pixels where [whenDrawn], else before it. */
    enum class SurfaceLoss(
        internal val whenDrawn: Boolean = false,
        internal val thenUnrequested: Boolean = false,
    ) {
        Alone,
        WithAContentChange,
        ThenAnUnrequestedPaint(thenUnrequested = true),
        WhileTheRecordingIsDrawn(whenDrawn = true),
    }

    private companion object {
        @JvmStatic
        fun effectsHostsAndSequences(): Stream<Arguments> =
            BackgroundEffect.entries
                .flatMap { effect ->
                    EffectHost.entries.flatMap { host ->
                        EffectSequence.entries.map { Arguments.of(Named.of("$effect $host $it", effect), host, it) }
                    }
                }.stream()

        @JvmStatic
        fun lostSurfaceRows(): Stream<Arguments> =
            LostSurfaceStep.entries
                .flatMap { step ->
                    SurfaceLoss.entries.map { loss -> Arguments.of(Named.of("$step $loss", step), loss) }
                }.stream()

        fun JComponent.areaIn(
            ancestor: JComponent,
            area: Rectangle,
        ): Rectangle = SwingUtilities.convertRectangle(this, area, ancestor)
    }
}

/** The content of a fixture: a test bumps [version] with each change it makes to the content. */
internal class Probe {
    var version = 0
    var paints = 0

    /** Changes the content once, in the next draw, after that draw filled the box with the version it replaces. */
    var signalInNextDraw: (() -> Unit)? = null

    private val colors = listOf(Color.RED, Color.BLUE)

    val draw: DrawScope.() -> Unit = {
        paints++
        graphics.color = colors[version]
        graphics.fillRect(0, 0, width, height)
        signalInNextDraw?.let { signal ->
            signalInNextDraw = null
            version++
            signal()
        }
    }

    /** The color is read just inside the corners, because an effect may soften them. */
    fun assertShownBy(
        frame: BufferedImage,
        box: Rectangle,
        opacity: Float = 1f,
    ) {
        val color = colors[version]
        val inside = Rectangle(box).apply { grow(-2, -2) }
        val center = Point(inside.centerX.toInt(), inside.centerY.toInt())
        listOf(inside.location, center, Point(inside.x + inside.width - 1, inside.y + inside.height - 1)).forEach {
            val pixel = Color(frame.getRGB(it.x, it.y), true)
            assertTrue(
                pixel.alpha > 0 &&
                    abs(pixel.red - color.red) <= 2 &&
                    abs(pixel.green - color.green) <= 2 &&
                    abs(pixel.blue - color.blue) <= 2,
                "At $it the frame shows $pixel, not the content's version $version.",
            )
        }
        val alpha = Color(frame.getRGB(center.x, center.y), true).alpha
        assertTrue(abs(alpha - 255 * opacity) <= 2, "The frame shows the content at alpha $alpha, not $opacity.")
    }
}

internal class EffectFixture(
    val subject: JComponent,
    val probe: Probe,
    val opacity: MutableFloatState = mutableFloatStateOf(1f),
) {
    /** The subject's box without the effect's paint outsets, where the content paints. */
    val content: Rectangle
        get() = (subject as Decoratable).decoration.localLayoutBounds(subject)

    fun changeContent(signal: JComponent.() -> Unit) {
        probe.version++
        subject.signal()
    }

    fun changeContentWhilePainting(signal: JComponent.() -> Unit) {
        probe.signalInNextDraw = { subject.signal() }
        subject.repaint()
        paint()
    }

    fun paint(view: Rectangle = Rectangle(subject.size)): BufferedImage =
        subject.paintOnto(
            view.width,
            view.height,
            AffineTransform.getTranslateInstance(-view.x.toDouble(), -view.y.toDouble()),
            view.intersection(Rectangle(subject.size)),
        )

    fun paintOn(screen: FakeScreen): BufferedImage {
        val surface = screen.surface(subject.width, subject.height)
        try {
            subject.paint(surface.graphics)
        } finally {
            surface.graphics.dispose()
        }
        return surface.pixels
    }
}

private fun ComposeSwingTest.setUpFixture(
    effect: BackgroundEffect,
    host: StaticBackgroundEffectTest.EffectHost,
): EffectFixture {
    val probe = Probe()
    setContent {
        Row {
            val subject = SwingModifier.effectBackground(effect).testTag("subject")
            when (host) {
                StaticBackgroundEffectTest.EffectHost.Box -> Box(subject.drawBehind(probe.draw))
                StaticBackgroundEffectTest.EffectHost.Canvas -> FoundationCanvas(subject, onDraw = probe.draw)
            }
        }
    }
    return EffectFixture(onNodeWithTag("subject").fetch<JComponent>(), probe)
}

private fun ComposeSwingTest.setUpLostSurfaceFixture(step: StaticBackgroundEffectTest.LostSurfaceStep): EffectFixture {
    val probe = Probe()
    // Below 1, so the fade keeps a recording from the first paint.
    val opacity = mutableFloatStateOf(if (step == StaticBackgroundEffectTest.LostSurfaceStep.Fade) 0.8f else 1f)
    setContent {
        Row {
            when (step) {
                StaticBackgroundEffectTest.LostSurfaceStep.Shadow -> {
                    val subject = SwingModifier.effectBackground(BackgroundEffect.Shadow)
                    Box(subject.testTag("subject").drawBehind(probe.draw))
                }

                StaticBackgroundEffectTest.LostSurfaceStep.Fade -> {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("subject")
                                .preferredSize(160, 100)
                                .placementLayer { alpha = opacity.floatValue }
                                .drawBehind(probe.draw),
                    )
                }
            }
        }
    }
    return EffectFixture(onNodeWithTag("subject").fetch<JComponent>(), probe, opacity)
}

enum class BackgroundEffect { Shadow, Blur }

context(scope: ConstrainedScope)
private fun SwingModifier.effectBackground(effect: BackgroundEffect): SwingModifier =
    when (effect) {
        BackgroundEffect.Shadow -> shadow(10, Color(0, 0, 0, 150), 2, 4)
        BackgroundEffect.Blur -> blur(10)
    }.size(160, 100).background(Color.WHITE, RoundedCornerShape(24f))
