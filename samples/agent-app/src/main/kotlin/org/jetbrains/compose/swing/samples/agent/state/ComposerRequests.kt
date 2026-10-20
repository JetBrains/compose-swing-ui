package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Requests to the composer. The draft is view state of the composer, so a request only asks, and the composer
 * acknowledges it when it has acted.
 */
@Stable
internal class ComposerRequests {
    /** The latest request to empty the draft, `null` once the composer has acted on it. */
    var clearDraftRequest by mutableStateOf<Long?>(null)
        private set

    /** Asks the composer to empty its draft. */
    fun requestClearDraft() {
        clearDraftRequest = (clearDraftRequest ?: 0L) + 1
    }

    /** Tells the state that the composer has acted on [clearDraftRequest]. */
    fun onClearDraftHandled() {
        clearDraftRequest = null
    }
}
