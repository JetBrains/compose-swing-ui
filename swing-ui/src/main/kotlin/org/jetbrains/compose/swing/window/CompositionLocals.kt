@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableOpenTarget
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.jetbrains.compose.swing.core.PublishLifecycleOwner
import org.jetbrains.compose.swing.core.rememberLifecycleOwner
import java.awt.Component
import java.awt.Window

/**
 * The owning AWT [Window] of the current subtree.
 *
 * Every composition the library roots in a window carries it: the [javax.swing.JFrame] or [javax.swing.JDialog] behind
 * a composed [Window] or [Dialog], and the window a `setContent` content composition is attached to. A composition
 * nested in another inherits the value of the one it joins. The window its own container hangs in answers only where
 * that composition names none, so content composed away from any window (a list cell renderer's rows, a menu shown
 * from a popup) keeps reading the window it was composed under rather than whichever transient peer it is painted
 * into.
 *
 * This is the parent to hand plain Swing API that needs one - `JFileChooser`, `JOptionPane` - and the
 * window to bring to the front or query for its screen.
 *
 * Content given a caller's own composition to compose under, before its container is added anywhere, reads `null`
 * until that container reaches a window. It reads that window from then on, without being composed again.
 *
 * The value is `null` wherever the content stands in no window at all, such as a bare `application { }` scope that
 * has created none.
 */
public val LocalWindow: CompositionLocal<Window?>
    get() = LocalProvidableWindow

/**
 * The providable side of [LocalWindow], which stays the library's to state: the owning window is read off
 * the composition or the Swing tree, and a subtree that declared a different one would hand a dialog an
 * owner it does not live under.
 *
 * A composition root provides it computed rather than by value. The computation reads snapshot state, so a window
 * arriving invalidates the places that read the window rather than the provision. That is what lets the local be
 * static: the provision itself does not change while content is on screen.
 */
internal val LocalProvidableWindow: ProvidableCompositionLocal<Window?> = staticCompositionLocalOf { null }

/**
 * Composes [content] as a content composition mounted in a window it does not own: a `setContent` on a
 * container or a menu bar.
 *
 * Both locals are inherited first, and answered from where [component] hangs only where that inheritance
 * says nothing. That is what leaves [LocalLifecycleOwner] overridable from outside the library - by a
 * caller, or by a navigation library - since a content composition defers to whatever stands above it.
 *
 * [window] is the window the content composition has settled on, not one resolved while composing, so one
 * that already has a window stands under it from its first pass. It is observable, so a content
 * composition that inherited none reads the window its container reaches once it arrives there.
 */
@Composable
@ComposableOpenTarget(-1)
internal fun ProvideContentLocals(
    window: State<Window?>,
    component: Component,
    content:
        @Composable
        @ComposableOpenTarget(-1)
        () -> Unit,
) {
    // Read outside the provision: inside it, this local already resolves to this computation, so asking
    // for it there would ask this computation for its own answer.
    val joined = LocalProvidableWindow.current
    CompositionLocalProvider(
        LocalProvidableWindow providesComputed { joined ?: window.value },
        LocalLifecycleOwner providesDefault rememberLifecycleOwner(component),
    ) {
        PublishLifecycleOwner(component)
        content()
    }
}

/**
 * Composes [content] as the content of the top-level [window] it stands in, which that window owns.
 *
 * Such a root states both locals outright rather than inheriting them. A composed window's content is a
 * child composition of the one the window was declared in, so a dialog that inherited would read the
 * frame behind it. Attachment, minimization and focus are facts about a single window, and a dialog
 * holds exactly the focus its owner gives up: a dialog reading the frame's owner would report itself
 * unshown while the user is looking at it.
 *
 * A caller wanting another [LocalLifecycleOwner] inside such a window provides one within its content.
 */
@Composable
@ComposableOpenTarget(-1)
internal fun ProvideWindowLocals(
    window: State<Window?>,
    component: Component,
    content:
        @Composable
        @ComposableOpenTarget(-1)
        () -> Unit,
) {
    CompositionLocalProvider(
        LocalProvidableWindow providesComputed { window.value },
        LocalLifecycleOwner provides rememberLifecycleOwner(component),
    ) {
        PublishLifecycleOwner(component)
        content()
    }
}
