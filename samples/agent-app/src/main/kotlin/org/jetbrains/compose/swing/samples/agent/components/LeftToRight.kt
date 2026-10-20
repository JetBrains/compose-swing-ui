package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import java.awt.ComponentOrientation

/** Lays [content] out left to right in either UI direction: source code always reads that way. */
@Composable
internal fun LeftToRight(content: @Composable () -> Unit) {
    ProvideComponentDefaults(DefaultComponentOrientation provides ComponentOrientation.LEFT_TO_RIGHT, content)
}
