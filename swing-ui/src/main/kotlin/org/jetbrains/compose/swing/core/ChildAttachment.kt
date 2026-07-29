package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.compose.swing.annotations.SwingComposable

/**
 * Adds the components [content] emits to their parent container under [constraint] - the value
 * `Container.add(Component, Object)` takes: a `BorderLayout` region name, a `GridBagConstraints`, a
 * `CardLayout` card name, or whatever the enclosing container's layout manager understands.
 *
 * This is how a container composable places children it knows nothing about. The container supplies
 * the layout manager and wraps each child it composes in the placement that child is to be added
 * under, which is also where the constraint's own type belongs:
 *
 * ```
 * @Composable
 * fun MosaicPanel(block: MosaicScope.() -> Unit) {
 *     val scope = MosaicScopeImpl().apply(block)
 *     SwingNode(
 *         factory = { JPanel(MosaicLayout()) },
 *         content = {
 *             scope.cells.forEach { (cell, content) ->
 *                 SwingConstraint(cell) { content() }
 *             }
 *         },
 *     )
 * }
 * ```
 *
 * The placement follows the value: change it and the component moves within the same parent, keeping
 * its position among its siblings. It reaches the components [content] emits directly; a container
 * composed in [content] takes the constraint for its own placement and lays its children out itself,
 * under the constraints it provides them. Nesting a further [SwingConstraint] inside [content] places
 * the components it wraps under the inner value.
 *
 * The value is handed over the way `Container.add(Component, Object)` hands it over: a
 * `LayoutManager2` receives it as-is and reports a value it does not understand with its own
 * exception, while a manager that takes no constraints places the component by index.
 *
 * @param constraint the placement the parent container's layout manager registers each component under.
 * @param content emits the components placed under [constraint].
 */
@Composable
public fun SwingConstraint(
    constraint: Any,
    content:
        @Composable @SwingComposable
        () -> Unit,
) {
    CompositionLocalProvider(LocalSwingConstraint provides constraint) {
        content()
    }
}

/**
 * The parent-container layout constraint a child Swing node should be added with.
 *
 * A container provides the placement for the children it composes - a `BorderPanel` its region per
 * slot, any container [SwingConstraint] - and `SwingNode` records the value on the node (see
 * `SwingNodeHolder.constraint`) so the applier adds the component with that constraint. This lets the
 * parent decide placement without the child knowing its container's layout manager.
 *
 * A container node consumes the value for its OWN placement and provides the default again to its
 * content, so a constraint never reaches past the children the container that declared it composes.
 *
 * Defaults to `null`, meaning "add by index" (no explicit constraint).
 */
@PublishedApi
internal val LocalSwingConstraint: ProvidableCompositionLocal<Any?> = staticCompositionLocalOf { null }

/**
 * Hosts a single-node region in a Swing component that owns its own attachment slot - a host whose
 * children go through a dedicated setter rather than the generic `Container.add`. The canonical case
 * is `JScrollPane`, whose viewport / header / corner regions are each reached via `setViewportView` /
 * `setRowHeaderView` / `setColumnHeaderView` / `setCorner`.
 *
 * This is how a container composable wraps such a host: build a [SlotAttachment] that installs and
 * uninstalls a component through the setter its region belongs to, and provide it here around the
 * composable that declares that region's single child.
 *
 * Provides [attachment] through [LocalSlotAttachment] and composes [content]; the single `SwingNode`
 * that [content] emits is installed into the host through the attachment and uninstalled on removal.
 * [content] must emit exactly one node - each such slot hosts a single view.
 *
 * A host's children are one index space, and the two kinds are reached through different Swing calls, so
 * a host whose children fill slots may hold no children added by index alongside them - a host node that
 * declares slots declares nothing else. Mixing the two fails where the second kind is added, naming the
 * host.
 *
 * @param attachment installs the region's node into the host and returns its uninstall action.
 * @param content emits the single node whose component fills the slot.
 */
@Composable
public inline fun SlotNode(
    attachment: SlotAttachment,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    CompositionLocalProvider(LocalSlotAttachment provides attachment) {
        content()
    }
}

/**
 * Carries a [SlotAttachment] down to the single `SwingNode` a slot hosts, the way [LocalSwingConstraint]
 * carries a layout constraint.
 *
 * A parent that owns a Swing host with dedicated single-occupancy slots (e.g. a `JScrollPane`'s
 * viewport / header / corner regions) provides this via [SlotNode]. `SwingNode` reads it into
 * `SwingNodeHolder.slotAttachment` so the applier installs the component through the attachment
 * instead of the generic `Container.add`. This lets the parent dictate how its content is attached
 * without the child knowing the host.
 *
 * Defaults to `null`, meaning "add as an ordinary child by index".
 */
@PublishedApi
internal val LocalSlotAttachment: ProvidableCompositionLocal<SlotAttachment?> =
    staticCompositionLocalOf { null }
