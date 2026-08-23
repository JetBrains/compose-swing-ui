package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.mutableStateOf
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Pins that [TreeContent.syncInto] leaves a node narrowed to the leaf it is syncing it onto even where
 * removing its old children throws partway through - a node left wrongly widened would go on accepting
 * children it no longer has any of.
 */
class TreeContentSyncTest {
    @Test
    fun aChildRemovalThatThrowsStillLeavesTheNodeNarrowed() {
        val onEdit = mutableStateOf<(value: String, path: List<Int>, newValue: Any?) -> Unit>({ _, _, _ -> })
        val withChild =
            TreeContent(
                root = "root",
                children = { value -> if (value == "root") listOf("child") else emptyList() },
                label = { it },
                hasChildren = null,
            )
        val model = withChild.toModel(onEdit)
        model.addTreeModelListener(
            object : TreeModelListener {
                override fun treeNodesChanged(e: TreeModelEvent) = Unit

                override fun treeNodesInserted(e: TreeModelEvent) = Unit

                override fun treeNodesRemoved(e: TreeModelEvent) = error("listener failed")

                override fun treeStructureChanged(e: TreeModelEvent) = Unit
            },
        )

        val leaf =
            TreeContent(
                root = "root",
                children = { emptyList() },
                label = { it },
                hasChildren = { false },
            )

        assertFailsWith<IllegalStateException> { leaf.syncInto(model, walkEvery = true) }

        val root = model.root as DefaultMutableTreeNode
        assertFalse(
            root.allowsChildren,
            "a node whose children failed to sync away should still end up narrowed to the leaf it was " +
                "syncing onto",
        )
    }
}
