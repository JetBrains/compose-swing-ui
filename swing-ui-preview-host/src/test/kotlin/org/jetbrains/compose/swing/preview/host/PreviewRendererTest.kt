package org.jetbrains.compose.swing.preview.host

import java.awt.image.BufferedImage
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val RGB_MASK = 0xFFFFFF
private const val SAMPLES = "org.jetbrains.compose.swing.preview.host.PreviewSamplesKt"

/**
 * Measures the renderer against previews written exactly as a user writes them, in `PreviewSamples.kt`.
 *
 * A rendering is asserted on rather than merely produced: an image of the right size that is entirely
 * one color is what a composition that applied nothing looks like, so every case that expects content
 * also asserts the pixels are not uniform.
 */
class PreviewRendererTest {
    @Test
    fun `renders a top-level preview at its content's preferred size`() {
        val image = renderOne("$SAMPLES.LabelPreview")

        assertTrue(image.width > 0, "expected a laid-out width, got ${image.width}")
        assertTrue(image.height > 0, "expected a laid-out height, got ${image.height}")
        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `renders at the size the annotation states`() {
        val image = renderOne("$SAMPLES.SizedPreview")

        assertEquals(320, image.width)
        assertEquals(200, image.height)
        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `paints the look and feel's own panel background`() {
        val image = renderOne("$SAMPLES.LabelPreview")

        assertEquals(
            UIManager.getColor("Panel.background").rgb,
            image.getRGB(image.width - 1, image.height - 1),
            "a rendering left transparent is unreadable wherever it is shown",
        )
    }

    @Test
    fun `renders a preview declared in an object`() {
        val image =
            renderOne("org.jetbrains.compose.swing.preview.host.PreviewsInAnObject.MemberPreview")

        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `renders a preview declared in a class, instantiating it`() {
        val image =
            renderOne("org.jetbrains.compose.swing.preview.host.PreviewsInAClass.MemberPreview")

        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `reports a class the preview cannot be instantiated from`() {
        val failure = reasonFor("org.jetbrains.compose.swing.preview.host.PreviewsNeedingAnArgument.MemberPreview")

        assertTrue(
            failure.contains("constructor that takes no arguments"),
            "expected the message to name what the class is missing, got: $failure",
        )
    }

    @Test
    fun `installs the look and feel the annotation states`() {
        renderOne("$SAMPLES.MetalPreview")

        assertEquals("javax.swing.plaf.metal.MetalLookAndFeel", UIManager.getLookAndFeel().javaClass.name)
    }

    @Test
    fun `renders a preview that never settles rather than failing on it`() {
        val image = renderOne("$SAMPLES.NeverSettlingPreview")

        assertEquals(120, image.width)
        assertEquals(40, image.height)
        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `renders a repeated annotation once per occurrence, in the order the source states them`() {
        val rendered = renderedFrom(listOf("$SAMPLES.RepeatedPreview"))

        assertEquals(listOf("Narrow", "Wide"), rendered.map { it.name })
        assertEquals(120, rendered[0].image.width)
        assertEquals(400, rendered[1].image.width)
    }

    @Test
    fun `renders every occurrence an annotation class stands for`() {
        val rendered = renderedFrom(listOf("$SAMPLES.MultiPreview"))

        assertEquals(listOf("Metal", "Nimbus"), rendered.map { it.name })
        assertTrue(rendered.all { it.image.hasVaryingPixels() }, "a rendering is a single flat color")
    }

    @Test
    fun `renders through annotation classes that carry other annotation classes`() {
        val rendered = renderedFrom(listOf("$SAMPLES.NestedMultiPreview"))

        assertEquals(listOf("Metal", "Nimbus", "Sized"), rendered.map { it.name })
        assertEquals(200, rendered[2].image.width)
    }

    @Test
    fun `terminates on annotation classes that carry each other`() {
        val rendered = renderedFrom(listOf("$SAMPLES.CyclicMultiPreview"))

        assertEquals(listOf("Cyclic"), rendered.map { it.name })
    }

    @Test
    fun `a look and feel one rendering asks for does not carry into the next`() {
        val rendered = renderedFrom(listOf("$SAMPLES.NestedMultiPreview"))

        // "Sized" states no look and feel, so it is rendered under what the environment prepared
        // rather than under the Nimbus its predecessor asked for.
        assertEquals(3, rendered.size)
        assertEquals(NimbusTestEnvironment.NIMBUS, UIManager.getLookAndFeel().javaClass.name)
    }

    @Test
    fun `reports a preview that emits nothing, naming the parameters that would render it anyway`() {
        val failure = reasonFor("$SAMPLES.EmptyPreview")

        assertTrue(
            failure.contains("widthPx and heightPx"),
            "expected the message to name the parameters that fix it, got: $failure",
        )
    }

    @Test
    fun `reports a composable that is not annotated`() {
        val failure = reasonFor("$SAMPLES.UnannotatedPreview")

        assertTrue(
            failure.contains("asks for no rendering"),
            "expected the message to say nothing asked for a rendering, got: $failure",
        )
    }

    @Test
    fun `reports a missing class, naming the file facade convention`() {
        val failure = reasonFor("com.example.Absent.somePreview")

        assertTrue(
            failure.contains("ends in 'Kt'"),
            "expected the message to explain how a top-level function is named, got: $failure",
        )
    }

    @Test
    fun `reports a missing method, naming the no-parameter requirement`() {
        val failure = reasonFor("$SAMPLES.AbsentPreview")

        assertTrue(
            failure.contains("cannot take parameters"),
            "expected the message to state the requirement, got: $failure",
        )
    }

    @Test
    fun `reports a look and feel that cannot be installed`() {
        val failure = reasonFor("$SAMPLES.AbsentLookAndFeelPreview")

        assertTrue(
            failure.contains("cannot be installed"),
            "expected the message to name the look and feel as the cause, got: $failure",
        )
    }

    @Test
    fun `renders a preview that is private, which is what a preview usually is`() {
        val renderings = renderPreviews(listOf("$SAMPLES.PrivatePreview"))
        assertTrue(renderings.failed.isEmpty(), "lost it: ${renderings.failed.map { it.reason }}")
        val image = renderings.rendered.single().image

        assertEquals(120, image.width)
        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `renders under a theme the classpath supplies rather than the JDK`() {
        val image = renderOne("$SAMPLES.ThemedPreview")

        // The renderer paints a preview on the background the installed look and feel gives a panel,
        // so this pixel is the theme's own file reaching the composition.
        assertEquals(SampleTheme.BACKGROUND, image.getRGB(0, 0) and RGB_MASK)
        assertTrue(image.hasVaryingPixels(), "the rendering is a single flat color, so nothing was drawn")
    }

    @Test
    fun `a composable that cannot be rendered costs the others nothing`() {
        val renderings =
            renderPreviews(listOf("$SAMPLES.EmptyPreview", "$SAMPLES.LabelPreview", "$SAMPLES.UnannotatedPreview"))

        assertEquals(listOf("$SAMPLES.LabelPreview"), renderings.rendered.map { it.fqName })
        assertEquals(
            listOf("$SAMPLES.EmptyPreview", "$SAMPLES.UnannotatedPreview"),
            renderings.failed.map { it.fqName },
        )
    }

    @Test
    fun `a rendering lost to its own look and feel leaves the others of the same composable`() {
        val renderings = renderPreviews(listOf("$SAMPLES.MultiPreview", "$SAMPLES.AbsentLookAndFeelPreview"))

        assertEquals(listOf("Metal", "Nimbus"), renderings.rendered.map { it.name })
        assertEquals(listOf("$SAMPLES.AbsentLookAndFeelPreview"), renderings.failed.map { it.fqName })
    }

    @Test
    fun `a reason is one line, so it survives a tab-separated file`() {
        val reason = reasonFor("$SAMPLES.EmptyPreview")

        assertFalse(reason.contains('\n') || reason.contains('\t'), "got: $reason")
    }

    /** Every rendering the named composables produced, asserting none of them failed. */
    private fun renderedFrom(fqNames: List<String>): List<RenderedPreview> {
        val renderings = renderPreviews(fqNames)

        assertTrue(renderings.failed.isEmpty(), "expected every rendering, lost ${renderings.failed.map { it.reason }}")
        return renderings.rendered
    }

    /** Renders a composable that asks for exactly one rendering. */
    private fun renderOne(fqName: String): BufferedImage = renderPreviews(listOf(fqName)).rendered.single().image

    /** The one reason [fqName] produced no rendering. */
    private fun reasonFor(fqName: String): String {
        val renderings = renderPreviews(listOf(fqName))

        assertTrue(
            renderings.rendered.isEmpty(),
            "expected no rendering, got ${renderings.rendered.size}",
        )
        return assertNotNull(
            renderings.failed.singleOrNull(),
            "expected exactly one reason, got ${renderings.failed.map { it.reason }}",
        ).reason
    }

    private fun BufferedImage.hasVaryingPixels(): Boolean {
        val first = getRGB(0, 0)
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (getRGB(x, y) != first) return true
            }
        }
        return false
    }
}
