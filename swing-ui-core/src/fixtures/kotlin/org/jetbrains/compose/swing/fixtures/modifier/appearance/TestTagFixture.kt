// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.appearance

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import javax.swing.JLabel

/** The parameters [TestTagFixture] varies. */
public data class TestTagParameters(
    val tag: String,
)

/** A [Label] carrying [SwingModifier.testTag] against a raw [JLabel] holding the same tag as a client property. */
public object TestTagFixture : ComposableFixture<TestTagParameters, JLabel>(
    initial = TestTagParameters(tag = "before"),
) {
    private const val HOST_TEXT = "Modifier"

    override val alternatives: List<TestTagParameters> =
        listOf(
            initial.copy(tag = "after"),
        )

    @Composable
    override fun Content(parameters: TestTagParameters): Unit = Content(parameters.tag)

    /** Composes a [Label] showing the host text, tagged [tag] through [SwingModifier.testTag]. */
    @Composable
    public fun Content(tag: String) {
        Label(HOST_TEXT, modifier = SwingModifier.testTag(tag))
    }

    /** Builds a raw [JLabel] showing the host text and carrying no tag yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Stores the tag [parameters] carries on [component] as a client property. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: TestTagParameters,
    ) {
        component.putClientProperty("testTag", parameters.tag)
    }
}
