package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import java.beans.PropertyChangeListener
import java.util.Hashtable
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSlider

/*
 * The text a `Slider` draws at its values: the map a composition declares them as, and the table the
 * slider paints - the declared entries, or the standard labels Swing generates at the major tick marks.
 */

/**
 * The labels [declared] names, held as a map of this composition's own - the map counterpart of
 * `rememberDeclaredList`, which carries why the entries are read here rather than in the apply block that
 * draws them. A `null` declaration is held as itself: there are no entries to read and none to copy.
 */
@Composable
internal fun rememberDeclaredLabels(declared: Map<Int, @Nls String>?): Map<Int, @Nls String>? {
    val held = remember { arrayOfNulls<Map<Int, String>>(1) }
    val standing = held[0]
    return if (standing != null && standing == declared) {
        standing
    } else {
        declared?.toMap().also { held[0] = it }
    }
}

/**
 * Declares the table the slider paints its labels from: [labels] where the caller names them, and
 * otherwise the standard labels [majorTickSpacing] places on the range [rangeMinimum] to [rangeMaximum].
 *
 * Declared before the painting flag is written, because `JSlider` fills an unset table in with its own
 * standard labels as soon as that flag is written. Those standard labels listen to the slider so they can
 * regenerate themselves when the range moves, and the slider drops that registration only while they are
 * still the table it holds - so the outgoing table's registration leaves with it here, or a table nothing
 * renders would keep rewriting a declared map, and would fail outright on a range change once there is no
 * table left for it to regenerate.
 */
internal fun SwingNodeUpdater<JSlider>.declareLabels(
    labels: Map<Int, @Nls String>?,
    majorTickSpacing: Int,
    rangeMinimum: Int,
    rangeMaximum: Int,
) {
    set(LabelDeclaration(labels, majorTickSpacing, rangeMinimum, rangeMaximum)) { declaration ->
        (labelTable as? PropertyChangeListener)?.let { removePropertyChangeListener(it) }
        this.labelTable = declaration.labels?.toLabelTable() ?: standardLabels()
    }
}

/**
 * What the labels a slider paints are derived from: the declared map, or - where none is declared - the
 * major tick spacing and the range Swing's own labels are generated over.
 */
private data class LabelDeclaration(
    val labels: Map<Int, @Nls String>?,
    val majorTickSpacing: Int,
    val rangeMinimum: Int,
    val rangeMaximum: Int,
)

/** The `JSlider` label table this text draws as, one [JLabel] per entry. */
private fun Map<Int, @Nls String>.toLabelTable(): Hashtable<Int, JComponent> {
    val table = Hashtable<Int, JComponent>()
    for ((value, text) in this) table[value] = JLabel(text)
    return table
}

/**
 * The standard labels a `JSlider` puts at its major tick marks, or `null` when there is no major tick
 * spacing to place them at.
 */
private fun JSlider.standardLabels(): Hashtable<Int, JComponent>? =
    if (majorTickSpacing > 0) createStandardLabels(majorTickSpacing) else null
