package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.core.MenuApplier
import org.jetbrains.compose.swing.core.SwingApplier
import org.jetbrains.compose.swing.core.SwingCompositionMount
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.core.findParentCompositionContext
import org.jetbrains.compose.swing.core.getOrCreateWindowRecomposer
import org.jetbrains.compose.swing.core.publishCompositionContext
import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import javax.swing.JComponent
import javax.swing.JMenuBar
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities

/**
 * Sets the composable [content] of any [Container] (a `JPanel`, a window's content pane, ...).
 *
 * This is the everyday entry point - just `container.setContent { ... }`. When the container is
 * nested under another composition the content joins that composition and shares its scope;
 * otherwise it joins the composition shared by the owning top-level [Window], so every island in one
 * window recomposes together.
 *
 * A container detached from any window is **not** an error: the content is mounted as soon as the
 * container is attached to a window. Disposing the returned handle before that happens mounts nothing.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes this island's composition when invoked (or, if the mount
 *   has not happened yet, cancels it).
 */
public fun Container.setContent(
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle =
    setContent(
        recomposer = null,
        content = content,
    )

/**
 * Integration seam for [Container.setContent] that hosts the composition inside a caller-owned
 * [Recomposer]. For the everyday call use the
 * [single-argument overload][setContent] (`container.setContent { ... }`).
 *
 * When a [recomposer] is supplied it drives the composition and the **caller owns its lifecycle**:
 * disposing the returned handle disposes this island's composition but leaves the recomposer running.
 * The content is mounted immediately. When [recomposer] is `null`, the content joins a composition as
 * the everyday overload does.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param recomposer a caller-owned recomposer that drives the composition and whose lifecycle the
 *   caller owns. Defaults to `null`, meaning join a composition as the everyday overload does.
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes this island's composition when invoked.
 */
@InternalSwingUiApi
public fun Container.setContent(
    recomposer: Recomposer? = null,
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    if (recomposer != null) {
        val mount = SwingCompositionMount.nested(recomposer) { observer -> SwingApplier(this, observer) }
        mount.setContent(content)
        return DisposableHandle { mount.dispose() }
    }

    return mountWhenParentResolves(this) { parent ->
        val mount = SwingCompositionMount.nested(parent) { observer -> SwingApplier(this, observer) }
        mount.setContent(content)
        mount
    }
}

/**
 * Resolves the [CompositionContext] a non-injected `setContent` on [component] should nest into
 * **right now**, or `null` if it cannot be resolved yet (the container has no host stamp and no window
 * ancestor, so the mount must be deferred until [component] is attached to a window).
 *
 * Resolution order: first an existing host discovered self-first up the Swing tree, then the owning
 * window's shared recomposer.
 */
private fun resolveWindowParentContextOrNull(component: Component): CompositionContext? {
    component.findParentCompositionContext()?.let { return it }

    val window = SwingUtilities.getWindowAncestor(component) ?: (component as? Window)
    return window?.getOrCreateWindowRecomposer()
}

/**
 * Mounts a non-injected `setContent` on [component] as soon as its parent [CompositionContext] can be
 * resolved, and returns a [DisposableHandle] over the (possibly still pending) mount.
 *
 * If the parent resolves immediately (a host stamp self-first up the tree, or the owning window's
 * recomposer), [mount] runs synchronously here. Otherwise the mount is **deferred** until [component]
 * gains a window ancestor, at which point the parent is re-resolved and [mount] runs.
 *
 * The returned handle is idempotent: disposing before attach removes the pending listener and mounts
 * nothing; disposing after mount disposes the mount.
 */
private fun mountWhenParentResolves(
    component: Component,
    mount: (CompositionContext) -> SwingCompositionMount,
): DisposableHandle {
    val state = DeferredMountState(component, mount)
    state.start()
    return state
}

/**
 * The explicit lifecycle of a (possibly deferred) non-injected `setContent` mount.
 *
 * The mount is exactly one of three phases at any time:
 *  - [Pending] - no parent resolved yet; a window-attach listener is armed waiting for one;
 *  - [Mounted] - a parent resolved and the composition is live;
 *  - [Disposed] - torn down; terminal, reached at most once.
 *
 * Modeling the phase explicitly (rather than a `disposed` flag plus the implicit
 * "composition != null => mounted" invariant) lets the transitions reject illegal moves
 * structurally - e.g. a window-attach event that fires after disposal cannot mount.
 */
private enum class MountPhase { Pending, Mounted, Disposed }

/**
 * The lifecycle state machine backing a (possibly deferred) non-injected `setContent` mount.
 *
 * Lives only on the EDT, so its fields need no synchronization. It moves from *pending* to *mounted*
 * once a parent resolves, or to *disposed* on teardown, reaching disposed at most once. All phase
 * transitions go through this object, so no caller can drive it into an illegal state.
 *
 * @param component the container whose parent context is resolved (self-first walk, then window).
 * @param mount creates and starts the [SwingCompositionMount] once the parent context is known.
 */
private class DeferredMountState(
    private val component: Component,
    private val mount: (CompositionContext) -> SwingCompositionMount,
) : DisposableHandle {
    private var phase = MountPhase.Pending

    /** Handle removing the pending attach listener; held only while [MountPhase.Pending]. */
    private var pendingAttachListener: DisposableHandle? = null

    /** The live composition; held only while [MountPhase.Mounted]. */
    private var composition: SwingCompositionMount? = null

    /**
     * Resolves the parent and mounts now if it can; otherwise stays pending and arms a window-attach
     * listener that retries the resolution the moment [component] gains a window ancestor.
     *
     * A stamped host (self-first walk) or an already-attached window resolves immediately; deferral is
     * only for the genuinely detached, unstamped case.
     */
    fun start() {
        if (tryMount()) return
        pendingAttachListener = runOnceAttachedToWindow(component) { tryMount() }
    }

    /**
     * Resolves the parent and, if available, transitions [MountPhase.Pending] -> [MountPhase.Mounted].
     * No-op (returns `false`) unless currently pending and a parent resolves - so it cannot mount after
     * disposal, nor mount twice. Returns `true` once the content has been mounted.
     */
    private fun tryMount(): Boolean {
        val parent =
            if (phase == MountPhase.Pending) resolveWindowParentContextOrNull(component) else null
        if (parent != null) {
            pendingAttachListener?.dispose()
            pendingAttachListener = null
            composition = mount(parent)
            phase = MountPhase.Mounted
        }
        return parent != null
    }

    override fun dispose() {
        if (phase == MountPhase.Disposed) return
        phase = MountPhase.Disposed
        pendingAttachListener?.dispose()
        pendingAttachListener = null
        composition?.dispose()
        composition = null
    }
}

/**
 * Runs [block] once [component] is attached to a top-level [Window]: immediately if it already has a
 * window ancestor, otherwise when it first gains one. [block] runs at most once.
 *
 * Attachment is observed through a [HierarchyListener] on [component].
 *
 * @return a [DisposableHandle] that, if [block] has not run yet, removes the pending listener; a no-op
 *   once [block] has already run. Disposing is idempotent.
 */
private fun runOnceAttachedToWindow(
    component: Component,
    block: () -> Unit,
): DisposableHandle {
    if (component.isAttachedToWindow()) {
        block()
        return DisposableHandle { }
    }

    var pending = true
    lateinit var listener: HierarchyListener
    listener =
        HierarchyListener { event ->
            if (event.changeFlags and ATTACH_CHANGE_FLAGS == 0L) return@HierarchyListener
            if (pending && component.isAttachedToWindow()) {
                pending = false
                component.removeHierarchyListener(listener)
                block()
            }
        }
    component.addHierarchyListener(listener)
    return DisposableHandle {
        if (pending) {
            pending = false
            component.removeHierarchyListener(listener)
        }
    }
}

/** A [Window] is its own ancestor; any other component is attached once it has a window ancestor. */
private fun Component.isAttachedToWindow(): Boolean = this is Window || SwingUtilities.getWindowAncestor(this) != null

/**
 * The [HierarchyEvent] change flags that can hand a previously detached [Component] a window ancestor:
 * a parent reattachment ([HierarchyEvent.PARENT_CHANGED]), the component becoming displayable
 * ([HierarchyEvent.DISPLAYABILITY_CHANGED]), or its showing state changing
 * ([HierarchyEvent.SHOWING_CHANGED]). Other hierarchy events are irrelevant to attachment.
 */
private const val ATTACH_CHANGE_FLAGS: Long =
    (
        HierarchyEvent.PARENT_CHANGED or
            HierarchyEvent.DISPLAYABILITY_CHANGED or
            HierarchyEvent.SHOWING_CHANGED
    ).toLong()

/**
 * Hosts [content] inside [this] container as a child of an explicit [parent] [CompositionContext], so
 * descendant `setContent` calls on this container also join [parent]. Use this from external Swing
 * code that wants to host a Compose island joined to an existing host composition.
 *
 * Typical use: capture the enclosing context with `rememberCompositionContext()` in a `@Composable`
 * scope and thread it here, so a detached top-level peer's content (a separate window/dialog) joins
 * the host composition and shares its [androidx.compose.runtime.CompositionLocal]s and state.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param parent the composition context this content joins and shares the recomposition scope of
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes the child composition when invoked.
 */
internal fun Container.setContentAsInteropHost(
    parent: CompositionContext,
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    val clearStamp = publishCompositionContext(this as? JComponent, parent)

    val mount = SwingCompositionMount.nested(parent) { observer -> SwingApplier(this, observer) }
    mount.setContent(content)
    return DisposableHandle {
        clearStamp()
        mount.dispose()
    }
}

/**
 * Sets the composable [content] of a [Window] (a [javax.swing.JFrame], [javax.swing.JDialog], or
 * [javax.swing.JWindow]).
 *
 * The content is hosted on the window's content pane and joins the composition shared by all islands
 * in that window.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes the composition when invoked.
 */
public fun Window.setContent(
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle {
    val contentPane =
        (this as? RootPaneContainer)?.contentPane
            ?: error(
                "Window.setContent { } requires a RootPaneContainer (JFrame/JDialog/JWindow); " +
                    "'${javaClass.name}' has no content pane.",
            )
    return contentPane.setContent(content = content)
}

/**
 * Sets the composable content of a [JMenuBar].
 *
 * Joins a composition like [Container.setContent]: the enclosing composition when nested, otherwise
 * the composition shared by the owning window. A menu bar installed on a window shares that window's
 * composition.
 *
 * A menu bar is routinely built **before** it is installed on its frame (`bar.setContent { ... }` then
 * `frame.jMenuBar = bar`), so at the call site it usually has no window ancestor yet. As with
 * [Container.setContent], that is **not** an error: the content is mounted the moment the menu bar
 * gains a window ancestor (when it is installed on the frame). Must be called on the Event Dispatch
 * Thread.
 *
 * @param content the composable menu tree (`Menu`, `MenuItem`, ...)
 * @return a [DisposableHandle] that disposes this menu-bar composition (or cancels it if it has not
 *   mounted yet).
 */
@ComposableOpenTarget(-1)
public fun JMenuBar.setContent(
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    return mountWhenParentResolves(this) { parent ->
        val mount = SwingCompositionMount.nestedUnobserved(parent) { MenuApplier(this) }
        mount.setContent(content)
        mount
    }
}

/**
 * Hosts [content] in this menu bar as a child of an explicit [parent] [CompositionContext] - the
 * menu-tree counterpart of [Container.setContentAsInteropHost]. The menu tree then shares [parent]'s
 * recomposition scope, so state hoisted there and any
 * [androidx.compose.runtime.CompositionLocal] provided above it reach the menu items and their
 * callbacks.
 *
 * Unlike [JMenuBar.setContent] this never defers: the parent is given rather than discovered, so a bar
 * that is not yet installed on a window is mounted straight away.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param parent the composition context this menu tree joins and shares the recomposition scope of
 * @param content the composable menu tree (`Menu`, `MenuItem`, ...)
 * @return a [DisposableHandle] that disposes the menu-bar composition when invoked.
 */
@ComposableOpenTarget(-1)
internal fun JMenuBar.setContentAsMenuInteropHost(
    parent: CompositionContext,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    val mount = SwingCompositionMount.nestedUnobserved(parent) { MenuApplier(this) }
    mount.setContent(content)
    return DisposableHandle { mount.dispose() }
}
