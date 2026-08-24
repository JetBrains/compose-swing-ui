package org.jetbrains.compose.swing.modifier.listener

import javax.swing.JColorChooser
import javax.swing.colorchooser.ColorSelectionModel
import javax.swing.event.ChangeListener

// A color chooser publishes its change events through its selection model, which a caller can replace.
private val COLOR_CHOOSER_SELECTION =
    SwappableModel<JColorChooser, ColorSelectionModel, ChangeListener>(
        property = JColorChooser.SELECTION_MODEL_PROPERTY,
        modelType = ColorSelectionModel::class.java,
        model = JColorChooser::getSelectionModel,
        add = ColorSelectionModel::addChangeListener,
        remove = ColorSelectionModel::removeChangeListener,
    )

/** Adds [changeListener] to the selection model the receiver holds, and follows it across a swap. */
internal fun JColorChooser.attachSwappableChangeListener(changeListener: ChangeListener): Unit =
    COLOR_CHOOSER_SELECTION.attach(this, changeListener)

/** Undoes [attachSwappableChangeListener]. */
internal fun JColorChooser.detachSwappableChangeListener(changeListener: ChangeListener): Unit =
    COLOR_CHOOSER_SELECTION.detach(this, changeListener)
