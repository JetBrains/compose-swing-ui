package org.jetbrains.compose.swing.samples.agent.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.scaleIn
import org.jetbrains.compose.swing.animation.scaleOut
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.focusable
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import java.awt.event.MouseEvent
import javax.swing.JList
import javax.swing.ListSelectionModel

/**
 * The `/` command list: one row per command in [commands], the [highlighted] one selected. Pointing at a row
 * highlights it through [onHighlight] and a click hands it to [onExecute]. The list never takes focus, so the
 * composer keeps the keyboard.
 */
@Composable
internal fun ConstrainedScope.SlashPopup(
    visible: Boolean,
    commands: List<SlashCommand>,
    highlighted: Int,
    onHighlight: (Int) -> Unit,
    onExecute: (SlashCommand) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    // The card keeps listing the last matches while it plays its exit.
    var shown by remember { mutableStateOf(commands) }
    if (visible) shown = commands
    AnimatedVisibility(
        visible,
        modifier,
        enter =
            scaleIn(
                tween(160),
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) + fadeIn(tween(160)),
        exit =
            scaleOut(
                tween(110),
                targetScale = 0.95f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) + fadeOut(tween(110)),
        label = "slash popup",
    ) {
        CommandCard(shown, highlighted.coerceAtMost(shown.lastIndex), onHighlight, onExecute)
    }
}

/**
 * The list over a static, shadowed background: the shadow belongs to the background layer alone, so the list
 * repaints without it.
 */
@Composable
private fun ConstrainedScope.CommandCard(
    commands: List<SlashCommand>,
    highlighted: Int,
    onHighlight: (Int) -> Unit,
    onExecute: (SlashCommand) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val rows = remember(commands) { commands.map { "/${it.name}  ${it.description}" } }
    Box(
        modifier
            .fillMaxWidth()
            // Listening for clicks keeps one on the card's padding from reaching the transcript beneath.
            .onPointerEvent(onClick = {}),
    ) {
        Box(
            SwingModifier
                .matchParentSize()
                .shadow(
                    16,
                    theme.colors.shadow,
                    offsetY = 4,
                ).background(theme.colors.raisedSurface, theme.shapes.medium),
        )
        ListBox(
            rows,
            SwingModifier
                .fillMaxWidth()
                .padding(6)
                .styleClass(StyleClass.CommandList)
                .focusable(false)
                .mouseMotionListener { event ->
                    val row = event.rowIn()
                    if (row >= 0) onHighlight(row)
                }.onPointerEvent(onClick = { event ->
                    val row = event.rowIn()
                    if (row >= 0) onExecute(commands[row])
                }),
            selectedIndices = setOf(highlighted),
            onSelectionChange = { selection -> selection.firstOrNull()?.let(onHighlight) },
            selectionMode = ListSelectionModel.SINGLE_SELECTION,
            visibleRowCount = rows.size,
        )
    }
}

/** The row under the pointer, or -1 between rows. */
private fun MouseEvent.rowIn(): Int {
    val list = source as JList<*>
    val row = list.locationToIndex(point)
    return if (row >= 0 && list.getCellBounds(row, row).contains(point)) row else -1
}
