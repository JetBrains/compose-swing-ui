package org.jetbrains.compose.swing.preview.host

import org.jetbrains.compose.swing.tooling.PreviewEnvironment
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val SAMPLES = "org.jetbrains.compose.swing.preview.host.PreviewSamplesKt"
private const val METAL = "javax.swing.plaf.metal.MetalLookAndFeel"

/**
 * Measures what a `META-INF/services` entry on the preview classpath does to a rendering, and what
 * happens when the classpath offers more than one.
 *
 * This module registers [NimbusTestEnvironment] the way a project registers its own, so the discovery
 * these cases assert on is the real one rather than a list handed to the loader.
 */
class PreviewEnvironmentTest {
    @Test
    fun `the classpath's own environment is discovered`() {
        val environments = loadEnvironments()

        assertEquals(1, environments.size, "found: ${environments.map { it.javaClass.name }}")
        assertTrue(environments.single() is NimbusTestEnvironment)
    }

    @Test
    fun `the environment prepares the process a preview renders in`() {
        renderPreviews(listOf("$SAMPLES.LabelPreview"))

        assertEquals(NimbusTestEnvironment.NIMBUS, UIManager.getLookAndFeel().javaClass.name)
    }

    @Test
    fun `a preview that states a look and feel overrides the environment`() {
        renderPreviews(listOf("$SAMPLES.MetalPreview"))

        assertEquals(METAL, UIManager.getLookAndFeel().javaClass.name)
    }

    @Test
    fun `a second environment is an error rather than a winner`() {
        val failure = assertFails { prepare(listOf(NimbusTestEnvironment(), NimbusTestEnvironment())) }

        assertTrue(
            failure.message.orEmpty().contains("Leave one on the classpath"),
            "expected the message to say what to do about it, got: ${failure.message}",
        )
    }

    @Test
    fun `an environment that throws is reported in place of the rendering`() {
        val failure = assertFails { prepare(listOf(FailingEnvironment())) }

        assertTrue(
            failure.message.orEmpty().contains("failed to prepare"),
            "expected the message to name the environment as the cause, got: ${failure.message}",
        )
        assertEquals("no look and feel here", failure.cause?.message)
    }

    @Test
    fun `no environment is not an error`() {
        prepare(emptyList())
    }

    private class FailingEnvironment : PreviewEnvironment {
        override fun prepare(): Nothing = error("no look and feel here")
    }

    private fun assertFails(prepare: () -> Unit): PreviewFailure {
        val failure =
            try {
                prepare()
                null
            } catch (expected: PreviewFailure) {
                expected
            }
        return assertNotNull(failure, "expected preparation to fail, but it succeeded")
    }
}
