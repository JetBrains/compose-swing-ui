package org.jetbrains.compose.swing.samples.agent

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.samples.agent.data.AgentDatabase
import org.jetbrains.compose.swing.samples.agent.data.SessionRepository
import org.jetbrains.compose.swing.samples.agent.data.SettingsRepository
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRepository
import org.jetbrains.compose.swing.samples.agent.data.databaseFile
import org.jetbrains.compose.swing.samples.agent.harness.defaultAgents
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.shell.AgentApp
import org.jetbrains.compose.swing.samples.agent.shell.AgentEnvironment
import org.jetbrains.compose.swing.samples.agent.shell.AgentMenuBar
import org.jetbrains.compose.swing.samples.agent.shell.MacWindowChrome
import org.jetbrains.compose.swing.samples.agent.shell.newSessionAction
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.ReduceMotion
import org.jetbrains.compose.swing.samples.agent.state.Settings
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.installLookAndFeelOnEventThread
import org.jetbrains.compose.swing.window.ApplicationScope
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowPosition
import org.jetbrains.compose.swing.window.awaitApplication
import org.jetbrains.compose.swing.window.rememberWindowState
import java.awt.Dimension
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Installs the look and feel before any component exists, then runs the app in the system's language, if it ships
 * one, with the "Reduce motion" switch on the application's coroutine context, where every window's animations
 * read it. The sessions, settings and window layout come from the database in the operating system's application
 * data folder. On exit the running turn is stopped first, then every change is stored, then the database closes.
 */
fun main() {
    // AWT reads this once, as it starts; set later, the menus stay in the window.
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    runAgentApplication(AgentDatabase.open(databaseFile())) { state, panes, settingsState ->
        awaitApplication { AgentWindow(state, panes, settingsState) }
    }
    exitProcess(0)
}

internal fun runAgentApplication(
    database: AgentDatabase,
    showApplication: suspend (AppState, PaneLayout, SettingsState) -> Unit,
) {
    val settingsStore = SettingsRepository(database)
    val defaultSettings = Settings(language = AppLanguage.closestTo(Locale.getDefault()))
    // Nothing may touch the event thread before the look and feel is installed, so the settings load on this thread.
    val storedSettings = runBlocking { SettingsState.stored(settingsStore, defaultSettings) }
    installLookAndFeelOnEventThread(storedSettings.themeMode)
    // The motion switch reads the settings and the app scope carries the switch, so the settings write on a scope
    // of their own.
    val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val settingsState = SettingsState(storedSettings, settingsStore, settingsScope)
    val motion = ReduceMotion(settingsState)
    val appScope = appCoroutineScope(motion)
    try {
        runBlocking(motion) {
            val workspaceStore = WorkspaceRepository(database)
            val state =
                withContext(Dispatchers.Swing) {
                    AppState.restore(
                        appScope,
                        defaultAgents(databaseFile().parent) { settingsState.settings.piExecutable },
                        SessionRepository(database),
                        workspaceStore,
                    )
                }
            val panes = withContext(Dispatchers.Swing) { PaneLayout.restore(workspaceStore, appScope) }
            try {
                showApplication(state, panes, settingsState)
            } finally {
                val failures =
                    withContext(NonCancellable + Dispatchers.Swing) {
                        listOfNotNull(state.shutDown(), panes.awaitWrites(), settingsState.awaitWrites())
                    }
                for (failure in failures) System.err.println("A change could not be stored: $failure")
            }
        }
    } finally {
        appScope.cancel()
        settingsScope.cancel()
        database.close()
    }
}

/**
 * The scope the app's turns run in. They record into snapshot state on the event thread, and one failing turn
 * leaves the others running.
 */
internal fun appCoroutineScope(motion: ReduceMotion): CoroutineScope =
    CoroutineScope(SupervisorJob() + Dispatchers.Swing + motion)

/** The app's one window, centered, with its menus and the macOS full-height content. */
@Composable
private fun ApplicationScope.AgentWindow(
    state: AppState,
    panes: PaneLayout,
    settingsState: SettingsState,
) {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(WindowPosition.CenteredOnScreen, Dimension(1320, 860)),
        title = message(settingsState.settings.language.locale, "app.title"),
        minimumSize = Dimension(960, 600),
    ) {
        MacWindowChrome()
        AgentEnvironment(settingsState.settings) {
            AgentMenuBar(
                state,
                panes,
                settingsState,
                onCloseWindow = ::exitApplication,
                onNewSession = state.newSessionAction(),
            )
            AgentApp(state, panes, settingsState)
        }
    }
}
