@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Image
import java.awt.Toolkit
import javax.swing.JDialog
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/**
 * A dialog window, realized as a `JDialog`: it shows [content], holds its geometry in [state], blocks
 * input to the top-level windows in its [modality]'s scope of blocking, and reports the user's attempt
 * to close it to [onCloseRequest].
 *
 * The close gesture is controlled: it invokes [onCloseRequest] and closes nothing, so the dialog stays
 * on screen - and a modal one goes on blocking - until the caller answers, by declaring [visible]
 * `false` or by stopping declaring the dialog at all.
 *
 * The declarative control surface mirrors [Window] (`visible` + `onCloseRequest`, no imperative
 * handle), adding owner resolution and AWT [modality]. The dialog is owned by [owner], which defaults
 * to the nearest enclosing [Window] read from [LocalWindow]. The owner is one of the windows a modal
 * dialog blocks, and the one a [WindowPosition.CenteredOnOwner] position centers the dialog on. A
 * dialog with no [owner] under a bare `application { }` scope, which creates no window, is ownerless:
 * an ownerless document-modal dialog is its own document root, so its scope of blocking is empty and it
 * behaves as a modeless one, while application- and toolkit-modal blocking does not depend on an owner.
 * Centering on the owner then resolves against the screen.
 *
 * The dialog content runs as part of the enclosing (application or window) composition: state from
 * that scope and any [androidx.compose.runtime.CompositionLocal] provided above the dialog flow into
 * [content]. Descendants of the dialog read it as their [LocalWindow]. A shown dialog keeps
 * recomposing while it is visible.
 *
 * [content] receives the dialog as its [WindowScope]: what the dialog carries besides its content - its
 * [MenuBar] - is declared on that scope.
 *
 * The [title], [modality], [visible], [resizable], [alwaysOnTop], [iconImage] and [minimumSize]
 * arguments are reactive: changing any of them in a recomposition updates the realized dialog
 * accordingly. A [modality] change may have no effect on a dialog that is already showing until it is
 * hidden and shown again. Geometry is driven by [state], which is two-way: assigning to
 * [DialogState.position]/[DialogState.size] repositions or resizes the dialog, and a user dragging or
 * resizing the dialog writes the new geometry back into [state].
 *
 * [undecorated] and the owning window are reactive too, at a higher price: a dialog takes its owner at
 * construction and AWT only accepts decorations on a dialog that is not yet realized, so declaring
 * another owner or another decoration releases the dialog peer and builds a replacement. The content is
 * re-hosted in the new peer - its composition, and any state remembered in it, starts over - while the
 * geometry held in [state] and the declared visibility are re-applied to the replacement. A look and
 * feel that draws the dialog decorations itself (see [JDialog.setDefaultLookAndFeelDecorated]) draws
 * them on the replacement too, whatever [undecorated] declares.
 *
 * @param onCloseRequest callback to be called when the user attempts to close the dialog
 * @param state the hoistable, observable geometry (position and size) of the dialog; by default one
 *   this dialog keeps to itself, which leaves the placement to the platform and sizes the dialog to
 *   its content
 * @param owner the window that owns the dialog, or null to take the nearest enclosing [Window] from
 *   [LocalWindow]
 * @param title the title of the dialog, empty by default, matching a freshly constructed `JDialog`
 * @param modality the AWT modality of the dialog, defaulting to [Dialog.ModalityType.MODELESS] (the
 *   JDialog default), which blocks nothing; each other type names its own scope of blocking, which
 *   never covers the dialog's own child hierarchy, and a modality type the toolkit does not support
 *   leaves the dialog modeless
 * @param visible whether the dialog should be visible; `true` by default, so declaring a dialog shows
 *   it, and `false` hides the dialog while keeping its content composed
 * @param resizable whether the dialog can be resized; `true` by default, matching a freshly
 *   constructed `JDialog`
 * @param alwaysOnTop whether the dialog stays above other windows; ignored on platforms that do not
 *   support an always-on-top window, and `false` by default, so the dialog takes its turn in the
 *   platform's stacking order
 * @param iconImage the image shown as the dialog's icon, or null for the platform default; the
 *   windowing system may show it in several places at sizes of its own, or show none at all
 * @param minimumSize the smallest size the dialog can take, or null to leave the floor to the dialog's
 *   layout; a declared size below the floor is raised to it, while holding the user's own resizing to
 *   the floor is platform-dependent
 * @param undecorated whether the dialog is shown without its platform decorations (title bar and
 *   border); `false` by default, so the dialog is shown with them
 * @param content the composable content of the dialog, receiving the dialog as its [WindowScope]
 * @see javax.swing.JDialog
 */
