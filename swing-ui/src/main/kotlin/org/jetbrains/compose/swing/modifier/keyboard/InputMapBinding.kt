package org.jetbrains.compose.swing.modifier.keyboard

import javax.swing.Action
import javax.swing.ActionMap
import javax.swing.InputMap
import javax.swing.KeyStroke

/**
 * An entry in an input map that, on removal, puts back the nearest earlier entry that is still installed,
 * or removes the stroke so an inherited binding applies. The binding also owns its ActionMap entry.
 */
internal class InputMapBinding(
    private val inputMap: InputMap,
    private val stroke: KeyStroke,
    private val actionMap: ActionMap,
    private val action: Action,
) {
    private var previous: Any? = null
    private var active = false

    fun install() {
        previous = if (inputMap.keys()?.contains(stroke) == true) inputMap.get(stroke) else null
        active = true
        inputMap.put(stroke, this)
        actionMap.put(this, action)
    }

    fun uninstall() {
        active = false
        if (actionMap.get(this) === action) actionMap.remove(this)
        if (inputMap.get(stroke) !== this) return
        var restored = previous
        // A binding that was replaced can be removed before the one that replaced it. Its own
        // previous entry is then the one to put back.
        while (restored is InputMapBinding && !restored.active) restored = restored.previous
        if (restored == null) inputMap.remove(stroke) else inputMap.put(stroke, restored)
    }
}
