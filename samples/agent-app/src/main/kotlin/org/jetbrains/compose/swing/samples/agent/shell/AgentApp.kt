package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContent
import org.jetbrains.compose.swing.animation.AnimatedVisibility
import org.jetbrains.compose.swing.animation.EnterExitState
import org.jetbrains.compose.swing.animation.expandHorizontally
import org.jetbrains.compose.swing.animation.fadeIn
import org.jetbrains.compose.swing.animation.fadeOut
import org.jetbrains.compose.swing.animation.shrinkHorizontally
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.components.PaneDivider
import org.jetbrains.compose.swing.samples.agent.composer.Composer
import org.jetbrains.compose.swing.samples.agent.composer.SlashCommand
import org.jetbrains.compose.swing.samples.agent.context.ContextPane
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.sessions.SessionsPane
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.Session
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.transcript.EmptyTranscript
import org.jetbrains.compose.swing.samples.agent.transcript.Transcript
import java.nio.file.Path

/**
 * The whole window's content: the top bar, the sessions pane, the transcript with its composer, the context pane
 * and the status bar. It reads the locale, the theme and the reading direction from an [AgentEnvironment] around it.
 */
@Composable
internal fun AgentApp(
    state: AppState,
    panes: PaneLayout,
    settingsState: SettingsState,
    modifier: SwingModifier = SwingModifier,
    chooseFolder: (String) -> Path? = rememberFolderChooser(),
    confirmClear: () -> Boolean = rememberConfirmClear(),
) {
    val onNewSession = state.newSessionAction(chooseFolder)
    val toggleTheme = { settingsState.toggleTheme() }
    var retry by remember { mutableIntStateOf(0) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState.settings.piExecutable, retry) { state.selection.loadModels() }
    if (isSettingsOpen) SettingsDialog(settingsState, onClose = { isSettingsOpen = false })
    WindowShortcuts(state, panes)
    Box(modifier) {
        Column(SwingModifier.fillMaxSize()) {
            TopBar(
                panes,
                settingsState,
                selection = state.selection,
                onThemeToggle = toggleTheme,
                onOpenSettings = { isSettingsOpen = true },
            )
            Row(SwingModifier.weight(1f).fillMaxWidth()) {
                SessionsPaneSlot(state, panes, onNewSession, confirmClear)
                TranscriptArea(
                    state,
                    toggleTheme,
                    chooseFolder,
                    settingsState.settings.piExecutable,
                    onOpenSettings = { isSettingsOpen = true },
                    onRetry = { retry++ },
                    modifier = SwingModifier.weight(1f).fillMaxHeight(),
                )
                ContextPaneSlot(state, panes)
            }
            StatusBar(
                agentName =
                    state.selection.agent
                        ?.displayName
                        .orEmpty(),
                model = state.selection.model.orEmpty(),
                tokens = state.activeSession?.totalTokens ?: 0,
                isRunning = state.runner.isRunning,
                hasWriteFailure =
                    state.writeFailure != null || panes.writeFailure != null || settingsState.writeFailure != null,
            )
        }
    }
}

@Composable
private fun RowScope.SessionsPaneSlot(
    state: AppState,
    panes: PaneLayout,
    onNewSession: () -> Unit,
    confirmClear: () -> Boolean,
) {
    val confirmDelete = rememberConfirmDelete()
    AnimatedVisibility(
        visible = panes.sessions.isVisible,
        // Pinned to the outer edge, so the pane folds into the window side instead of sliding over the transcript.
        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        label = "sessions pane",
    ) {
        Row(SwingModifier.fillMaxHeight()) {
            SessionsPane(
                sessions = state.sessions,
                activeSessionId = state.activeSessionId,
                onSessionSelect = state::selectSession,
                onSessionRename = state::renameSession,
                onSessionDelete = state::deleteSession,
                onSessionClear = { state.clearWithConfirmation(it, confirmClear) },
                onNewSession = onNewSession,
                modifier = SwingModifier.width(panes.sessions.width),
                confirmDelete = confirmDelete,
            )
            PaneDivider(lineAtStart = false, onWiden = panes::widenSessions)
        }
    }
}

