package org.jetbrains.compose.swing.samples.agent.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.drawWithContent
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.heightIn
import org.jetbrains.compose.swing.foundation.layout.layout
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.FocusRequester
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.interaction.focusRequester
import org.jetbrains.compose.swing.modifier.interaction.initialFocus
import org.jetbrains.compose.swing.modifier.interaction.rememberFocusRequester
import org.jetbrains.compose.swing.modifier.keyboard.onKeyEvent
import org.jetbrains.compose.swing.samples.agent.components.ToolbarButton
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.rememberThemedIcon
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.jetbrains.compose.swing.window.LocalWindow
import org.kordamp.ikonli.feather.Feather
import java.awt.Dialog
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.font.FontRenderContext
import javax.swing.JScrollPane
import javax.swing.text.JTextComponent
import kotlin.math.ceil

/**
 * The prompt editor under the transcript: Enter sends, Shift+Enter adds a line, and a leading `/` offers
 * [slashCommands] in a popup above it, which the commands button also opens. Send becomes Stop while a turn runs,
 * and a clear button, enabled for a non-empty draft, empties it through [onClear].
 */
@Composable
internal fun ConstrainedScope.Composer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    isRunning: Boolean,
    slashCommands: List<SlashCommand>,
    modifier: SwingModifier = SwingModifier,
    isReady: Boolean = true,
    isAnotherSessionRunning: Boolean = false,
) {
    // Keyed on the text: each keystroke starts at the first row and offers a dismissed popup again.
    val popup = remember(text) { SlashPopupState() }
    val matches = if (popup.isRequested) slashCommands else slashCommands.matching(text)
    val isPopupOpen = matches.isNotEmpty() && !popup.isDismissed
    val canSend = isReady && text.isNotBlank() && !isRunning
    val inputFocus = rememberFocusRequester()

    fun execute(command: SlashCommand) {
        popup.isDismissed = true
        if (!popup.isRequested) onTextChange("")
        command.execute()
    }

    val onKey = { event: KeyEvent ->
        handleComposerKey(event, canSend, onSend) { key ->
            isPopupOpen && popup.handleKey(key, matches.size) { execute(matches[it]) }
        }
    }

    // The same centered column as the transcript's, so the field lines up with the conversation above it.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            SwingModifier.padding(start = 24, end = 24, top = 8, bottom = 10).widthIn(max = 760).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6),
        ) {
            Box(SwingModifier.fillMaxWidth()) {
                Row(
                    SwingModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    CommandsButton(SwingModifier.size(32)) {
                        popup.isRequested = true
                        popup.isDismissed = false
                        popup.highlighted = 0
                        inputFocus.requestFocus()
                    }
                    PromptField(text, onTextChange, onKey, inputFocus, SwingModifier.weight(1f))
                    ClearButton(isVisible = text.isNotEmpty(), inputFocus, onClear)
                    SendButton(
                        isRunning,
                        canSend,
                        onSend,
                        onStop,
                        SwingModifier.size(32),
                        sendToolTip = sendToolTip(isAnotherSessionRunning),
                    )
                }
                SlashPopup(
                    visible = isPopupOpen,
                    commands = matches,
                    highlighted = popup.highlighted,
                    onHighlight = { popup.highlighted = it },
                    onExecute = ::execute,
                    modifier = SwingModifier.fillMaxWidth().floatAbove(gap = 8),
                )
            }
            KeyHints(visible = text.isEmpty())
        }
    }
}

/** Empties the draft and returns focus to the field. Always laid out, so the field keeps its width. */
@Composable
private fun ConstrainedScope.ClearButton(
    isVisible: Boolean,
    focus: FocusRequester,
    onClear: () -> Unit,
) {
    Box(SwingModifier.alpha(if (isVisible) 1f else 0f)) {
        ToolbarButton(
            onClick = {
                onClear()
                focus.requestFocus()
            },
            modifier = SwingModifier.enabled(isVisible).testTag("composer-clear"),
            icon = rememberThemedIcon(Feather.X),
            toolTip = message("composer.clear"),
        )
    }
}

/** Opens the command list while keeping the draft. */
@Composable
private fun CommandsButton(
    modifier: SwingModifier = SwingModifier,
    onClick: () -> Unit,
) {
    ToolbarButton(
        onClick,
        modifier.testTag("composer-commands"),
        icon = rememberThemedIcon(Feather.TERMINAL),
        toolTip = message("composer.commands"),
    )
}

/**
 * The rounded field: a text area that grows from one line to about eight, then scrolls. The scroll pane's look and
 * feel border draws the outline and the focus ring.
 */
