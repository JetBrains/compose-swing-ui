// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals

/** A value tree: each topic yields its [children], and its [name] is what the row renders. */
private data class Topic(
    val name: String,
    val children: List<Topic> = emptyList(),
)

/**
 * What becomes of an edit the user has open when a later structure is walked into the nodes under it. An
 * editor names a node, and the walk hands nodes over to the values that stand for them, so an edit whose
 * node the latest declarations no longer describe is ended rather than left to commit against a value the
 * user never edited. An edit over a node the walk leaves alone stays open.
 */
class TreeEditInFlightTest {
    @Test
    fun aNodeRewrittenUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        val a = Topic("A")
        var root by mutableStateOf(Topic("root", listOf(a, Topic("B"))))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        // The node under the editor takes over another value at the same position, keeping its identity.
        root = Topic("root", listOf(a, Topic("C")))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(false, "the edit should end with the node it was opened on") { isEditing }
        assertEquals(emptyList(), edits, "and commit nothing, rather than against the value that took over")
    }

    @Test
    fun aNodeThatGoesAwayUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        val a = Topic("A")
        var root by mutableStateOf(Topic("root", listOf(a, Topic("B"))))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        root = Topic("root", listOf(a))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            false,
            "the edit should end with the node that left the structure",
        ) { isEditing }
        assertEquals(emptyList(), edits, "and commit nothing, since the node it named is gone")
    }

    @Test
    fun aChangeElsewhereLeavesAnEditStanding() = runComposeSwingTest {
        val b = Topic("B")
        var root by mutableStateOf(Topic("root", listOf(Topic("A"), b)))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        // A sibling is rewritten; the node under the editor is handed over unchanged.
        root = Topic("root", listOf(Topic("X"), b))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(true, "an edit on a node the walk left alone should stand") { isEditing }

        tree.stopEditing()
        assertEquals(
            listOf<Triple<String, List<Int>, Any?>>(Triple("B", listOf(1), "B")),
            edits,
            "and commit against the node it was opened on",
        )
    }

    @Test
    fun aNodeRebuiltEqualUnderAnEditorKeepsTheEdit() = runComposeSwingTest {
        // The walk hands every node the object the new data stands it on, so a caller that rebuilds its
        // data gives the node under the editor another instance of the value it already carried. What
        // decides is whether that value changed, which is what the walk asks of a row it repaints.
        var root by mutableStateOf(Topic("root", listOf(Topic("A"), Topic("B"))))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        root = Topic("root", listOf(Topic("X"), Topic("B")))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(true, "an edit on a node rebuilt equal should stand") { isEditing }

        tree.stopEditing()
        assertEquals(
            listOf<Triple<String, List<Int>, Any?>>(Triple("B", listOf(1), "B")),
            edits,
            "and commit against the node it was opened on",
        )
    }

    @Test
    fun aNodeMovedAlongByAnInsertKeepsTheEdit() = runComposeSwingTest {
        // The walk keeps the nodes a list hands over unchanged at its back, so an insert in front of the
        // edited node moves it along rather than rewriting it - what stands under the editor is decided by
        // what the walk did to that node, not by what the new list declares at its old position.
        val a = Topic("A")
        val b = Topic("B")
        var root by mutableStateOf(Topic("root", listOf(a, b)))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        root = Topic("root", listOf(Topic("X"), a, b))
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(
            true,
            "an edit on a node an insert only moved along should stand",
        ) { isEditing }

        tree.stopEditing()
        assertEquals(
            listOf<Triple<String, List<Int>, Any?>>(Triple("B", listOf(2), "B")),
            edits,
            "and commit against that node at the index it moved to",
        )
    }

    @Test
    fun replacingTheModelWholeUnderAnEditorEndsTheEdit() = runComposeSwingTest {
        // A branch answer arriving asks for nodes of another shape, so the walk gives way to a model built
        // from scratch - and an editor names a node of the model it was opened on.
        var hasChildren by mutableStateOf<((Topic) -> Boolean)?>(null)
        val root = Topic("root", listOf(Topic("A"), Topic("B")))
        val edits = mutableListOf<Triple<String, List<Int>, Any?>>()
        setContent {
            Tree(
                root = root,
                children = { it.children },
                label = { it.name },
                hasChildren = hasChildren,
                isEditable = true,
                onNodeEdit = { value, path, newValue -> edits += Triple(value.name, path, newValue) },
            )
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.startEditingAtPath(tree.pathTo(1))
        onNodeOfType<JTree>().assertProperty(true, "the second node's row should open for editing") { isEditing }

        hasChildren = { true }
        awaitIdle()

        onNodeOfType<JTree>().assertProperty(false, "the edit should end with the model it was opened on") { isEditing }
        assertEquals(emptyList(), edits, "and commit nothing, since the nodes it named are gone")
    }
}
