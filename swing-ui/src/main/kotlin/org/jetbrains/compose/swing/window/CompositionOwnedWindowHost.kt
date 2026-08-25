package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.core.KeepEnclosingApplicationAlive
import org.jetbrains.compose.swing.core.disposeContentCompositionsIn
import org.jetbrains.compose.swing.core.setCompositionContext
import org.jetbrains.compose.swing.setContentAsInteropHost
import java.awt.Container
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.Image
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * Wires a composition-owned top-level AWT [peer] (a [javax.swing.JFrame] or [javax.swing.JDialog])
 * into the enclosing composition: it forwards the window-closing gesture to [onCloseRequest], installs
 * the geometry write-back, hosts [content] as a child of the enclosing composition - under a
 * [WindowScope] standing for [peer] - and pushes the reactive chrome ([title], [resizable],
 * [alwaysOnTop], [iconImage], [minimumSize]) and geometry onto the peer.
 *
 * [peer] itself is reactive. A caller that must rebuild its peer - because a property AWT accepts only
 * on a window that is not yet realized changed - hands the replacement in, and the wiring follows: the
 * previous peer loses its content, its listeners and its own peer, and the replacement is wired and
 * hosted from scratch.
 *
 * The shape of the two directions matches the two-way geometry model: [applyDeclaredGeometry] reads the
 * hoisted state under a snapshot observer, so a window system reporting a drag or a resize - which
 * [setPosition] and [setSize] carry back into that same state - re-runs the apply rather than
 * recomposing anything.
 *
 * Everything that is specific to one kind of peer is threaded in as a lambda: [installExtras] alongside
 * the wiring this function installs, [applyExtras] at the tail of the observed apply (after geometry, so
 * it can flip visibility once the peer is sized and positioned), and [disposePeer] at the very end of
 * teardown, so a caller can interleave its own steps (for example, hiding a modal dialog before it is
 * disposed).
 *
 * @param peer the top-level window this composition owns.
 * @param onCloseRequest called when the window system reports a closing gesture.
 * @param title the window title.
 * @param resizable whether the user may resize the window.
 * @param alwaysOnTop whether the window stays above the windows of other applications.
 * @param iconImage the window icon, or `null` to leave whatever the platform shows.
 * @param minimumSize the smallest size the user may resize the window to, or `null` for none.
 * @param applyDeclaredGeometry pushes the geometry the hoisted state holds onto the peer, reading that
 *   state as it runs. It runs on every recomposition and again whenever the state it read changes, so
 *   what it reads is what drives the peer - a value read anywhere else is applied only as often as the
 *   composable that read it recomposes.
 * @param setPosition carries a position the window system reports back into the hoisted state.
 * @param setSize carries a size the window system reports back into the hoisted state.
 * @param appliedGeometry the mirror that tells the peer's own moves and resizes from the ones applied
 *   to it, so only the former are carried back.
 * @param installExtras registers any additional listeners in the same [DisposableEffect] as the wiring
 *   installed here, and returns their removal. It runs once per peer, while the state a listener writes
 *   into is declared afresh on every recomposition, so a listener it installs has to reach that state
 *   through a handle the recomposition refreshes - `rememberUpdatedState`. A listener that captures the
 *   state declared when the peer was wired keeps writing into the one the caller has moved on from,
 *   instead of the one a caller that hoists its state somewhere else has moved to.
 * @param applyExtras applies whatever this kind of peer carries alongside its geometry, reading the
 *   hoisted state the same way [applyDeclaredGeometry] does.
 * @param disposePeer disposes the peer, at the very end of teardown.
 * @param content the window's content, composed against a [WindowScope] standing for [peer].
 */
