package org.jetbrains.compose.swing.core

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Platform-specific mechanism for starting a monitor of global snapshot state writes in order to
 * schedule the periodic dispatch of snapshot apply notifications. This process should remain
 * platform-specific; it is tied to the threading and update model of a particular platform and
 * framework target.
 *
 * Composition bootstrapping mechanisms for a particular platform/framework should call
 * [ensureStarted] during setup to initialize periodic global snapshot notifications. These
 * notifications are always sent on the Swing event dispatch thread via [Dispatchers.Swing].
 *
 * This is the guarantee a [SnapshotStateObserver][androidx.compose.runtime.snapshots.SnapshotStateObserver]
 * elsewhere in this module can rely on for a write made the ordinary way - an assignment to a
 * `mutableStateOf` outside of an explicit snapshot - since that write reaches its observer only
 * through the notification this class schedules. A write applied through an explicit
 * [Snapshot][androidx.compose.runtime.snapshots.Snapshot] (`takeMutableSnapshot`, then `apply()`)
 * bypasses this class entirely: its observers are notified synchronously, on whatever thread called
 * `apply()`.
 */
internal object GlobalSnapshotManager {
    private val started = AtomicBoolean(false)
    private val sent = AtomicBoolean(false)

    fun ensureStarted() {
        if (started.compareAndSet(false, true)) {
            val channel = Channel<Unit>(1)
            CoroutineScope(Dispatchers.Swing).launch {
                channel.consumeEach {
                    sent.set(false)
                    Snapshot.sendApplyNotifications()
                }
            }
            Snapshot.registerGlobalWriteObserver {
                if (sent.compareAndSet(false, true)) {
                    channel.trySend(Unit)
                }
            }
        }
    }
}
