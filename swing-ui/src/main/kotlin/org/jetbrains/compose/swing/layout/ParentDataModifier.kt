package org.jetbrains.compose.swing.layout

/**
 * A parent-layout declaration that builds data its immediate parent registers for this component.
 *
 * Retained modifiers fold in declaration order and must carry one [parentProtocol] instance. Parent data
 * must be immutable because a parent layout may retain it.
 */
public interface ParentDataModifier : ParentLayoutElement {
    /** Returns this declaration applied to the [parentData] declared before it. */
    public fun modifyParentData(parentData: Any?): Any?
}
