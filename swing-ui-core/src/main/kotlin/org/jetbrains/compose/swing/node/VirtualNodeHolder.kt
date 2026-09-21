// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        /**
         * The parent-layout declarations of this group's modifier, retained after key resolution, or `null` until the
         * first update stores them.
         */
        private var storedDeclarations: List<ParentElement>? = null

        private val groupDeclarations: List<ParentElement> get() = storedDeclarations.orEmpty()

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
            attachmentWait = AttachmentWait.None
            children.fastForEachIndexed { childIndex, child ->
                if (!regions.attachesWithGroup(child)) return@fastForEachIndexed
                child.attachTo(this, container, childIndex, regions)
            }
        }

        override fun takeGroupDeclarations(
            parent: NodeHolder,
            index: Int,
            container: Container,
            regions: ChildRegions,
        ) {
            if (deactivated || joinHostIfWaiting(parent, index, container, regions)) return
            children.fastForEachIndexed { childIndex, child ->
                child.takeGroupDeclarations(this, childIndex, container, regions)
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

        /**
         * Stores [declarations] as this group's. Where the group is placed, refuses parent data its host cannot
         * receive again. Records a change for [GroupDeclarationEffect], where every component the group places takes
         * it, for a group that was placed before, one relocated and awaiting its reattachment included.
         *
         * @param declarations the group's retained parent declarations after key resolution.
         */
        @PublishedApi
        internal fun storeGroupDeclarations(declarations: List<ParentElement>) {
            if (placesChildren) {
                val host = componentHost.component as? Container
                if (host != null) checkGroupParentDataUnder(host, declarations)
            }
            if (storedDeclarations == declarations) return
            val placedBefore = storedDeclarations != null && !deactivated
            storedDeclarations = declarations
            cachedGroupDeclarations = null
            if (placedBefore) owner?.updateBatch?.changedGroups?.record(this)
        }

        /** Deactivates the group, so it is never attached again. The runtime deactivates each child itself. */
        override fun onDeactivate() {
            deactivated = true
        }

        override fun onRelease() {
            owner = null
        }
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

private fun NodeHolder.clearAwaitingAttachment() {
    attachmentWait = AttachmentWait.None
    children.fastForEach { it.clearAwaitingAttachment() }
}
