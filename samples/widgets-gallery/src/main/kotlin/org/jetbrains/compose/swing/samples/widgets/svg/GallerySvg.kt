// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.weisj.jsvg.SVGDocument
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.svg.rememberSvgDocument

/**
 * Reads one of the gallery's own SVG resources and composes [content] once the document is loaded. Loading
 * runs off the event dispatch thread, so a "Loading" label is shown until the document is loaded.
 *
 * @param name the file name, relative to the gallery's `/svg/` resource folder
 * @param content composed with the parsed document once it is loaded
 */
@Composable
internal fun WithGallerySvg(
    name: String,
    content: @Composable (SVGDocument) -> Unit,
) {
    val url = remember(name) { requireNotNull(SvgResources.javaClass.getResource("/svg/$name")) }
    val document = rememberSvgDocument(url)
    if (document == null) {
        Label("Loading $name...")
    } else {
        content(document)
    }
}

// Names the class loader the SVG resources are read through.
private object SvgResources