@Composable
private fun RowScope.ContextPaneSlot(
    state: AppState,
    panes: PaneLayout,
) {
    AnimatedVisibility(
        visible = panes.context.isVisible,
        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
        label = "context pane",
    ) {
        Row(SwingModifier.fillMaxHeight()) {
            PaneDivider(lineAtStart = true, onWiden = panes::widenContext)
            val session = state.activeSession
            ContextPane(
                plan = session?.plan.orEmpty(),
                files = session?.touchedFiles.orEmpty(),
                usedTokens = session?.contextTokens ?: 0,
                contextLimit = state.selection.contextLimit,
                modifier = SwingModifier.width(panes.context.width),
            )
        }
    }
}

/**
 * The active session's transcript over the composer; switching sessions fades the other transcript in. What the
 * user typed in a composer and which tool cards they opened belong to their session and last for the run: each
 * session's transcript and composer sit in their own saveable state, so they come back when the session does.
 */
@Composable
internal fun TranscriptArea(
    state: AppState,
    onThemeToggle: () -> Unit,
    chooseFolder: (String) -> Path?,
    piExecutable: String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: SwingModifier = SwingModifier,
    confirmClear: () -> Boolean = rememberConfirmClear(),
) {
    val send = state.sendAction(chooseFolder)
    val saveable = rememberSaveableStateHolder()
    ForgetDeletedSessions(saveable, state.sessions)
    Column(modifier.background(LocalAgentTheme.current.colors.surface, RectangleShape)) {
        AnimatedContent(
            targetState = state.activeSessionId,
            modifier = SwingModifier.weight(1f).fillMaxWidth(),
            label = "session",
        ) { id ->
            val session = remember(id) { state.sessions.firstOrNull { it.id == id } }
            saveable.SaveableStateProvider(transcriptKey(id)) {
                if (session != null) {
                    Transcript(
                        turns = session.turns,
                        onPermissionDecision = { permission, decision -> permission.decide(decision) },
                        modifier = SwingModifier.fillMaxSize(),
                        isSettled = transition.currentState == EnterExitState.Visible && !transition.isRunning,
                    )
                } else {
                    EmptyTranscript(SwingModifier.fillMaxSize())
                }
            }
        }
        NoAgentsNotice(state.selection, piExecutable, onOpenSettings, onRetry)
        saveable.SaveableStateProvider(draftKey(state.activeSessionId)) {
            var draft by rememberSaveable { mutableStateOf("") }
            val clearRequest = state.composer.clearDraftRequest
            LaunchedEffect(clearRequest) {
                if (clearRequest != null) {
                    draft = ""
                    state.composer.onClearDraftHandled()
                }
            }
            Composer(
                text = draft,
                onTextChange = { draft = it },
                onSend = { if (send(draft)) draft = "" },
                onStop = state.runner::stop,
                onClear = state.composer::requestClearDraft,
                isRunning = state.activeSession?.isRunning == true,
                slashCommands = slashCommands(state, onThemeToggle, confirmClear),
                modifier = SwingModifier.fillMaxWidth(),
                isReady = state.selection.canSend && !state.runner.isRunning,
                isAnotherSessionRunning = state.runner.isRunning && state.activeSession?.isRunning != true,
            )
        }
    }
}

private fun transcriptKey(sessionId: String?) = "transcript:$sessionId"

private fun draftKey(sessionId: String?) = "draft:$sessionId"

/** Drops the saved view state of a session once it is deleted, so nothing keeps it for the rest of the run. */
@Composable
private fun ForgetDeletedSessions(
    saveable: SaveableStateHolder,
    sessions: List<Session>,
) {
    val ids = sessions.map { it.id }
    val known = remember { HashSet<String>() }
    LaunchedEffect(ids) {
        for (id in known - ids.toSet()) {
            saveable.removeState(transcriptKey(id))
            saveable.removeState(draftKey(id))
        }
        known.clear()
        known += ids
    }
}

@Composable
private fun slashCommands(
    state: AppState,
    onThemeToggle: () -> Unit,
    confirmClear: () -> Boolean,
): List<SlashCommand> =
    buildList {
        add(
            SlashCommand("clear", message("command.clear")) {
                state.activeSessionId?.let { state.clearWithConfirmation(it, confirmClear) }
            },
        )
        add(SlashCommand("model", message("command.model"), state.selection::cycleModel))
        if (state.selection.agents.count { it.isAvailable } >= 2) {
            add(SlashCommand("agent", message("command.agent"), state.selection::cycleAgent))
        }
        add(SlashCommand("theme", message("command.theme"), onThemeToggle))
    }
