package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentElement
import org.jetbrains.compose.swing.util.fastFirstOrNull
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Container

/**
 * The node of a [VirtualSwingNode]: it has no component, and its children are added to the container of its
 * [componentHost]. It declares its modifier's parent-layout declarations to that container for each component it
 * places there.
 */
@PublishedApi
internal class VirtualNodeHolder
    @PublishedApi
    internal constructor() : NodeHolder {
        /** The parent-layout declarations of this group's modifier, retained after key resolution. */
        private var groupDeclarations: List<ParentElement> = emptyList()

        override val children: MutableList<NodeHolder> = ArrayList()

        override var awaitingAttachment: Boolean = true
            set(value) {
                field = value
                if (value) children.fastForEach { it.awaitingAttachment = true }
            }

        override var deactivated: Boolean = false
            private set

        override var owner: SwingCompositionOwner? = null
            private set

        override var parent: NodeHolder? = null
            set(value) {
                field = value
                cachedGroupDeclarations = null
            }

        private var cachedGroupDeclarations: List<ParentElement>? = null
        private var cachedOuterDeclarations: List<ParentElement>? = null

        override val placesChildren: Boolean
            get() = !awaitingAttachment && !deactivated

        override val componentHost: SwingNodeHolder<*>
            get() = insertedParent.componentHost

        override val childGroupDeclarations: List<ParentElement>
            get() {
                val outer = insertedParent.childGroupDeclarations
                if (cachedGroupDeclarations == null || cachedOuterDeclarations !== outer) {
                    cachedOuterDeclarations = outer
                    cachedGroupDeclarations = if (groupDeclarations.isEmpty()) outer else outer + groupDeclarations
                }
                return checkNotNull(cachedGroupDeclarations)
            }

        override val declaredName: String get() = "VirtualSwingNode"

        /** The node this group was inserted under, which every walk to its host goes through. */
        private val insertedParent: NodeHolder
            get() = checkNotNull(parent) { "A VirtualSwingNode is resolved through its parent before it was inserted." }

        /** The subtree leaves its previous composition before any child attaches to the new composition. */
        override fun moveTo(
            owner: SwingCompositionOwner?,
            forGood: Boolean,
        ) {
            val previous = this.owner
            if (owner === previous) return
            val failure =
                collectFailure(null) {
                    if (previous != null) {
                        this.owner = null
                        var failure: Throwable? = null
                        val movedChildren = children.toList()
                        movedChildren.fastForEach { child ->
                            failure = collectFailure(failure) { child.moveTo(null, forGood) }
                        }
                        failure?.let { throw it }
                    }
                    if (owner != null) {
                        this.owner = owner
                        children.fastForEach { it.moveTo(owner, forGood = false) }
                    }
                }
            if (failure != null) {
                clearAwaitingAttachment()
                throw failure
            }
        }

        /**
         * Refuses the group under a host that places its children in regions, and where it declares parent data the
         * host cannot receive again, then checks each child it will attach.
         */
        override fun checkPlacementUnder(
            host: SwingNodeHolder<*>,
            container: Container,
            regions: ChildRegions,
        ) {
            check(!host.childPlacement.holdsRegions) {
                "A VirtualSwingNode is placed among indexed children; a ${container.declaredName} places each child " +
                    "in a region it names. Compose the group inside a container that adds its children by index."
            }
            checkGroupParentDataUnder(container, groupDeclarations)
            children.fastForEach { if (regions.attachesWithGroup(it)) it.checkPlacementUnder(host, container, regions) }
        }

        /**
         * Attaches each child in composition order, skipping children [ChildRegions.attachesWithGroup] excludes.
         *
         * The caller checked every child before the first is added, so a refusal leaves the host unchanged.
         */
        override fun attachTo(
            parent: NodeHolder,
            container: Container,
            index: Int,
            regions: ChildRegions,
        ) {
            awaitingAttachment = false
            children.fastForEachIndexed { childIndex, child ->
                if (!regions.attachesWithGroup(child)) return@fastForEachIndexed
                child.attachTo(this, container, childIndex, regions)
            }
        }

        override fun insertIn(projection: ChildProjection) {
            projection.insertGroup()
        }

        override fun removeFrom(projection: ChildProjection) {
            projection.removeGroup()
        }

        override fun unfoldInto(destination: MutableList<SwingNodeHolder<*>>) {
            children.fastForEach { it.unfoldInto(destination) }
        }

        /** An ExistingSwingNode must be composed directly under the component it configures. */
        override fun checkClaimable() {
            error(
                "An ExistingSwingNode is composed inside a VirtualSwingNode, but a claimed component is where its " +
                    "parent put it, so no group can place it. Compose the ExistingSwingNode directly under the " +
                    "component it configures.",
            )
        }

        /**
         * Stores [declarations] as this group's. Where the group is placed, refuses parent data its host cannot
         * receive again. Each child receives an accepted update before its host settles against its children.
         * A refused update is checked again after the apply's node lifecycle callbacks, and the host settles after
         * it publishes.
         *
         * @param declarations the group's retained parent declarations after key resolution.
         */
        @PublishedApi
        internal fun storeGroupDeclarations(declarations: List<ParentElement>) {
            if (placesChildren) {
                val host = componentHost.component as? Container
                if (host != null) checkGroupParentDataUnder(host, declarations)
            }
            if (groupDeclarations == declarations) return
            groupDeclarations = declarations
            cachedGroupDeclarations = null
            if (placesChildren) {
                owner?.updateBatch?.let {
                    it.holdForGroupRedeclare(this)
                    it.holdForChildSettle(componentHost)
                }
            }
        }

        /** Deactivates the group, so it is never attached again. The runtime deactivates each child itself. */
        override fun onDeactivate() {
            deactivated = true
        }

        override fun onRelease() {
            owner = null
        }

        override fun onReuse() = Unit
    }

