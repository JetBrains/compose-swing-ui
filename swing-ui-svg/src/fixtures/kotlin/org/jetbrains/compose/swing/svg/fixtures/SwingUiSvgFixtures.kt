// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg.fixtures

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.SVGLoader
import org.jetbrains.compose.swing.fixtures.ComposableFixture

internal val staticSvgDocument: SVGDocument = loadCurvedDocument()

private fun loadCurvedDocument(): SVGDocument {
    val url =
        checkNotNull(SvgImageFixture::class.java.getResource("/svg/curved.svg")) { "Missing SVG fixture: curved.svg" }
    return checkNotNull(SVGLoader().load(url)) { "Could not parse $url" }
}

/** SVG fixtures shared by the timing and memory suites. */
public val swingUiSvgFixtures: List<ComposableFixture<*, *>> =
    listOf(SvgImageFixture, SvgIconFixture)
