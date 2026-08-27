package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.core.MountParent
import org.jetbrains.compose.swing.core.SwingContentComposition
import org.jetbrains.compose.swing.core.checkEventDispatchThread
import org.jetbrains.compose.swing.core.mountUnderNamedParent
import org.jetbrains.compose.swing.core.mountWhenParentResolves
import org.jetbrains.compose.swing.core.setCompositionContext
import org.jetbrains.compose.swing.node.MenuApplier
import org.jetbrains.compose.swing.node.SwingApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import org.jetbrains.compose.swing.window.ProvideContentLocals
import java.awt.Container
import java.awt.Window
import javax.swing.JComponent
import javax.swing.JMenuBar
import javax.swing.RootPaneContainer

/**
 * Sets the composable [content] of any [Container] (a `JPanel`, a window's content pane, ...).
 *
 * With no [parent] the content joins the composition the container's own place in the Swing tree
 * resolves to: an enclosing composition when the container is nested under one, otherwise the
 * composition shared by the owning top-level [Window], so every content composition in one window
 * recomposes together. A container detached from any window is **not** an error: the content is mounted
 * as soon as the container is attached to a window, and disposing the returned handle before that
 * happens mounts nothing. A container in a window that has been disposed composes where it stands, on a
 * composition that lasts as long as the handle returned here.
 *
 * A [parent] drives the composition instead, and the content composes **on this call**, whatever the
 * container is attached to - which is what reaches a container that is built to be read rather than
 * shown. Everything mounted inside this content joins [parent] as well: a `setContent` naming no parent
 * of its own on a container hanging under this one resolves to [parent] rather than to the composition
 * its window shares. The caller owns what they pass: disposing the returned handle disposes this
 * content composition and leaves [parent] running. A container that later ends up in a different
 * window joins the composition of the window it is then in, recreating this content there - unless
 * [parent] is a recomposer of the caller's own, which the content is kept on. A move then brings only
 * the window the content reads up to date, and everything the content remembered survives it.
 *
 * The content reads its [LocalWindow][org.jetbrains.compose.swing.window.LocalWindow] from the
 * composition it joins, and the window this container is in wherever that composition names none.
 *
 * The content also reads a [LifecycleOwner][androidx.lifecycle.LifecycleOwner] as its
 * [LocalLifecycleOwner][androidx.lifecycle.compose.LocalLifecycleOwner], shared with everything this
 * content hosts - a popup, a menu, an overlay. It is the owner the composition this content joins
 * carries, so an owner provided over that composition - by a caller, or by a navigation library -
 * reaches this content too. Failing that it is the owner of composed content this container hangs under,
 * a window's own content as a rule. Content that finds neither is given an owner of its own following
 * this container, and only such an owner is ended at `DESTROYED` by disposing the returned handle. A
 * [Window][org.jetbrains.compose.swing.window.Window] or
 * [Dialog][org.jetbrains.compose.swing.window.Dialog] composed in this content is a top-level window of
 * its own and states an owner of its own, whatever stands over the declaration.
 *
 * An owner reports `RESUMED` while the content it follows is in a window showing at full size that holds
 * the keyboard focus, `STARTED` while that content is shown without the focus, and `CREATED` while it has
 * no native peer - detached, or in a window Swing has not realized yet - or while its window is minimized.
 * It answers for the content it was given to follow, so content reading an owner it did not get for itself
 * reports where that window's content stands rather than where this container hangs. Since `RESUMED` is
 * the focused state, `repeatOnLifecycle(RESUMED)` ends its work the moment the user switches to another
 * window - work that should run for as long as the content is on screen belongs under `STARTED`.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param parent the composition context this content joins and shares the recomposition scope of, and
 *   the one anything mounted inside this content joins. Defaults to `null`, meaning the composition the
 *   container's own place in the Swing tree resolves to.
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes this content composition when invoked (or cancels it if it
 *   has not mounted yet). Must be disposed on the Event Dispatch Thread.
 */
