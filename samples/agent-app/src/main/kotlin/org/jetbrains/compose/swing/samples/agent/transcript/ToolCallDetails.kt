package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.defaults.current
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.samples.agent.components.CheckGlyph
import org.jetbrains.compose.swing.samples.agent.components.CodeBlock
import org.jetbrains.compose.swing.samples.agent.components.planStepFont
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.PlanItem
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.currentIndex
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme

/** The input and output of [call], below its header: an edit's as a diff, a plan's as a checklist. */
@Composable
internal fun ConstrainedScope.ToolCallDetails(call: TranscriptItem.ToolCall) {
    val theme = LocalAgentTheme.current
    val kind = ToolKind.of(call.tool)
    Column(
        SwingModifier.fillMaxWidth().padding(start = 5, end = 5, bottom = 5),
        verticalArrangement = Arrangement.spacedBy(8),
    ) {
        val output = call.output
        if (kind != ToolKind.Plan && call.input.isNotEmpty()) CodeBlock(call.input)
        when {
            kind == ToolKind.Plan -> {
                PlanChecklist(call.planSteps)
            }

            output == null -> {
                Label(
                    message("toolCall.waiting"),
                    SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText),
                )
            }

            kind == ToolKind.Edit && call.hasDiffOutput -> {
                DiffView(output, SwingModifier.fillMaxWidth())
            }

            else -> {
                OutputText(output, isError = call.status == ToolStatus.Failed || kind == ToolKind.Edit)
            }
        }
    }
}

/** A plan's steps, checked and struck through as in the context pane's plan. */
@Composable
private fun PlanChecklist(steps: List<PlanItem>) {
    val colors = LocalAgentTheme.current.colors
    val current = steps.currentIndex
    Column(verticalArrangement = Arrangement.spacedBy(6)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8), verticalAlignment = Alignment.CenterVertically) {
                CheckGlyph(step.isDone, isCurrent = index == current)
                Label(
                    step.text,
                    SwingModifier
                        .font(planStepFont(step.isDone))
                        .foreground(if (step.isDone) colors.mutedText else colors.text),
                )
            }
        }
    }
}

/** A tool's output in mono, cut to its first lines until the user asks for all of it. */
@Composable
internal fun ColumnScope.OutputText(
    output: String,
    isError: Boolean,
) {
    val theme = LocalAgentTheme.current
    val previewLines = 12
    val lines = output.lines()
    var showAll by remember(output) { mutableStateOf(false) }
    Column(SwingModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6)) {
        CodeBlock(
            if (showAll) output else lines.take(previewLines).joinToString("\n"),
            color = if (isError) theme.colors.danger else theme.colors.text,
        )
        if (lines.size > previewLines) {
            DisclosureButton(
                expanded = showAll,
                onExpandedChange = { showAll = it },
                text = if (showAll) message("toolCall.showLess") else message("toolCall.showAll", lines.size),
            )
        }
    }
}

internal val TranscriptItem.ToolCall.hasDiffOutput: Boolean
    get() = status == ToolStatus.Done && output?.let(::hasDiffHunk) == true
