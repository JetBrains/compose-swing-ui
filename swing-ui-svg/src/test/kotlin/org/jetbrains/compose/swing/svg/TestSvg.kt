// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import java.awt.image.BufferedImage
import java.net.URL
import kotlin.test.assertNotNull

/** SVG fixtures loaded independently for each test. Whole-number dimensions differ from the harness defaults. */
internal enum class TestSvg(
    private val fileName: String,
) {
    Static("static.svg"),
    CurrentColor("current-color.svg"),
    Finite("finite.svg"),
    LongFinite("long-finite.svg"),
    Indefinite("indefinite.svg"),
    Curved("curved.svg"),
    Fractional("fractional.svg"),
    VisibleSlice("visible-slice.svg"),
    Wide("wide.svg"),
    Repeating("repeating.svg"),
    ;

    val url: URL get() = assertNotNull(TestSvg::class.java.getResource("/svg/$fileName"), "Missing fixture $fileName")

    fun load(): SVGDocument = assertNotNull(SVGLoader().load(url), "Fixture $fileName did not parse")
}

/** Counts the pixels of [image] that are painted at all. */
internal fun drawnPixels(image: BufferedImage): Int =
    (0 until image.width).sumOf { x -> (0 until image.height).count { y -> (image.getRGB(x, y) ushr 24) != 0 } }