public fun Container.setContent(
    parent: CompositionContext? = null,
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    val compose = { resolved: MountParent, window: State<Window?> -> mountContent(resolved, window, content) }
    return if (parent == null) {
        mountWhenParentResolves(this, compose)
    } else {
        mountUnderNamedParent(this, parent, compose)
    }
}

/**
 * Mounts [content] into this container as a content composition under [parent], stating [window] as the
 * window the content reads.
 *
 * A window's shared recomposer is a composition root rather than a composition, so it carries no
 * [androidx.compose.runtime.CompositionLocal]s and the window has to be stated here. [window] is the one
 * the content stands in and follows, so content mounted under no window reads the one it later reaches.
 */
private fun Container.mountContent(
    parent: MountParent,
    window: State<Window?>,
    content:
        @Composable @SwingComposable
        () -> Unit,
): SwingContentComposition {
    val composition =
        SwingContentComposition.nested(parent.context) { owner ->
            SwingApplier(SwingNodeHolder(this).attachedTo(owner))
        }
    composition.setContent { ProvideContentLocals(window, this, content = content) }
    return composition
}

/**
 * Hosts [content] inside [this] container as a child of an explicit [parent] [CompositionContext], so
 * descendant `setContent` calls on this container also join [parent]. Use this from external Swing
 * code that wants to host Compose content joined to an existing host composition.
 *
 * Typical use: capture the enclosing context with `rememberCompositionContext()` in a `@Composable`
 * scope and thread it here, so a detached top-level peer's content (a separate window/dialog) joins
 * the host composition and shares its [androidx.compose.runtime.CompositionLocal]s and state.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param parent the composition context this content joins and shares the recomposition scope of
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes the child composition when invoked. Must be disposed on
 *   the Event Dispatch Thread.
 */
internal fun Container.setContentAsInteropHost(
    parent: CompositionContext,
    content:
        @Composable @SwingComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    val host = this as? JComponent
    host?.setCompositionContext(parent)

    val composition =
        SwingContentComposition.nested(parent) { owner ->
            SwingApplier(SwingNodeHolder(this).attachedTo(owner))
        }
    composition.setContent(content)
    return DisposableHandle {
        checkEventDispatchThread()
        host?.setCompositionContext(null)
        composition.dispose()
    }
}

/**
 * Sets the composable [content] of a [Window] (a [javax.swing.JFrame], [javax.swing.JDialog], or
 * [javax.swing.JWindow]).
 *
 * The content is hosted on the window's content pane and joins the composition shared across that
 * window.
 *
 * Must be called on the Event Dispatch Thread.
 *
 * @param content the composable content to set
 * @return a [DisposableHandle] that disposes the composition when invoked. Must be disposed on the
 *   Event Dispatch Thread.
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
 * gains a window ancestor (when it is installed on the frame) - and the menu tree reads that window as
 * its [LocalWindow][org.jetbrains.compose.swing.window.LocalWindow], so an item's callback reaches the
 * window its menu hangs off. Must be called on the Event Dispatch Thread.
 *
 * @param content the composable menu tree (`Menu`, `MenuItem`, ...)
 * @return a [DisposableHandle] that disposes this menu-bar composition (or cancels it if it has not
 *   mounted yet). Must be disposed on the Event Dispatch Thread.
 */
@ComposableOpenTarget(-1)
public fun JMenuBar.setContent(
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    return mountWhenParentResolves(this) { parent, window ->
        val composition =
            SwingContentComposition.nestedUnobserved(parent.context) { owner ->
                MenuApplier(SwingNodeHolder(this).attachedTo(owner))
            }
        composition.setContent { ProvideContentLocals(window, this, content = content) }
        composition
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
 * @return a [DisposableHandle] that disposes the menu-bar composition when invoked. Must be disposed
 *   on the Event Dispatch Thread.
 */
@ComposableOpenTarget(-1)
internal fun JMenuBar.setContentAsMenuInteropHost(
    parent: CompositionContext,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): DisposableHandle {
    checkEventDispatchThread()

    val composition =
        SwingContentComposition.nestedUnobserved(parent) { owner ->
            MenuApplier(SwingNodeHolder(this).attachedTo(owner))
        }
    composition.setContent(content)
    return DisposableHandle { composition.dispose() }
}
