// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.tooling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import org.jetbrains.compose.swing.components.Label
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A set of renderings carried by an annotation class, which is the second place [Preview] may sit. */
@Preview(name = "Light", lookAndFeel = "javax.swing.plaf.metal.MetalLookAndFeel")
annotation class PreviewThemesFixture

/**
 * Pins the contract a renderer reads [Preview] through. Everything a rendering is configured by is read
 * reflectively off a compiled class, so the retention, the repetition, the two places the annotation may
 * sit and each default are the public shape here - none of which the ABI dump records.
 *
 * The annotation is read the way a renderer reads it, through plain JDK reflection: nothing here composes
 * or renders, which is the separate artifact's to do.
 */
class PreviewTest {
    @Test
    fun theAnnotationIsReadableAtRuntime() {
        val retention = Preview::class.java.getAnnotation(Retention::class.java)
        assertNotNull(retention, "a renderer reads Preview off a loaded class, so it must survive to runtime")
        assertEquals(
            RetentionPolicy.RUNTIME,
            retention.value,
            "a renderer reads Preview off a loaded class, so it must survive to runtime",
        )
    }

    @Test
    fun aComposableCarriesEveryRenderingItDeclaresInOrder() {
        val method = previewMethod("RepeatedPreview")

        val renderings = method.getAnnotationsByType(Preview::class.java)

        assertEquals(
            listOf("first", "second"),
            renderings.map { it.name },
            "a repeated Preview must be readable as one occurrence per rendering, in declaration order",
        )
    }

    @Test
    fun anAnnotationClassCarriesTheRenderingsItStandsFor() {
        val carried = PreviewThemesFixture::class.java.getAnnotation(Preview::class.java)

        assertNotNull(
            carried,
            "Preview must be readable off an annotation class, which is how sets of renderings compose",
        )
        assertEquals("Light", carried.name, "the annotation class must carry the rendering it was declared with")
    }

    @Test
    fun aRenderingThatStatesNothingReportsEveryDefault() {
        val stated = previewMethod("DefaultPreview").getAnnotation(Preview::class.java)

        assertNotNull(stated, "the fixture carries a Preview")
        assertEquals("", stated.name, "an unnamed rendering is labeled by whatever renders it")
        assertEquals(-1, stated.width, "an unsized rendering lays the content out at its own preferred width")
        assertEquals(-1, stated.height, "an unsized rendering lays the content out at the height it takes")
        assertEquals("", stated.lookAndFeel, "a rendering that names none keeps what the environment installed")
    }
}

@Preview
@Composable
internal fun DefaultPreview() = Label("Default")

@Preview(name = "first")
@Preview(name = "second")
@Composable
internal fun RepeatedPreview() = Label("Repeated")

private fun previewMethod(name: String) = Class
    .forName("${PreviewTest::class.java.name}Kt")
    .getDeclaredMethod(name, Composer::class.java, Int::class.java)
