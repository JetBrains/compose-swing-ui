package org.jetbrains.compose.swing.samples.agent.composer

import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/** A `/` command the composer offers: its [name] without the slash, what it does, and what it [execute]s. */
internal class SlashCommand(
    val name: @NonNls String,
    val description: @Nls String,
    val execute: () -> Unit,
)

/** The commands whose name starts with what follows the slash in [text]; none unless [text] is a single `/word`. */
internal fun List<SlashCommand>.matching(text: String): List<SlashCommand> {
    if (!text.startsWith("/") || text.any { it.isWhitespace() }) return emptyList()
    val prefix = text.removePrefix("/")
    return filter { it.name.startsWith(prefix, ignoreCase = true) }
}
