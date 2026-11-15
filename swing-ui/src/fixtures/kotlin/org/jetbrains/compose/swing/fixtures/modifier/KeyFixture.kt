// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.modifier.key
import javax.swing.JLabel

/** The parameters [KeyFixture] varies. */
public data class KeyParameters(
    val key: Boolean,
)

/** A [Label] tied to a key flag and named "keyed", against a raw [JLabel] carrying the same name. */
public object KeyFixture : ComposableFixture<KeyParameters, JLabel>(
    initial = KeyParameters(key = false),
) {
    private const val HOST_TEXT = "Modifier"
    private const val KEYED_NAME = "keyed"

    override val alternatives: List<KeyParameters> =
        listOf(
            initial.copy(key = true),
        )

    @Composable
    override fun Content(parameters: KeyParameters): Unit = Content(parameters.key)

    /** Composes a [Label] whose modifier is tied to [key] and named "keyed". */
    @Composable
    public fun Content(key: Boolean) {
        Label(text = HOST_TEXT, modifier = SwingModifier.key(key).name(KEYED_NAME))
    }

    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    override fun updateRawSwing(
        component: JLabel,
        parameters: KeyParameters,
    ) {
        component.name = KEYED_NAME
    }
}
