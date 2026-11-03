// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.window

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.WindowPropertiesFixture
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.DialogState
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.Image
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.WindowConstants

/** The parameters [DialogFixture] varies. */
public data class DialogParameters(
    val onCloseRequest: () -> Unit,
    val state: DialogState,
    val title: String,
    val modality: ModalityType,
    val visible: Boolean,
    val resizable: Boolean,
    val alwaysOnTop: Boolean,
    val iconImage: Image?,
    val minimumSize: Dimension?,
    val undecorated: Boolean,
    val contentText: String,
)

/** A [Dialog] showing a [Label] against a raw [JDialog] showing a [JLabel] with the same text. */
public object DialogFixture :
    ComposableFixture<DialogParameters, JDialog>(
        initial =
            DialogParameters(
                onCloseRequest = {},
                state = DialogState(size = Dimension(200, 100)),
                title = "Before",
                modality = ModalityType.MODELESS,
                visible = false,
                resizable = true,
                alwaysOnTop = false,
                iconImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                minimumSize = Dimension(80, 60),
                undecorated = false,
                contentText = "Before",
            ),
    ),
    WindowPropertiesFixture {
    /** The alternatives in which each parameter that can change on a built dialog changes on its own from [initial]. */
    override val alternatives: List<DialogParameters> =
        listOf(
            initial.copy(onCloseRequest = {}),
            initial.copy(state = DialogState(size = Dimension(220, 120))),
            initial.copy(title = "After"),
            initial.copy(modality = ModalityType.DOCUMENT_MODAL),
            initial.copy(visible = true),
            initial.copy(resizable = false),
            initial.copy(alwaysOnTop = true),
            initial.copy(iconImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)),
            initial.copy(minimumSize = Dimension(100, 80)),
            initial.copy(undecorated = true),
            initial.copy(contentText = "After"),
        )

    @Composable
    override fun Content(parameters: DialogParameters): Unit =
        Content(
            onCloseRequest = parameters.onCloseRequest,
            state = parameters.state,
            title = parameters.title,
            modality = parameters.modality,
            visible = parameters.visible,
            resizable = parameters.resizable,
            alwaysOnTop = parameters.alwaysOnTop,
            iconImage = parameters.iconImage,
            minimumSize = parameters.minimumSize,
            undecorated = parameters.undecorated,
            contentText = parameters.contentText,
        )

    /** Composes a [Dialog] declared with the given values, whose content is a [Label] showing [contentText]. */
    @Composable
    public fun Content(
        onCloseRequest: () -> Unit,
        state: DialogState,
        title: String,
        modality: ModalityType,
        visible: Boolean,
        resizable: Boolean,
        alwaysOnTop: Boolean,
        iconImage: Image?,
        minimumSize: Dimension?,
        undecorated: Boolean,
        contentText: String,
    ) {
        Dialog(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            modality = modality,
            visible = visible,
            resizable = resizable,
            alwaysOnTop = alwaysOnTop,
            iconImage = iconImage,
            minimumSize = minimumSize,
            undecorated = undecorated,
        ) {
            Label(text = contentText)
        }
    }

    /**
     * Builds a raw dialog with no owner that does not close on its own, so that a close request reaches
     * its listeners, holding the content label.
     */
    override fun createRawSwing(): JDialog =
        JDialog(null as Window?).apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            contentPane.add(JLabel())
        }

    /** Applies [parameters] to [component], so that it shows the dialog that [Content] declares with them. */
    override fun updateRawSwing(
        component: JDialog,
        parameters: DialogParameters,
    ) {
        val listener =
            component.windowListeners.filterIsInstance<CloseRequestListener>().firstOrNull()
                ?: CloseRequestListener(parameters.onCloseRequest).also { component.addWindowListener(it) }
        listener.onCloseRequest = parameters.onCloseRequest
        component.title = parameters.title
        component.modalityType = parameters.modality
        component.isResizable = parameters.resizable
        if (component.isAlwaysOnTopSupported) component.isAlwaysOnTop = parameters.alwaysOnTop
        component.setIconImage(parameters.iconImage)
        component.minimumSize = parameters.minimumSize
        component.size = parameters.state.size
        component.place(parameters.state.position)
        if (component.isUndecorated != parameters.undecorated) {
            component.dispose()
            component.isUndecorated = parameters.undecorated
        }
        val contentPane = component.contentPane
        contentPane.components.filterIsInstance<JLabel>().forEach { it.text = parameters.contentText }
        component.isVisible = parameters.visible
    }

    /** Forwards a window closing to the close request the fixture declares. */
    private class CloseRequestListener(
        var onCloseRequest: () -> Unit,
    ) : WindowAdapter() {
        override fun windowClosing(event: WindowEvent) {
            onCloseRequest()
        }
    }
}