@Composable
public fun Dialog(
    onCloseRequest: () -> Unit,
    state: DialogState = rememberDialogState(),
    owner: java.awt.Window? = null,
    title: @Nls String = "",
    modality: Dialog.ModalityType = Dialog.ModalityType.MODELESS,
    visible: Boolean = true,
    resizable: Boolean = true,
    alwaysOnTop: Boolean = false,
    iconImage: Image? = null,
    minimumSize: Dimension? = null,
    undecorated: Boolean = false,
    content: @Composable WindowScope.() -> Unit,
) {
    // A named owner stands on its own: the enclosing window is read only where none is named, so a
    // dialog that names its owner is left alone by whichever window it happens to be composed under.
    val owningWindow = owner ?: LocalWindow.current

    // Only an explicit undecorated declaration is written, so a dialog that decorates itself through its
    // look and feel keeps both the decoration style and the undecorated flag that pairing needs. Modality
    // is left out of the key below: AWT accepts a modality change on a built dialog, so the reactive apply
    // carries it instead of rebuilding. The constructor still takes the declaration, so a peer realized
    // before the dialog is first shown - sizing to content realizes one - is created with the modality the
    // dialog declares.
    val dialog =
        remember(owningWindow, undecorated) {
            JDialog(owningWindow, modality).also {
                if (undecorated) it.isUndecorated = true
                it.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            }
        }

    // Holds the geometry that is currently in sync between [state] and the realized dialog. Shared by
    // the apply and the write-back listener so the two directions never fight, and tied to the dialog it
    // describes: a replacement dialog starts out of sync and is given the geometry [state] holds.
    val appliedGeometry = remember(dialog) { AppliedGeometry() }

    // Tracks the latest visibility requested on the dialog, or null while there is no request to honor.
    // A modal show is deferred to a later EDT tick, so the realized isVisible lags the request: reading
    // the peer instead would re-schedule the same show on every recomposition that lands before the
    // deferred runnable executes.
    // Cleared when the dialog leaves the composition, which retires any still-queued transition. Tied to
    // the dialog it tracks: a replacement dialog starts with no request, so the declared visibility is
    // applied to it afresh.
    val requestedVisible = remember(dialog) { arrayOfNulls<Boolean>(1) }

    CompositionOwnedWindowHost(
        peer = dialog,
        onCloseRequest = onCloseRequest,
        title = title,
        resizable = resizable,
        alwaysOnTop = alwaysOnTop,
        iconImage = iconImage,
        minimumSize = minimumSize,
        applyDeclaredGeometry = { dialog.applyGeometry(state.position, state.width, state.height, appliedGeometry) },
        setPosition = { state.position = it },
        setSize = { width, height ->
            state.width = width
            state.height = height
        },
        appliedGeometry = appliedGeometry,
        installExtras = {
            // A dialog is not a frame and carries no extended state, so no extended-state write-back
            // listener is installed; the only teardown owed is retiring a still-queued visibility
            // transition, whose apply to the disposed dialog would realize a fresh peer with no content
            // or listeners.
            val retireQueuedShow = { requestedVisible[0] = null }
            retireQueuedShow
        },
        applyExtras = {
            // Modality is written before the visibility flip, so a dialog about to be shown is shown
            // with the modality it declares.
            dialog.applyModality(modality)
            if (requestedVisible[0] != visible) {
                requestedVisible[0] = visible
                // Defer the show to a fresh EDT tick: a modal setVisible(true) blocks inside a nested
                // secondary event loop until the dialog hides, so it must run on a plain runnable rather
                // than inline in this effect body. The parent recomposer's continuations and frame-clock
                // timer firings are pumped by that same loop and keep recomposing the dialog content while
                // it shows. By the time the runnable runs, a newer request or a disposal may have retired
                // this one; a retired transition must not touch the peer.
                SwingUtilities.invokeLater {
                    if (requestedVisible[0] == visible) dialog.isVisible = visible
                }
            }
        },
        disposePeer = {
            // Hide before releasing the peer so a show that is already executing (a modal show blocks
            // inside a nested event loop, which also pumps this disposal) returns cleanly.
            dialog.isVisible = false
            dialog.dispose()
        },
        content = content,
    )
}

/**
 * Pushes the declared [modality] onto this dialog when it differs from the modality the dialog already
 * carries. A type the toolkit does not support resolves to [Dialog.ModalityType.MODELESS], the same
 * substitution [java.awt.Dialog.setModalityType] applies, so the declaration is compared against the
 * modality the dialog will actually take and settles instead of being rewritten on every recomposition.
 */
private fun JDialog.applyModality(modality: Dialog.ModalityType) {
    val effective =
        if (Toolkit.getDefaultToolkit().isModalityTypeSupported(modality)) {
            modality
        } else {
            Dialog.ModalityType.MODELESS
        }
    if (modalityType != effective) modalityType = effective
}
