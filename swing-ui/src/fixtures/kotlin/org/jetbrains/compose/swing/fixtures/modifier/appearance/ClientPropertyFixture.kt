// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.appearance

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.clientProperty
import javax.swing.JLabel

/** The parameters [ClientPropertyFixture] varies. */
public data class ClientPropertyParameters(
    val key: String,
    val value: String,
)

/** A [Label] declaring a client property under a key and value, against a raw [JLabel] carrying the same entry. */
public object ClientPropertyFixture : ComposableFixture<ClientPropertyParameters, JLabel>(
    initial = ClientPropertyParameters(key = "original", value = "before"),
) {
    private const val HOST_TEXT = "Property"
    private const val ALTERNATE_KEY = "alternate"

    override val alternatives: List<ClientPropertyParameters> =
        listOf(
            initial.copy(key = ALTERNATE_KEY),
            initial.copy(value = "after"),
        )

    @Composable
    override fun Content(parameters: ClientPropertyParameters): Unit = Content(parameters.key, parameters.value)

    /** Composes a [Label] whose modifier declares a client property under [key] holding [value]. */
    @Composable
    public fun Content(
        key: String,
        value: String,
    ) {
        Label(HOST_TEXT, modifier = SwingModifier.clientProperty(key = key, value = value))
    }

    /** Builds a [JLabel] showing the host text and carrying no client property yet. */
    override fun createRawSwing(): JLabel = JLabel(HOST_TEXT)

    /** Stores the key and value of [parameters] on [component] as its only client property of the fixture. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ClientPropertyParameters,
    ) {
        val other = if (parameters.key == ALTERNATE_KEY) initial.key else ALTERNATE_KEY
        if (component.getClientProperty(other) != null) component.putClientProperty(other, null)
        component.putClientProperty(parameters.key, parameters.value)
    }
}
