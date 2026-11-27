// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg.fixtures

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.svg.SvgImage
import org.jetbrains.compose.swing.svg.SvgSurface

/** The antialiasing setting for [SvgImageFixture]. */
internal data class SvgImageParameters(
    val antialias: Boolean,
)

/** A curved static [SvgImage] and its Swing equivalent, with and without antialiasing. */
internal object SvgImageFixture : ComposableFixture<SvgImageParameters, SvgSurface>(
    initial = SvgImageParameters(antialias = true),
) {
    override val alternatives: List<SvgImageParameters> = listOf(initial.copy(antialias = false))

    @Composable
    override fun Content(parameters: SvgImageParameters): Unit = Content(parameters.antialias)

    /** Draws the static document with the given [antialias] setting. */
    @Composable
    private fun Content(antialias: Boolean) {
        SvgImage(document = staticSvgDocument, antialias = antialias)
    }

    override fun createRawSwing(): SvgSurface = SvgSurface(staticSvgDocument)

    override fun updateRawSwing(
        component: SvgSurface,
        parameters: SvgImageParameters,
    ) {
        component.antialias = parameters.antialias
    }
}
