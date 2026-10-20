package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import java.awt.Component

/**
 * Identifies one installed modifier or style property declaration.
 *
 * Compare instances by reference: the same instance identifies the declaration from its first write to its
 * restore.
 *
 * Read the properties on the event dispatch thread, inside the callback that supplied this instance and before
 * invoking its action. Retain the instance for identity tracking until restoration.
 */
@InternalSwingUiApi
public interface ModifierInfo {
    /** The component this declaration modifies. */
    public val component: Component

    /** The element currently assigned to this declaration. */
    public val modifier: SwingModifier.NodeElement<*, *>
}
