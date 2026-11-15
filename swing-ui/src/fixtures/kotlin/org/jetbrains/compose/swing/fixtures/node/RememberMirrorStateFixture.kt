// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.node

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import javax.swing.JLabel

/** The parameters [RememberMirrorStateFixture] varies: the text the label is declared to hold. */
public data class RememberMirrorStateParameters(
    val text: String,
)

/** A [rememberMirrorState] record settling a declared label text, against a raw [JLabel] showing the same text. */
public object RememberMirrorStateFixture : ComposableFixture<RememberMirrorStateParameters, JLabel>(
    initial = RememberMirrorStateParameters(text = "Before"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<RememberMirrorStateParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(text = ""),
        )

    @Composable
    override fun Content(parameters: RememberMirrorStateParameters): Unit = Content(parameters.text)

    /**
     * Composes a [JLabel] node whose text is declared as [text] through one record, seeded with the empty text a new
     * [JLabel] holds.
     */
    @Composable
    public fun Content(text: String) {
        val mirror = rememberMirrorState("")
        SwingNode(factory = { JLabel() }, update = {
            declare(text, mirror, JLabel::getText, JLabel::setText)
        })
    }

    /** Builds an empty [JLabel], before any of the fixture's values are applied. */
    override fun createRawSwing(): JLabel = JLabel()

    /** Sets the text of [component] to the text of [parameters]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: RememberMirrorStateParameters,
    ) {
        component.text = parameters.text
    }
}
