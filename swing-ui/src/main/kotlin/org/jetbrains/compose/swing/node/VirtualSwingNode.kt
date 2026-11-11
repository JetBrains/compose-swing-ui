// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentComposer
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.layout.MeasurementLayoutManager
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.layout.ParentLayoutElement
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.requireInheritableIsNotAdditive
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.materialize
import org.jetbrains.compose.swing.modifier.requireSingleElementRole
import org.jetbrains.compose.swing.modifier.retainedParentDeclarations

/**
 * Emits [content] into the container this node is composed in, as if [content] were written there, and declares
 * [modifier] to that container for each component [content] places in it.
 *
 * A virtual node has no component. Its children are added to the nearest ancestor that has one, in composition
 * order among that ancestor's other children. That ancestor's layout manager receives this node's declarations
 * ahead of each child's own. Nested groups declare in order from outermost to innermost.
 * The group declarations and each child's own declarations form one list. For non-additive declarations, a later
 * declaration replaces an earlier declaration of the same key. Additive declarations accumulate. Parent data in
 * the list must use the same protocol. The container checks exactly the declarations it receives, so a declaration
 * a later one replaces is not checked.
 * Declarations reach no further: the children of a child are added to the child's container and never receive them.
 *
 * A changed [modifier] reaches each component this node places in composition order, once the pass's other changes
 * have been applied; a child added in that pass is added to the container then, with its final declaration. A
 * declaration the container refuses fails the pass, and the components declared before it keep the new one.
 *
 * A group is refused under a container that places children in named regions, including a composition root
 * with a single-content slot. An ordinary `setContent` root accepts groups among its indexed children.
 * An `ExistingSwingNode` directly inside a group is refused. A group cannot be used in menu content.
 * A group that declares parent data is refused under a container with no layout manager. Each child can still
 * declare its own parent data there.
 *
 * Under a `LayoutManager2` that is not a [MeasurementLayoutManager], the group's folded parent data is the `add`
 * constraint of each child, so `VirtualSwingNode(SwingModifier.layoutConstraint(c)) { ... }` gives every child
 * the constraint `c`.
 *
 * @param modifier parent-layout declarations only: [ParentLayoutElement]s that are not
 *   [ParentLayoutNodeElement]s. Any other element is refused.
 * @param content the composables whose components the container receives.
 */
@Composable
@SwingComposable
public inline fun VirtualSwingNode(
    modifier: SwingModifier = SwingModifier,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit,
) {
    val declarations = groupDeclarationsOf(currentComposer.materialize(modifier))
    ComposeNode<VirtualNodeHolder, SwingApplier>(
        factory = ::VirtualNodeHolder,
        update = {
            set(declarations) {
                storeGroupDeclarations(it)
            }
        },
        content = content,
    )
    GroupDeclarationEffect()
}

/**
 * Gives the components of each group whose update stored a different declaration in this apply that declaration,
 * after the apply's node lifecycle callbacks have let deactivated and released children leave their hosts. Only a
 * group's update records such a change, and that update runs only in a pass that runs this effect too: it is not
 * restartable, so the pass never skips it.
 */
@PublishedApi
@Composable
@NonRestartableComposable
internal fun GroupDeclarationEffect() {
    val applier = currentComposer.applier as SwingApplier
    SideEffect { applier.declareChangedGroups() }
}

/**
 * The declarations of a [VirtualSwingNode]'s [modifier], retained after key resolution. Refuses an element that is
 * not a parent-layout declaration, or that a layout node backs: a group has no component whose modifier chain could
 * carry the node.
 */
@PublishedApi
internal fun groupDeclarationsOf(modifier: SwingModifier): List<ParentElement> {
    val declarations =
        modifier.foldIn(ArrayList<ParentElement>()) { declarations, element ->
            element.requireSingleElementRole()
            require(element is ParentLayoutElement && element !is ParentLayoutNodeElement<*>) {
                val named =
                    if (element is SwingModifier.InspectableElement) {
                        "SwingModifier.${element.name}()"
                    } else {
                        element.javaClass.name
                    }
                "A VirtualSwingNode has no component, so its modifier declares only to the parent: $named is not a " +
                    "parent-layout declaration."
            }
            element.requireInheritableIsNotAdditive()
            declarations += element
            declarations
        }
    return retainedParentDeclarations(declarations)
}
