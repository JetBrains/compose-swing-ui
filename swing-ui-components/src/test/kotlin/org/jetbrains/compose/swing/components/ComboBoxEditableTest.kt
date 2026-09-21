// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.singleWidget
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for an editable [ComboBox] and for the size of its popup. An editable combo box
 * accepts a value the list does not contain: the selection callback reports that no item is selected any
 * more, and the commit callback carries the value itself.
 */
class ComboBoxEditableTest {
    @Test
    fun editableTracksTheDeclaredValue() = runComposeSwingTest {
        // Declared `true` first: `false` is the combo box's own default, so a wrapper that never
        // applied the value at all would still satisfy an opening `isEditable(false)`.
        var editable by mutableStateOf(true)
        setContent {
            ComboBox(items = listOf("red", "green"), selectedItem = null, onSelectionChange = {}, editable = editable)
        }

        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable(false))
    }

    @Test
    fun aTypedValueOutsideTheModelIsReportedAsACommit() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("red", "green"))
        val reported = mutableListOf<Int>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                model = model,
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(emptyList(), reported, "rendering the declared selection must not report a change")

        // Committing the editor is what the user's Enter key does: the typed text becomes the combo
        // box's selected item even though no item equals it.
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(-1, combo.selectedIndex, "a value outside the model leaves the combo box with no selection")
        assertEquals(listOf(-1), reported, "no item is selected any more, reported once")
        assertEquals(listOf("purple"), committed, "the typed value should reach the commit callback")
        assertEquals("purple", model.selectedItem, "the typed value should land on the caller's model")
    }

    @Test
    fun aTypedValueOutsideTheItemsIsReportedAsACommit() = runComposeSwingTest {
        val reported = mutableListOf<String?>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedItem = "red",
                onSelectionChange = { reported += it },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val editor = onNodeOfType<JComboBox<*>>().fetch().editor.editorComponent as JTextField
        editor.text = "purple"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(listOf("purple"), committed, "the typed value should reach the commit callback")
        assertEquals(listOf<String?>(null), reported, "no item is selected any more, reported once")
    }

    @Test
    fun aTypedValueOutsideTheItemsReachesTheCommitCallbackBeforeTheSelectionIsPutBack() = runSwingTest {
        // On the recomposer the library drives, a reported change settles inside the event that made it.
        // This pins the commit against a pass that can run between the two events a commit is published as.
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        val reported = mutableListOf<String?>()
        val committed = mutableListOf<String>()
        var mounted: DisposableHandle? = null
        try {
            mounted =
                composition.setContent(parent = recomposer.compositionContext) {
                    ComboBox(
                        items = listOf("red", "green"),
                        selectedItem = "red",
                        onSelectionChange = { reported += it },
                        editable = true,
                        onValueCommit = { committed += it },
                    )
                }
            val combo = singleWidget(composition, JComboBox::class.java)

            val editor = combo.editor.editorComponent as JTextField
            editor.text = "purple"
            editor.postActionEvent()

            assertEquals(listOf("purple"), committed, "the typed value should reach the commit callback")
            assertEquals(listOf<String?>(null), reported, "no item is selected any more, reported once")

            awaitUntil("the selection the caller did not adopt is put back") { combo.selectedItem == "red" }

            assertEquals(
                listOf<String?>(null),
                reported,
                "and putting it back is the wrapper's own write, reported to nobody",
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun typedTextThatNamesAnItemReachesTheCommitCallbackBeforeTheSelectionIsPutBack() = runSwingTest {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        val reported = mutableListOf<String?>()
        val committed = mutableListOf<String>()
        var mounted: DisposableHandle? = null
        try {
            mounted =
                composition.setContent(parent = recomposer.compositionContext) {
                    ComboBox(
                        items = listOf("red", "green"),
                        selectedItem = "red",
                        onSelectionChange = { reported += it },
                        editable = true,
                        onValueCommit = { committed += it },
                    )
                }
            val combo = singleWidget(composition, JComboBox::class.java)

            val editor = combo.editor.editorComponent as JTextField
            editor.text = "green"
            editor.postActionEvent()

            assertEquals(listOf("green"), committed, "the typed text should reach the commit callback")
            assertEquals(listOf<String?>("green"), reported, "the item the text names is reported once")

            awaitUntil("the selection the caller did not adopt is put back") { combo.selectedItem == "red" }

            assertEquals(
                listOf<String?>("green"),
                reported,
                "and putting it back is the wrapper's own write, reported to nobody",
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun committingAnItemReportsBothTheItemAndItsValue() = runComposeSwingTest {
        // The item a commit reports is adopted into state and fed back as the declaration, the way a
        // real caller wires a controlled ComboBox: a declaration that stayed behind would be reasserted
        // over the very commit this test is checking for.
        var selectedItem by mutableStateOf<String?>(null)
        val reported = mutableListOf<String?>()
        val committed = mutableListOf<String>()
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedItem = selectedItem,
                onSelectionChange = {
                    reported += it
                    selectedItem = it
                },
                editable = true,
                onValueCommit = { committed += it },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        val editor = combo.editor.editorComponent as JTextField
        editor.text = "green"
        editor.postActionEvent()
        awaitIdle()

        assertEquals(1, combo.selectedIndex, "a typed value that matches an item selects it")
        assertEquals(listOf<String?>("green"), reported, "the selected item should be reported")
        assertEquals(listOf("green"), committed, "the committed text should be reported")
    }

    @Test
    fun maximumRowCountTracksTheDeclaredValue() = runComposeSwingTest {
        var rows by mutableStateOf(3)
        setContent {
            ComboBox(
                items = listOf("red", "green", "blue"),
                selectedItem = null,
                onSelectionChange = {},
                maximumRowCount = rows,
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(3, combo.maximumRowCount, "the declared maximum row count should reach the combo box")

        rows = 5
        awaitIdle()
        assertEquals(5, combo.maximumRowCount, "changing the maximum row count should reach the combo box")
    }

    @Test
    fun aWithdrawnRowCountGoesBackToTheCountTheComboBoxCarried() = runComposeSwingTest {
        var rows by mutableStateOf<Int?>(null)
        setContent {
            ComboBox(items = listOf("red"), selectedItem = null, onSelectionChange = {}, maximumRowCount = rows)
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()

        // A count written onto this box, the way a look and feel installs one onto the boxes it builds.
        // The withdrawal owes the count this box carried, which a freshly built box need not carry.
        val carried = combo.maximumRowCount + 1
        combo.maximumRowCount = carried

        rows = POPUP_ROWS
        awaitIdle()
        assertEquals(POPUP_ROWS, combo.maximumRowCount, "the declared maximum row count should reach the combo box")

        rows = null
        awaitIdle()
        assertEquals(
            carried,
            combo.maximumRowCount,
            "withdrawing the count should hand back the one the combo box was carrying",
        )
    }
}

/** A declared row count, chosen apart from any count a combo box carries of its own. */
private const val POPUP_ROWS: Int = 15
