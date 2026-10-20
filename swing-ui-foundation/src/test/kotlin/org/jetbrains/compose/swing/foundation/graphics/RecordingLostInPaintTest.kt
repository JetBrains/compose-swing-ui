package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.awt.Component
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A recording made in a paint and lost before the paint is done leaves the frame without it, and the component asks
 * for the repaint that records it again.
 */
class RecordingLostInPaintTest {
    @ParameterizedTest
    @EnumSource(LostPath::class)
    fun aRecordingLostAsItIsDrawnRepaintsAndIsRecordedAgain(path: LostPath) =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(path, screen)
            fixture.lose()

            val asked = RecordedRepaints()
            withRecordedRepaints(asked) { fixture.paintOn() }
            asked.assertAskedToRepaint(fixture.subject, "A recording lost as its paint drew it")

            fixture.content.losing = false
            fixture.step?.afterRecording = null
            val paints = fixture.content.paints
            val next = RecordedRepaints()
            val recorded = withRecordedRepaints(next) { fixture.paintOn() }
            assertEquals(1, fixture.content.paints - paints, "the paint after the loss records the content again")
            recorded.assertShowsContent("the paint after the loss")

            val unrequested = RecordedRepaints()
            val reused = withRecordedRepaints(unrequested) { fixture.paintOn() }
            assertEquals(1, fixture.content.paints - paints, "the new recording is drawn without painting the content")
            assertEquals(0, unrequested.repaintsOf(fixture.subject), "a recording not lost asks for no repaint")
            reused.assertShowsContent("the paint that reuses the new recording")
        }

    @Test
    fun aRecordingLeftEmptyOnPurposeAsksForNoRepaint() =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(LostPath.StepWhileDrawn, screen)
            checkNotNull(fixture.step).collapses = true

            val asked = RecordedRepaints()
            withRecordedRepaints(asked) { repeat(2) { fixture.paintOn() } }

            assertEquals(0, asked.repaintsOf(fixture.subject), "a recording that holds nothing was never lost")
        }

    @ParameterizedTest
    @EnumSource(LostPath::class, names = ["Shadow", "FadeAtConstantAlpha", "StepWhileDrawn"])
    fun aRecordingLostWhileItsContentPaintsRepaintsAndIsRecordedAgain(path: LostPath) =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(path, screen)
            fixture.content.losesWhilePainting = true

            val asked = RecordedRepaints()
            withRecordedRepaints(asked) { fixture.paintOn() }
            asked.assertAskedToRepaint(fixture.subject, "A recording lost as its content painted")

            fixture.content.losesWhilePainting = false
            val paints = fixture.content.paints
            val recorded = fixture.paintOn()
            assertEquals(1, fixture.content.paints - paints, "the paint after the loss records the content again")
            recorded.assertShowsContent("the paint after the loss")
        }

    @Test
    fun aRecordingLostAfterItsDrawRepaintsWhenTheContentPaintsOverIt() =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(LostPath.StepWhileDrawn, screen)
            val step = checkNotNull(fixture.step)
            step.paintsContentAfterDraw = true
            step.afterDraw = { fixture.content.losesWhilePainting = true }

            val asked = RecordedRepaints()
            withRecordedRepaints(asked) { fixture.paintOn() }
            asked.assertAskedToRepaint(
                fixture.subject,
                "A recording lost after its draw, as the content painted over it",
            )
        }

    @Test
    fun aRecordingLostAsItIsDrawnRepaintsWhenThePaintThrowsAfterwards() =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(LostPath.StepWhileDrawn, screen)
            fixture.lose()
            checkNotNull(fixture.step).throwsAfterDraw = true

            val asked = RecordedRepaints()
            val thrown =
                assertFailsWith<IllegalStateException> {
                    withRecordedRepaints(asked) { fixture.paintOn() }
                }
            assertEquals("the step's paint fails after its draw", thrown.message)
            asked.assertAskedToRepaint(fixture.subject, "A recording lost as its paint drew it, before the paint threw")
        }

    enum class LostPath {
        Shadow,
        FadeAtConstantAlpha,
        StepWhileDrawn,
        StepBeforeTheDraw,
    }
}

private class LostContent {
    var paints = 0