@Composable
private fun ConstrainedScope.PromptField(
    text: String,
    onTextChange: (String) -> Unit,
    onKey: (KeyEvent) -> Boolean,
    focus: FocusRequester,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val placeholder = message("composer.placeholder")
    val window = LocalWindow.current
    val initialFocus =
        if (window?.ownedWindows?.none { it is Dialog && it.isVisible } != false) {
            SwingModifier.initialFocus()
        } else {
            SwingModifier
        }
    // Written inside the text area's own edit, so the paint that shows a typed character already reads it. The
    // placeholder composed from [text] would still be on screen in that paint, over the character.
    val isEmpty = remember { mutableStateOf(text.isEmpty()) }
    SideEffect { isEmpty.value = text.isEmpty() }
    Box(modifier.testTag("composer-prompt")) {
        ScrollPane(
            SwingModifier
                .fillMaxWidth()
                .heightIn(max = lineHeight(theme.fonts.body) * 8)
                .styleClass(StyleClass.Prompt),
            horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
        ) {
            Viewport {
                TextArea(
                    text,
                    {
                        isEmpty.value = it.isEmpty()
                        onTextChange(it)
                    },
                    SwingModifier
                        .then(initialFocus)
                        .accessibleName(placeholder)
                        .onKeyEvent(onKey)
                        .focusRequester(focus)
                        .testTag("composer-input"),
                    lineWrap = true,
                    wrapStyleWord = true,
                )
            }
        }
        // Over the text area; neither box nor label listens for the mouse, so clicks reach the text area.
        Box(
            SwingModifier
                .matchParentSize()
                .drawWithContent { if (isEmpty.value) drawContent() }
                .padding(horizontal = 21),
            contentAlignment = Alignment.CenterStart,
        ) {
            Label(placeholder, SwingModifier.foreground(theme.colors.mutedText))
        }
    }
}

/** The keyboard hints under the field, fading out once there is text so they never compete with it. */
@Composable
private fun ConstrainedScope.KeyHints(
    visible: Boolean,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "key hints")
    Row(modifier.alpha(alpha).padding(horizontal = 14)) {
        Label(message("composer.hint"), SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText))
    }
}

/** The slash popup's own state for one text: the highlighted row, and whether Esc hid it. */
private class SlashPopupState {
    var highlighted by mutableIntStateOf(0)
    var isDismissed by mutableStateOf(false)
    var isRequested by mutableStateOf(false)

    /** Up and Down move through [count] rows, Enter hands the highlighted one to [onExecute], and Esc hides. */
    fun handleKey(
        keyCode: Int,
        count: Int,
        onExecute: (Int) -> Unit,
    ): Boolean {
        when (keyCode) {
            KeyEvent.VK_UP -> highlighted = (highlighted - 1).mod(count)
            KeyEvent.VK_DOWN -> highlighted = (highlighted + 1).mod(count)
            KeyEvent.VK_ENTER -> onExecute(highlighted)
            KeyEvent.VK_ESCAPE -> isDismissed = true
            else -> return false
        }
        return true
    }
}

/** Enter sends through [onSend] when [canSend], and Shift+Enter types a newline; other keys go to the text area. */
private fun handleEnter(
    event: KeyEvent,
    canSend: Boolean,
    onSend: () -> Unit,
): Boolean {
    if (event.keyCode != KeyEvent.VK_ENTER) return false
    if (event.isShiftDown) {
        // The event comes from the text area; a newline typed there lands at the caret, over any selection.
        (event.source as JTextComponent).replaceSelection("\n")
    } else if (canSend) {
        onSend()
    }
    return true
}

/** Places the content [gap] above where it would sit and takes no height, so it floats over what lies above. */
context(scope: ConstrainedScope)
private fun SwingModifier.floatAbove(gap: Int): SwingModifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minHeight = 0))
        layout(placeable.width, 0) { placeable.place(0, -placeable.height - gap) }
    }

private fun lineHeight(font: Font): Int =
    ceil(font.getLineMetrics("", FontRenderContext(null, true, true)).height).toInt()

private fun handleComposerKey(
    event: KeyEvent,
    canSend: Boolean,
    onSend: () -> Unit,
    onPopupKey: (Int) -> Boolean,
): Boolean =
    when {
        event.id != KeyEvent.KEY_PRESSED -> {
            false
        }

        event.keyCode == KeyEvent.VK_TAB && !event.isControlDown -> {
            if (event.isShiftDown) event.component.transferFocusBackward() else event.component.transferFocus()
            true
        }

        else -> {
            onPopupKey(event.keyCode) || handleEnter(event, canSend, onSend)
        }
    }

@Composable
private fun sendToolTip(anotherSessionRunning: Boolean): @Nls String =
    message(if (anotherSessionRunning) "composer.anotherSessionRunning" else "composer.send")
