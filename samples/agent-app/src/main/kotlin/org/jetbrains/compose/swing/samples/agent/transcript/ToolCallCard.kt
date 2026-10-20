package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.expandVertically
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.shrinkVertically
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawStyle
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.CountBadge
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.diffStats
import org.jetbrains.compose.swing.samples.agent.theme.IconTint
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.kordamp.ikonli.feather.Feather
import java.awt.BasicStroke
import java.awt.Cursor

/**
 * A tool the agent ran, as a card: its status, the tool and its input on one line, and on click the input and
 * output below, an edit's as a diff and a plan's as a checklist.
 */
@Composable
internal fun ConstrainedScope.ToolCallCard(
    call: TranscriptItem.ToolCall,
    modifier: SwingModifier = SwingModifier,
) {
    // View state of the run: a session's SaveableStateProvider keeps it while another session shows.
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), style = StyleClass.RaisedCard) {
        ToolCallHeader(call, isExpanded, onClick = { isExpanded = !isExpanded })
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            label = "tool details",
        ) {
            ToolCallDetails(call)
        }
    }
}

@Composable
private fun ColumnScope.ToolCallHeader(
    call: TranscriptItem.ToolCall,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalAgentTheme.current
    val kind = ToolKind.of(call.tool)
    val headerModifier =
        SwingModifier
            .onPointerEvent(onClick = { onClick() })
            .cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    Row(
        headerModifier
            .fillMaxWidth()
            .padding(horizontal = 5, vertical = 1),
        horizontalArrangement = Arrangement.spacedBy(8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusGlyph(call.status, headerModifier)
        Label("", headerModifier.icon(rememberThemedIcon(kind.icon)))
        Label(toolLabel(call.tool), headerModifier.font(theme.fonts.body))
        val summary =
            if (kind == ToolKind.Plan) {
                val steps = call.planSteps
                message("toolCall.planProgress", steps.count { it.isDone }, steps.size)
            } else {
                call.input.lineSequence().first()
            }
        // A label narrower than its text ends it with an ellipsis. A plan's progress is prose, the rest is input.
        val summaryFont = if (kind == ToolKind.Plan) theme.fonts.small else theme.fonts.mono
        Label(summary, headerModifier.weight(1f).font(summaryFont).foreground(theme.colors.mutedText))
        val output = call.output
        if (kind == ToolKind.Edit && call.hasDiffOutput) {
            val stats = remember(output) { diffStats(output.orEmpty()) }
            Row(headerModifier, horizontalArrangement = Arrangement.spacedBy(4)) {
                CountBadge(message("diff.added", stats.added), headerModifier, style = StyleClass.SuccessBadge)
                CountBadge(message("diff.removed", stats.removed), headerModifier, style = StyleClass.DangerBadge)
            }
        }
        DisclosureButton(
            expanded = isExpanded,
            onExpandedChange = { onClick() },
            text = "",
            modifier = SwingModifier.testTag("tool-call-header"),
            label = toolLabel(call.tool),
        )
    }
}

/** A spinner while the tool runs, then a check or a cross. A screen reader reads the status by name. */
@Composable
private fun RowScope.StatusGlyph(
    status: ToolStatus,
    modifier: SwingModifier,
) {
    when (status) {
        ToolStatus.Running -> {
            ToolSpinner(modifier.accessibleName(message("toolCall.status.running")))
        }

        ToolStatus.Done -> {
            Label(
                "",
                modifier
                    .icon(rememberThemedIcon(Feather.CHECK, IconTint.Success))
                    .testTag("tool-done")
                    .accessibleName(message("toolCall.status.done")),
            )
        }

        ToolStatus.Failed -> {
            Label(
                "",
                modifier
                    .icon(rememberThemedIcon(Feather.X, IconTint.Danger))
                    .accessibleName(message("toolCall.status.failed")),
            )
        }
    }
}

/** A rotating arc, composed only while the tool runs. */
@Composable
private fun ConstrainedScope.ToolSpinner(modifier: SwingModifier = SwingModifier) {
    val color = LocalAgentTheme.current.colors.accent
    val angle by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spinner angle",
    )
    val stroke = DrawStyle.Stroke(2f, BasicStroke.CAP_ROUND)
    Canvas(modifier.size(16).testTag("tool-spinner")) {
        drawCircle(color, radius = 6f, style = stroke, alpha = 0.2f)
        drawArc(
            color,
            startAngle = -angle,
            sweepAngle = 100f,
            useCenter = false,
            x = 2f,
            y = 2f,
            width = 12f,
            height = 12f,
            style = stroke,
        )
    }
}