@Composable
internal fun CompositionOwnedWindowHost(
    peer: Window,
    onCloseRequest: () -> Unit,
    title: @Nls String,
    resizable: Boolean,
    alwaysOnTop: Boolean,
    iconImage: Image?,
    minimumSize: Dimension?,
    applyDeclaredGeometry: () -> Unit,
    setPosition: (WindowPosition) -> Unit,
    setSize: (width: Int, height: Int) -> Unit,
    appliedGeometry: AppliedGeometry,
    installExtras: () -> () -> Unit,
    applyExtras: () -> Unit,
    disposePeer: () -> Unit,
    content: @Composable WindowScope.() -> Unit,
) {
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentContent by rememberUpdatedState(content)
    // The geometry write-back listener is installed once per peer, so it reads its writers from here.
    val currentSetPosition by rememberUpdatedState(setPosition)
    val currentSetSize by rememberUpdatedState(setSize)

    KeepEnclosingApplicationAlive()

    // Capture the enclosing composition context (the application/window composition) here in the
    // composable body, NOT inside the DisposableEffect: the peer's content pane is a detached top-level
    // peer, so the Swing-tree walk from it finds no parent. Threading this context through explicitly
    // makes the peer content a CHILD of the enclosing composition, so app-scope state and
    // CompositionLocals flow into the content.
    val parentContext = rememberCompositionContext()

    val container = peer as RootPaneContainer

    // The scope the content is given stands for this peer, so a declaration the content makes on it - a
    // menu bar - reaches the window that content is in. Tied to the peer: a replacement peer hands its
    // content a scope of its own.
    val scope: WindowScope = remember(peer) { WindowScope.of(container.rootPane) }

    // The apply that reads the hoisted state, and the observer that re-runs it when that state changes.
    // Tied to the peer it writes to: a replacement peer is applied to, and observed for, afresh.
    val observedApply = remember(peer) { ObservedPeerApply() }

    // Keyed on the peer: when a caller replaces it, the effect tears the old peer's wiring down (and
    // disposes it) before the new peer is listened to and hosted.
    DisposableEffect(peer) {
        val windowListener =
            object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    currentOnCloseRequest()
                }
            }
        peer.addWindowListener(windowListener)

        val geometryListener =
            peer.installGeometryWriteBack(
                applied = appliedGeometry,
                setPosition = { currentSetPosition(it) },
                setSize = { newWidth, newHeight -> currentSetSize(newWidth, newHeight) },
            )

        val removeExtras = installExtras()

        // This peer is a composition root: its content reads this window and a lifecycle owner, the same
        // locals a `setContent` root states, whether the composition around it came from a `setContent`
        // or from `application { }`, which is a root of no window at all. The owner comes out as this
        // window's own: the content pane's ancestors end at the peer, which publishes no owner, so the
        // root mints one.
        val handle =
            container.contentPane.setContentAsInteropHost(parentContext) {
                // A composed window is its content's window for as long as that peer stands, so the
                // window the locals follow is this one and never moves.
                val standingIn = remember(peer) { mutableStateOf<Window?>(peer) }
                ProvideWindowLocals(standingIn, container.contentPane) {
                    PublishContentParent(container.contentPane)
                    scope.currentContent()
                }
            }

        onDispose {
            removeExtras()
            // A content composition the caller mounted into this window is reached through the window's
            // component tree, so it is taken down while that tree still stands: disposing the content
            // below detaches the containers those compositions sit in, and nothing would reach them
            // afterwards. The window's own content composition is reached by the same walk, and disposal
            // is idempotent.
            disposeContentCompositionsIn(peer)
            handle.dispose()
            peer.removeComponentListener(geometryListener)
            peer.removeWindowListener(windowListener)
            disposePeer()
        }
    }

    // Reactive params: re-applied whenever the corresponding argument changes across recomposition.
    // They are pushed from here because the peer stands above the node tree rather than in it - the tree
    // the applier drives is rooted at container.contentPane, through setContentAsInteropHost above, and
    // the peer only hosts that root as its RootPaneContainer. No node stands for the window itself, so
    // its chrome has no update block to travel in.
    // Effect bodies run on the composition's Swing dispatcher (the EDT), so these mutations are
    // EDT-safe. Geometry is applied before [applyExtras] so the peer is sized and positioned before its
    // visibility (and, for a frame, its extended state) is flipped.
    SideEffect {
        peer.applyChrome(title, resizable)
        peer.applyAlwaysOnTop(alwaysOnTop)
        peer.applyIconImage(iconImage)
        // The floor is installed before the geometry so a size below it is clamped as it arrives rather
        // than the peer being sized twice: AWT raises a size below the floor whichever way round these
        // two land, both when the size is set and when the floor is.
        peer.applyMinimumSize(minimumSize)
        // What the hoisted state holds is read from here on, under the observer: the two lambdas are
        // the ones this recomposition stated, and the reads they make are recorded against the apply
        // rather than against any composable scope.
        observedApply.applyAndObserve {
            applyDeclaredGeometry()
            applyExtras()
        }
    }
}

/**
 * Applies the state a window is declared with onto its peer, under a snapshot observer that applies it
 * again whenever that state changes.
 *
 * A window's geometry is two-way: a window system reports a user's drag or resize once per frame of the
 * gesture, and every report is written back into the very state the window is declared with. Reading
 * that state here rather than in a composable body is what keeps the gesture off the composition - the
 * reads are recorded against this apply, so a report re-runs the apply, which finds the peer already
 * standing as the state says and does nothing.
 */
private class ObservedPeerApply : RememberObserver {
    /**
     * Marshals a change onto the composition's Swing dispatcher. Unlike an ordinary write - guaranteed
     * to notify on the EDT, see [org.jetbrains.compose.swing.core.GlobalSnapshotManager] - a write made
     * through an explicit snapshot notifies on whatever thread applied it, and sizing, packing and
     * placing a window are the event dispatch thread's alone: this marshals every notification rather
     * than trust that the state read here was written the ordinary way.
     */
    private val observer = SnapshotStateObserver { notify -> SwingUtilities.invokeLater(notify) }

