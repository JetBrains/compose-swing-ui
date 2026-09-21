// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SplitOrientation
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.fixtures.components.defaultOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.foreground
import java.awt.Color
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JLabel
import javax.swing.JSplitPane

/**
 * The parameters [SplitPaneFixture] varies: the enabled state, the foreground color, the orientation, the
 * divider location, the divider callback, the resize weight, the one-touch expandable flag, the divider size,
 * the continuous layout flag and the text of the first side.
 */
public data class SplitPaneParameters(
    val enabled: Boolean,
    val color: Color,
    @param:SplitOrientation val orientation: Int,
    val dividerLocation: Int,
    val onDividerLocationChange: (Int) -> Unit,
    val resizeWeight: Double,
    val oneTouchExpandable: Boolean?,
    val dividerSize: Int?,
    val continuousLayout: Boolean?,
    val firstText: String,
)

/**
 * A [SplitPane] taking an `onDividerLocationChange` callback against a raw [JSplitPane] with two label sides,
 * in the same enabled state, color, orientation, divider location, resize weight, one-touch expandable flag,
 * divider size, continuous layout flag and first side text. The raw side forwards divider moves to the
 * callback through a listener of its own.
 */
public object SplitPaneFixture : ComposableFixture<SplitPaneParameters, JSplitPane>(
    initial =
        SplitPaneParameters(
            enabled = true,
            color = Color.BLUE,
            orientation = JSplitPane.HORIZONTAL_SPLIT,
            dividerLocation = 40,
            onDividerLocationChange = {},
            resizeWeight = 0.25,
            oneTouchExpandable = false,
            dividerSize = 8,
            continuousLayout = false,
            firstText = "Before",
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<SplitPaneParameters> =
        listOf(
            initial.copy(enabled = false),
            initial.copy(color = Color.RED),
            initial.copy(orientation = JSplitPane.VERTICAL_SPLIT),
            initial.copy(dividerLocation = 80),
            initial.copy(onDividerLocationChange = {}),
            initial.copy(resizeWeight = 0.75),
            initial.copy(oneTouchExpandable = true),
            initial.copy(dividerSize = 12),
            initial.copy(continuousLayout = true),
            initial.copy(firstText = "After"),
        )

    @Composable
    override fun Content(parameters: SplitPaneParameters): Unit =
        Content(
            parameters.enabled,
            parameters.color,
            parameters.orientation,
            parameters.dividerLocation,
            parameters.onDividerLocationChange,
            parameters.resizeWeight,
            parameters.oneTouchExpandable,
            parameters.dividerSize,
            parameters.continuousLayout,
            parameters.firstText,
        )

    /**
     * Composes a [SplitPane] enabled as [enabled], drawn in [color], arranged by [orientation], with its divider
     * at [dividerLocation] reporting moves to [onDividerLocationChange], sharing extra space by [resizeWeight],
     * with [oneTouchExpandable], [dividerSize] and [continuousLayout] as declared, and with a first side showing
     * [firstText] beside a second side.
     */
    @Composable
    public fun Content(
        enabled: Boolean,
        color: Color,
        @SplitOrientation orientation: Int,
        dividerLocation: Int,
        onDividerLocationChange: (Int) -> Unit,
        resizeWeight: Double,
        oneTouchExpandable: Boolean?,
        dividerSize: Int?,
        continuousLayout: Boolean?,
        firstText: String,
    ) {
        SplitPane(
            style =
                SwingStyle {
                    enabled(enabled)
                    foreground(color)
                },
            orientation = orientation,
            dividerLocation = dividerLocation,
            onDividerLocationChange = onDividerLocationChange,
            resizeWeight = resizeWeight,
            oneTouchExpandable = oneTouchExpandable,
            dividerSize = dividerSize,
            continuousLayout = continuousLayout,
        ) {
            Label(firstText, modifier = SwingModifier.first())
            Label("Second", modifier = SwingModifier.second())
        }
    }

    /** Builds a bare [JSplitPane] with both sides empty before any of the fixture's values are applied. */
    override fun createRawSwing(): JSplitPane =
        JSplitPane(JSplitPane.HORIZONTAL_SPLIT, null, null).also {
            it.putClientProperty(ONE_TOUCH_EXPANDABLE, it.isOneTouchExpandable)
            it.putClientProperty(DIVIDER_SIZE, it.dividerSize)
            it.putClientProperty(CONTINUOUS_LAYOUT, it.isContinuousLayout)
        }

    /**
     * Applies the enabled state, color, orientation, divider location, divider callback, resize weight,
     * one-touch expandable flag, divider size, continuous layout flag and first side text of [parameters] to
     * [component]. A flag or size that is withdrawn goes back to the one the pane was created with, and a change of
     * the first text reaches the first label alone. The divider listener stays attached and does not report the
     * location written here.
     */
    override fun updateRawSwing(
        component: JSplitPane,
        parameters: SplitPaneParameters,
    ) {
        component.isEnabled = parameters.enabled
        component.foreground = parameters.color
        component.orientation = parameters.orientation
        component.resizeWeight = parameters.resizeWeight
        component.isOneTouchExpandable =
            parameters.oneTouchExpandable ?: component.defaultOf<Boolean>(ONE_TOUCH_EXPANDABLE)
        component.dividerSize = parameters.dividerSize ?: component.defaultOf<Int>(DIVIDER_SIZE)
        component.isContinuousLayout = parameters.continuousLayout ?: component.defaultOf<Boolean>(CONTINUOUS_LAYOUT)
        val first = component.leftComponent as? JLabel
        if (first == null) {
            component.leftComponent = JLabel(parameters.firstText)
            component.rightComponent = JLabel("Second")
        } else {
            first.text = parameters.firstText
        }
        component.leftComponent.foreground = parameters.color
        component.rightComponent.foreground = parameters.color
        val forwarder =
            component
                .getPropertyChangeListeners(JSplitPane.DIVIDER_LOCATION_PROPERTY)
                .firstNotNullOfOrNull { it as? SplitPaneCallbackForwarder }
                ?: SplitPaneCallbackForwarder(component, parameters.onDividerLocationChange).also {
                    component.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, it)
                }
        forwarder.callback = parameters.onDividerLocationChange
        forwarder.writing = true
        try {
            component.dividerLocation = parameters.dividerLocation
        } finally {
            forwarder.writing = false
        }
    }

    private const val ONE_TOUCH_EXPANDABLE = "fixtureOneTouchExpandable"
    private const val DIVIDER_SIZE = "fixtureDividerSize"
    private const val CONTINUOUS_LAYOUT = "fixtureContinuousLayout"
}

/** A raw divider listener that hands the new divider location to a callback, except while [writing] it itself. */
private class SplitPaneCallbackForwarder(
    private val pane: JSplitPane,
    var callback: (Int) -> Unit,
) : PropertyChangeListener {
    var writing: Boolean = false

    override fun propertyChange(event: PropertyChangeEvent) {
        if (!writing) callback(pane.dividerLocation)
    }
}