/**
 * Refuses group [declarations] of parent data under [container] where its child host has no layout manager: such a
 * host reads parent data only as it adds a child, so a change of the group's could never reach it.
 *
 * @param container the container receiving the group's components.
 * @param declarations the group's retained parent declarations.
 */
internal fun checkGroupParentDataUnder(
    container: Container,
    declarations: List<ParentElement>,
) {
    val childHost = container.childHost
    check(childHost.layout != null || declarations.fastFirstOrNull { it is ParentDataModifier } == null) {
        val host = childHost.declaredName
        "A VirtualSwingNode under a $host cannot declare parent data, because a $host has no layout manager to " +
            "declare it to again. Declare it on each child."
    }
}

/**
 * Publishes changed merged declarations to this group's host. Every changed declaration is checked before the
 * first is published. Only components still attached by composition are declared; attachment publishes the rest.
 * Returns a refused candidate's failure without changing any child's accepted declaration.
 *
 * @param components reusable storage for this group's component projection.
 * @param declarations reusable storage for checked declarations, in component order.
 */
internal fun VirtualNodeHolder.redeclareChildren(
    components: MutableList<SwingNodeHolder<*>>,
    declarations: MutableList<MergedDeclaration>,
): RuntimeException? {
    val host = if (placesChildren) (componentHost.component as? Container)?.childHost else null
    if (host == null) return null
    components.clear()
    declarations.clear()
    unfoldInto(components)
    var attachedCount = 0
    val refusal =
        try {
            components.fastForEach { child ->
                if (child.standsByCompositionIn(host)) {
                    declarations.add(child.declaration.checkGroupDeclarations())
                    components[attachedCount++] = child
                }
            }
            null
        } catch (failure: IllegalArgumentException) {
            failure
        } catch (failure: IllegalStateException) {
            failure
        }
    return if (refusal != null) {
        refusal
    } else {
        var changedCount = 0
        for (index in 0 until attachedCount) {
            val child = components[index]
            if (child.declaration.storeGroupDeclarations(declarations[index])) components[changedCount++] = child
        }
        for (index in 0 until changedCount) components[index].declaration.publish()
        components.clear()
        declarations.clear()
        null
    }
}

private fun NodeHolder.clearAwaitingAttachment() {
    awaitingAttachment = false
    children.fastForEach { it.clearAwaitingAttachment() }
}
