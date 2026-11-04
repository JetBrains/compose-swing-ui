package org.jetbrains.compose.swing.node

import org.jetbrains.compose.swing.core.collectFailure
import org.jetbrains.compose.swing.util.fastForEach
import org.jetbrains.compose.swing.util.fastForEachIndexed
import java.awt.Container

/**
 * The node of a [VirtualSwingNode]: it has no component, and its children are added to the container of its
 * [componentHost].
 */
@PublishedApi
internal class VirtualNodeHolder
    @PublishedApi
    internal constructor() : NodeHolder {
        override val children: MutableList<NodeHolder> = ArrayList()

        override var attachmentWait: AttachmentWait = AttachmentWait.Placement
            set(value) {
                field = value
                if (value == AttachmentWait.None) return
                children.fastForEach {
                    // A relocated child keeps its wait: the pass that relocated it attaches it once it settles.
                    if (it.attachmentWait != AttachmentWait.Relocation) it.attachmentWait = AttachmentWait.Placement
                }
            }

        override var deactivated: Boolean = false
            private set

        override var owner: SwingCompositionOwner? = null
            private set

        override var parent: NodeHolder? = null

        override val placesChildren: Boolean
            get() = !awaitingAttachment && !deactivated

        override val componentHost: SwingNodeHolder<*>
            get() = insertedParent.componentHost

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
                        moveChildrenTo(null, forGood)
                    }
                    if (owner != null) {
                        this.owner = owner
                        moveChildrenTo(owner, forGood)
                    }
                }
            if (failure != null) {
                clearAwaitingAttachment()
                throw failure
            }
        }

        /**
         * Refuses the group under a host that places its children in regions, then checks each child it will attach.
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
            attachmentWait = AttachmentWait.None
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

        override fun flattenInto(destination: MutableList<SwingNodeHolder<*>>) {
            children.fastForEach { it.flattenInto(destination) }
        }

        /** An ExistingSwingNode must be composed directly under the component it configures. */
        override fun checkClaimable() {
            error(
                "An ExistingSwingNode is composed inside a VirtualSwingNode, but a claimed component is where its " +
                    "parent put it, so no group can place it. Compose the ExistingSwingNode directly under the " +
                    "component it configures.",
            )
        }

        /** Deactivates the group, so it is never attached again. The runtime deactivates each child itself. */
        override fun onDeactivate() {
            deactivated = true
        }

        override fun onRelease() {
            owner = null
        }
    }

private fun NodeHolder.clearAwaitingAttachment() {
    attachmentWait = AttachmentWait.None
    children.fastForEach { it.clearAwaitingAttachment() }
}