    /**
     * The single callback instance the observer is handed: it keeps one scope map for the apply rather
     * than growing a further one per distinct callback.
     */
    private val reapplyOnChange: (ObservedPeerApply) -> Unit = { it.reapply() }

    /** What to apply, as the latest recomposition stated it; null until the first apply. */
    private var declaration: (() -> Unit)? = null

    private var released = false

    /** Applies [declaration] under the observer, and keeps it as what a later change re-applies. */
    fun applyAndObserve(declaration: () -> Unit) {
        this.declaration = declaration
        observe(declaration)
    }

    private fun reapply() {
        // A change notified before this peer left the composition is delivered after it, and applying
        // to a released peer would realize a fresh one carrying no content and no listeners.
        if (released) return
        declaration?.let(::observe)
    }

    private fun observe(declaration: () -> Unit) {
        observer.observeReads(scope = this, onValueChangedForScope = reapplyOnChange, block = declaration)
    }

    override fun onRemembered() {
        observer.start()
    }

    override fun onForgotten() {
        release()
    }

    override fun onAbandoned() {
        release()
    }

    private fun release() {
        released = true
        observer.stop()
        observer.clear()
    }
}

/**
 * Publishes the composition this composes in as the parent a `setContent` on [contentPane], or on
 * anything under it, joins - for as long as this stays composed.
 *
 * The peer's content is hosted under a context captured in the enclosing composition, which is the
 * composition of whatever the declaration was made in: a content composition joining that one reads the
 * window around the declaration, which for a dialog declared in a window's content is the frame behind
 * it. The context published here is captured under the locals this peer states, so a content composition
 * nested in this window reads them - the window it is in above all - and recomposes with the rest
 * of the content it is nested in.
 *
 * The `setContent` root that hosts this peer's content stamps the same [contentPane] with the context it
 * composes under, and this deliberately supersedes it: both name a valid parent, and this one is the only
 * one carrying the locals the peer states. The stamps nest rather than race - this one is written from
 * inside the composition that root owns, so it lands over that stamp and is the one a content composition
 * joining from under this peer reads.
 */
@Composable
private fun PublishContentParent(contentPane: Container) {
    val context = rememberCompositionContext()
    DisposableEffect(contentPane, context) {
        val host = contentPane as? JComponent
        host?.setCompositionContext(context)
        onDispose { host?.setCompositionContext(null) }
    }
}

/**
 * Pushes the declared [title] and [resizable] onto this peer when they differ from what it already
 * carries. Both [Frame] and [Dialog] declare these accessors independently, with no shared supertype,
 * so the write dispatches on the concrete peer type.
 */
private fun Window.applyChrome(
    title: @Nls String,
    resizable: Boolean,
) {
    when (this) {
        is Frame -> {
            if (this.title != title) this.title = title
            if (isResizable != resizable) isResizable = resizable
        }

        is Dialog -> {
            if (this.title != title) this.title = title
            if (isResizable != resizable) isResizable = resizable
        }
    }
}

/**
 * Pushes the declared [alwaysOnTop] onto this window when it differs from what the window already
 * carries. A platform with no always-on-top support drops the request while still recording it, so the
 * write is skipped there and the window keeps reporting the state it actually has.
 */
private fun Window.applyAlwaysOnTop(alwaysOnTop: Boolean) {
    if (isAlwaysOnTopSupported && isAlwaysOnTop != alwaysOnTop) isAlwaysOnTop = alwaysOnTop
}

/**
 * Pushes the declared [iconImage] onto this window when it differs from the icon the window already
 * carries. A null [iconImage] clears the icon, restoring the platform default.
 */
private fun Window.applyIconImage(iconImage: Image?) {
    if (iconImages.firstOrNull() != iconImage) setIconImage(iconImage)
}

/**
 * Pushes the declared [minimumSize] onto this window when it differs from the floor the window already
 * carries. A null [minimumSize] releases the floor, so the window reports the minimum size its layout
 * computes; reading that computed size is itself a layout pass, hence it is only read back for
 * comparison once a floor has been set.
 */
private fun Window.applyMinimumSize(minimumSize: Dimension?) {
    if (minimumSize == null) {
        if (isMinimumSizeSet) this.minimumSize = null
    } else if (!isMinimumSizeSet || this.minimumSize != minimumSize) {
        // AWT keeps the very instance it is handed, so the floor is installed from a copy: mutating the
        // declared dimension afterwards must not move the floor behind the composition's back.
        this.minimumSize = Dimension(minimumSize)
    }
}
