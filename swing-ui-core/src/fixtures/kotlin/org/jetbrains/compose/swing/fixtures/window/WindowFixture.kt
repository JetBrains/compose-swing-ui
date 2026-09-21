// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.window

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.WindowPropertiesFixture
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowPosition
import org.jetbrains.compose.swing.window.WindowState
import java.awt.Dimension
import java.awt.Image
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.WindowConstants

/** The parameters [WindowFixture] varies. */
public data class WindowParameters(
    val onCloseRequest: () -> Unit,
    val state: WindowState,
    val title: String,
    val visible: Boolean,
    val resizable: Boolean,
    val alwaysOnTop: Boolean,
    val iconImage: Image?,
    val minimumSize: Dimension?,
    val undecorated: Boolean,
    val contentText: String,
)

/** A [Window] showing a [Label] against a raw [JFrame] showing a [JLabel] with the same text. */
public object WindowFixture :
    ComposableFixture<WindowParameters, JFrame>(
        initial =
            WindowParameters(
                onCloseRequest = {},
                state = WindowState(size = Dimension(200, 100)),
                title = "Before",
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
    /** The alternatives in which each parameter changes on its own from [initial]. */
    override val alternatives: List<WindowParameters> =
        listOf(
            initial.copy(onCloseRequest = {}),
            initial.copy(state = WindowState(size = Dimension(220, 120))),
            initial.copy(title = "After"),
            initial.copy(visible = true),
            initial.copy(resizable = false),
            initial.copy(alwaysOnTop = true),
            initial.copy(iconImage = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)),
            initial.copy(minimumSize = Dimension(100, 80)),
            initial.copy(undecorated = true),
            initial.copy(contentText = "After"),
        )

    @Composable
    override fun Content(parameters: WindowParameters): Unit =
        Content(
            onCloseRequest = parameters.onCloseRequest,
            state = parameters.state,
            title = parameters.title,
            visible = parameters.visible,
            resizable = parameters.resizable,
            alwaysOnTop = parameters.alwaysOnTop,
            iconImage = parameters.iconImage,
            minimumSize = parameters.minimumSize,
            undecorated = parameters.undecorated,
            contentText = parameters.contentText,
        )

    /** Composes a [Window] declared with the given values, whose content is a [Label] showing [contentText]. */
    @Composable
    public fun Content(
        onCloseRequest: () -> Unit,
        state: WindowState,
        title: String,
        visible: Boolean,
        resizable: Boolean,
        alwaysOnTop: Boolean,
        iconImage: Image?,
        minimumSize: Dimension?,
        undecorated: Boolean,
        contentText: String,
    ) {
        Window(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
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
     * Builds a raw frame that does not close on its own, so that a close request reaches its listeners, holding
     * the content label.
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            contentPane.add(JLabel())
        }

    /** Applies [parameters] to [component], so that it shows the window that [Content] declares with them. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: WindowParameters,
    ) {
        val existing = component.windowListeners.filterIsInstance<ForwardingWindowListener>().firstOrNull()
        if (existing == null) {
            component.addWindowListener(ForwardingWindowListener(parameters.onCloseRequest))
        } else {
            existing.onCloseRequest = parameters.onCloseRequest
        }
        component.title = parameters.title
        component.isResizable = parameters.resizable
        if (component.isAlwaysOnTopSupported) component.isAlwaysOnTop = parameters.alwaysOnTop
        component.iconImage = parameters.iconImage
        component.minimumSize = parameters.minimumSize
        component.size = parameters.state.size
        component.extendedState = parameters.state.extendedState
        component.place(parameters.state.position)
        if (component.isUndecorated != parameters.undecorated) {
            component.dispose()
            component.isUndecorated = parameters.undecorated
        }
        val contentPane = component.contentPane
        contentPane.components.filterIsInstance<JLabel>().forEach { it.text = parameters.contentText }
        component.isVisible = parameters.visible
    }
}

/** Places this window the way [position] declares, resolving a centering position against what it names. */
internal fun Window.place(position: WindowPosition) {
    when (position) {
        WindowPosition.PlatformDefault -> Unit
        WindowPosition.CenteredOnScreen -> setLocationRelativeTo(null)
        WindowPosition.CenteredOnOwner -> setLocationRelativeTo(owner)
        is WindowPosition.CenteredOn -> setLocationRelativeTo(position.window)
        is WindowPosition.Absolute -> setLocation(position.x, position.y)
    }
}

/** The window listener [WindowFixture] installs on its raw frame, so that it removes only its own. */
private class ForwardingWindowListener(
    var onCloseRequest: () -> Unit,
) : WindowAdapter() {
    override fun windowClosing(event: WindowEvent) {
        onCloseRequest()
    }
}