    /** Whether the surfaces made so far are lost as the next draw copies them. */
    var losing = false

    /** Whether the surfaces made so far are lost while the content paints. */
    var losesWhilePainting = false

    var surfaces: () -> Collection<LosableSurface> = { emptyList() }

    val draw: DrawScope.() -> Unit = {
        paints++
        graphics.color = Color.BLUE
        graphics.fillRect(0, 0, width, height)
        if (losesWhilePainting) surfaces().forEach { it.lost = true }
        if (losing) surfaces().forEach { it.losesWhenDrawn = true }
    }
}

/** Records its content, then draws it, as a third-party step does. */
private class RecordingNode : DecorationModifierNode<Component>() {
    private val layer = obtainImageLayer()
    var afterRecording: (() -> Unit)? = null

    /** Whether the recording is left empty on purpose. */
    var collapses = false

    /** Whether the paint throws once it has drawn the layer. */
    var throwsAfterDraw = false

    /** Whether the paint paints the content again once it has drawn the layer. */
    var paintsContentAfterDraw = false

    /** Runs once the layer is drawn, before the content paints again when [paintsContentAfterDraw] is set. */
    var afterDraw: (() -> Unit)? = null

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val area = graphics.clipBounds
        if (layer.drawCached(graphics, area)) return
        layer.recordCached(graphics, area) { recording ->
            if (collapses) {
                // An area with no size leaves the layer empty without running the block.
                recording.record(graphics, 0, 0) {}
            } else {
                recording.record(graphics, width, height) { content(it, width, height) }
            }
        }
        afterRecording?.invoke()
        layer.draw(graphics)
        afterDraw?.invoke()
        if (paintsContentAfterDraw) content(graphics, width, height)
        check(!throwsAfterDraw) { "the step's paint fails after its draw" }
    }
}

private class LostFixture(
    val subject: JComponent,
    val content: LostContent,
    val step: RecordingNode?,
    private val screen: FakeScreen,
    private val path: RecordingLostInPaintTest.LostPath,
) {
    /** Has the next paint lose the recording it makes. */
    fun lose() {
        if (path == RecordingLostInPaintTest.LostPath.StepBeforeTheDraw) {
            checkNotNull(step).afterRecording = { screen.surfaces.forEach { it.lost = true } }
        } else {
            content.losing = true
        }
    }

    fun paintOn(): BufferedImage {
        val surface = screen.surface(subject.width, subject.height)
        try {
            subject.paint(surface.graphics)
        } finally {
            surface.graphics.dispose()
        }
        return surface.pixels
    }
}

private fun BufferedImage.assertShowsContent(what: String) {
    val shown = Color(getRGB(width / 2, height / 2), true)
    assertTrue(shown.alpha > 0 && shown.blue > 200 && shown.red < 50, "$what shows $shown, not the content")
}

private fun ComposeSwingTest.setUpFixture(
    path: RecordingLostInPaintTest.LostPath,
    screen: FakeScreen,
): LostFixture {
    val content = LostContent().also { it.surfaces = { screen.surfaces } }
    val step = TestStep { RecordingNode() }
    setContent {
        Row {
            when (path) {
                RecordingLostInPaintTest.LostPath.Shadow -> {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier = shadowed().testTag("subject").drawBehind(content.draw),
                    )
                }

                RecordingLostInPaintTest.LostPath.FadeAtConstantAlpha -> {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("subject")
                                .preferredSize(160, 100)
                                .placementLayer { alpha = 0.5f }
                                .drawBehind(content.draw),
                    )
                }

                RecordingLostInPaintTest.LostPath.StepWhileDrawn,
                RecordingLostInPaintTest.LostPath.StepBeforeTheDraw,
                -> {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("subject")
                                .preferredSize(160, 100)
                                .opaque(false)
                                .then(step)
                                .drawBehind(content.draw),
                    )
                }
            }
        }
    }
    return LostFixture(
        onNodeWithTag("subject").fetch<JComponent>(),
        content,
        step.created.singleOrNull(),
        screen,
        path,
    )
}

context(scope: ConstrainedScope)
private fun shadowed(): SwingModifier = SwingModifier.shadow(10, Color(0, 0, 0, 150), 2, 4).size(160, 100)
