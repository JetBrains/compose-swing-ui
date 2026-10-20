package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRecord
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRepository

/** Whether a side pane shows, and its width in pixels. */
@Immutable
internal data class SidePane(
    val isVisible: Boolean,
    val width: Int,
)

/**
 * Which side panes show and how wide they are, stored in [repository] after every change. Each width stays within
 * its pane's bounds. [stored] is the workspace of the last run, if any.
 */
@Stable
internal class PaneLayout(
    private val repository: WorkspaceRepository,
    scope: CoroutineScope,
    stored: WorkspaceRecord? = null,
) {
    private val writes = WriteQueue(scope)

    var sessions by mutableStateOf(
        SidePane(
            isVisible = stored?.isSessionsVisible ?: true,
            width = (stored?.sessionsWidth ?: 252).coerceIn(SESSIONS_WIDTHS),
        ),
    )
        private set

    var context by mutableStateOf(
        SidePane(
            isVisible = stored?.isContextVisible ?: true,
            width = (stored?.contextWidth ?: 296).coerceIn(CONTEXT_WIDTHS),
        ),
    )
        private set

    /** The first exception a write of this state threw, or `null` while every change has been stored. */
    val writeFailure: Throwable? get() = writes.failure

    /** Returns once every change so far is stored or has failed, with the [writeFailure], if any. */
    suspend fun awaitWrites() = writes.awaitWrites()

    private fun save() {
        val sessionsPane = sessions
        val contextPane = context
        writes.writeLatest(PaneLayout::class) {
            repository.savePanes(sessionsPane.isVisible, sessionsPane.width, contextPane.isVisible, contextPane.width)
        }
    }

    fun setSessionsVisible(visible: Boolean) {
        sessions = sessions.copy(isVisible = visible)
        save()
    }

    fun setContextVisible(visible: Boolean) {
        context = context.copy(isVisible = visible)
        save()
    }

    fun toggleSessions() = setSessionsVisible(!sessions.isVisible)

    fun toggleContext() = setContextVisible(!context.isVisible)

    /** Widens the sessions pane by [pixels], or narrows it when negative, within 180 to 420 pixels. */
    fun widenSessions(pixels: Int) {
        sessions = sessions.copy(width = (sessions.width + pixels).coerceIn(SESSIONS_WIDTHS))
        save()
    }

    /** Widens the context pane by [pixels], or narrows it when negative, within 220 to 480 pixels. */
    fun widenContext(pixels: Int) {
        context = context.copy(width = (context.width + pixels).coerceIn(CONTEXT_WIDTHS))
        save()
    }

    companion object {
        private val SESSIONS_WIDTHS = 180..420
        private val CONTEXT_WIDTHS = 220..480

        /** Creates the layout the last run left, or the default one on the first run. */
        suspend fun restore(
            repository: WorkspaceRepository,
            scope: CoroutineScope,
        ) = PaneLayout(repository, scope, repository.workspace())
    }
}
