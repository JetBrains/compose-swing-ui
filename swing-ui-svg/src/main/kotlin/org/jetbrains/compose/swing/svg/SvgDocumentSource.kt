// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SvgKt")

package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.LoaderContext
import com.github.weisj.jsvg.parser.SVGLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.coroutines.CoroutineContext

/**
 * Loads the SVG at [url] off the event dispatch thread, and remembers it for as long as the caller stays
 * composed.
 *
 * Returns `null` until the first document loads. Changing [url], [loaderContext] or [parseContext]
 * reloads the document and keeps the previous result until the replacement loads.
 *
 * A failure loading the current source ends the composition and reaches its exception handler.
 * Load fallible sources outside the composition if you need to recover from errors.
 *
 * @param url where to read the document from.
 * @param loaderContext JSVG loader configuration. `null` uses JSVG's defaults. Keep a custom context
 *   stable across recompositions to avoid reloading.
 * @param parseContext context for reading and parsing. Defaults to [Dispatchers.IO] and must keep
 *   both off the event dispatch thread. Keep a custom context stable across recompositions.
 */
@Composable
public fun rememberSvgDocument(
    url: URL,
    loaderContext: LoaderContext? = null,
    parseContext: CoroutineContext = Dispatchers.IO,
): SVGDocument? {
    // The address is the key, not the URL: URL.equals resolves hosts, which would block this thread on a
    // lookup and treat two hosts that share an address as one source.
    val address = url.toExternalForm()
    val document by produceState<SVGDocument?>(null, address, loaderContext, parseContext) {
        val loaded =
            withContext(parseContext) {
                // SVGLoader is not safe to share between threads, so each load gets its own.
                runCatching {
                    val loader = SVGLoader()
                    if (loaderContext == null) loader.load(url) else loader.load(url, loaderContext)
                }
            }
        // A load replaced while it parsed resumes with its cancellation, so no failure of it reaches
        // the composition.
        value = requireNotNull(loaded.getOrThrow()) { "Not a parseable SVG document: $url" }
    }
    return document
}
