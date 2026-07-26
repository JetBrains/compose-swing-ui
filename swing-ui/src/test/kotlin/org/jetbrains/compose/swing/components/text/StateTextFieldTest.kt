package org.jetbrains.compose.swing.components.text

import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTextField
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Tests the state-based [TextField] composable overload: the field shares the state's document,
 * typing recomposes a sibling reading `state.text`, and an unmount tears the binding down cleanly.
 */
class StateTextFieldTest {
    @Test
    fun fieldSharesTheStateDocument() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("seed")
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertSame(state.document, field.document, "the field must render the state's own document")
    }

    @Test
    fun callerSuppliedDocumentIsInstalledIntoTheField() = runComposeSwingTest {
        val document = PlainDocument().apply { insertString(0, "preset", null) }
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState(document = document)
            TextField(state = state)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertSame(document, field.document)
        assertEquals("preset", field.text)
    }

    @Test
    fun typingRecomposesASiblingReadingStateText() = runComposeSwingTest {
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hi")
            BoxPanel {
                TextField(state = state)
                Label(text = "Echo: ${state.text}")
            }
        }

        onNodeWithText("Echo: hi").assertExists()

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.text = "bye"
        awaitIdle()

        onNodeWithText("Echo: bye").assertExists()
        onNodeWithText("Echo: hi").assertDoesNotExist()
    }

    @Test
    fun externalDocumentMutationRecomposesASiblingReadingStateText() = runComposeSwingTest {
        // A caller may mutate the shared document directly rather than through the state. Reading
        // state.text registers a snapshot subscription to the document generation, so a direct document
        // edit must invalidate that reader even though it never went through the state's API.
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hi")
            BoxPanel {
                TextField(state = state)
                Label(text = "Echo: ${state.text}")
            }
        }

        onNodeWithText("Echo: hi").assertExists()

        state.document.insertString(state.document.length, " there", null)
        awaitIdle()

        onNodeWithText("Echo: hi there").assertExists()
        onNodeWithText("Echo: hi").assertDoesNotExist()
    }

    @Test
    fun movingCaretRecomposesASiblingReadingStateSelection() = runComposeSwingTest {
        // Reading state.selection inside a composable subscribes to caret changes, so moving the caret in
        // the bound field must recompose the sibling that renders the selection.
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("hello world")
            BoxPanel {
                TextField(state = state)
                Label(text = "Range: ${state.selection.start}-${state.selection.end}")
            }
        }

        onNodeWithText("Range: 11-11").assertExists()

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.select(0, 5)
        awaitIdle()

        onNodeWithText("Range: 0-5").assertExists()
        onNodeWithText("Range: 11-11").assertDoesNotExist()
    }

    @Test
    fun undoAndRedoRecomposeASiblingReadingAvailability() = runComposeSwingTest {
        // Reading state.canUndo / state.canRedo inside a composable subscribes to the document generation.
        // An edit, and then an undo and a redo, each change the document and so must recompose a sibling
        // that renders undo/redo availability.
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("base")
            BoxPanel {
                TextField(state = state)
                Label(text = "undo=${state.canUndo} redo=${state.canRedo}")
            }
        }

        onNodeWithText("undo=false redo=false").assertExists()

        state.edit { append("+more") }
        awaitIdle()
        onNodeWithText("undo=true redo=false").assertExists()

        state.undo()
        awaitIdle()
        onNodeWithText("undo=false redo=true").assertExists()

        state.redo()
        awaitIdle()
        onNodeWithText("undo=true redo=false").assertExists()
    }

    @Test
    fun stateAndValueBasedOverloadsCoexist() = runComposeSwingTest {
        var controlled by mutableStateOf("controlled")
        lateinit var state: DocumentState
        setContent {
            state = rememberDocumentState("stateful")
            BoxPanel {
                TextField(value = controlled, onValueChange = { controlled = it })
                TextField(state = state)
            }
        }

        onNodeWithText("controlled").assertExists()
        onNodeWithText("stateful").assertExists()
    }

    @Test
    fun forgettingTheStateDetachesItsListenersFromACallerDocument() = runComposeSwingTest {
        // A caller-supplied document outlives the state, so leaving the composition must detach the
        // state's listeners; otherwise the discarded state stays reachable from the live document. No
        // field is mounted here, so only the state registers listeners and the counts are unambiguous.
        val document = PlainDocument()
        var mounted by mutableStateOf(true)
        setContent {
            if (mounted) {
                val state = rememberDocumentState(document = document)
                Label(text = state.text.toString())
            }
        }
        awaitIdle()
        assertEquals(1, document.documentListeners.size, "the live state listens to its document")
        assertEquals(1, document.undoableEditListeners.size, "the live state records undo on its document")

        mounted = false
        awaitIdle()
        assertEquals(0, document.documentListeners.size, "forgetting the state removes its document listener")
        assertEquals(0, document.undoableEditListeners.size, "forgetting the state removes its undo listener")
    }

    @Test
    fun swappingTheStateParamDetachesTheFormerStatesCaretListener() = runComposeSwingTest {
        // A field is owned by at most one state. When the `state` argument swaps to a different state,
        // binding the new state installs its document into the field, which resets the caret; the former
        // state must already be unbound so that reset does not drive the former state's caret listener. So
        // the field must render the new state's document, a subsequent caret change must reach only the new
        // state, and the former state's selection must be left as it was.
        lateinit var stateA: DocumentState
        lateinit var stateB: DocumentState
        var useA by mutableStateOf(true)
        setContent {
            stateA = rememberDocumentState("first")
            stateB = rememberDocumentState("second")
            TextField(state = if (useA) stateA else stateB)
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertSame(stateA.document, field.document)

        // Give stateA a distinctive selection so a stray caret event driven by the swap would change it.
        field.select(1, 4)
        awaitIdle()
        assertEquals(TextRange(1, 4), stateA.selection)

        useA = false
        awaitIdle()

        assertSame(stateB.document, field.document, "after the swap the field renders the new state's document")
        assertEquals(
            TextRange(1, 4),
            stateA.selection,
            "installing the new state's document must not drive the former state's caret listener",
        )

        field.select(0, 2)
        awaitIdle()

        assertEquals(TextRange(0, 2), stateB.selection, "the caret drives the currently bound state")
        assertEquals(
            TextRange(1, 4),
            stateA.selection,
            "the former state's caret listener was detached from the field",
        )
    }

    @Test
    fun unmountingTheFieldStopsCaretWriteBack() = runComposeSwingTest {
        lateinit var state: DocumentState
        var mounted by mutableStateOf(true)
        setContent {
            state = rememberDocumentState("hello world")
            BoxPanel {
                if (mounted) TextField(state = state)
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.select(0, 5)
        awaitIdle()
        assertEquals(TextRange(0, 5), state.selection)

        mounted = false
        awaitIdle()

        // Editing the now-detached field must not write back into the unbound state.
        field.select(6, 11)
        awaitIdle()
        assertEquals(TextRange(0, 5), state.selection, "an unmounted field must not drive the state")
    }

    @Test
    fun aParkedFieldDoesNotDriveASurvivingStateUntilReactivated() = runComposeSwingTest {
        // The state is held at a longer-lived scope than the field: constructed outside the composition, it
        // is never forgotten when the field parks, standing in for a state hoisted above a collapsible or
        // reused region. Parking the field (ReusableContentHost deactivated) must detach the binding with the
        // node's modifiers so the parked field stops driving the surviving state; reactivating rebinds and
        // restores the state's selection.
        val document = PlainDocument().apply { insertString(0, "parked text", null) }
        val state = DocumentState(document)
        var active by mutableStateOf(true)
        setContent {
            ReusableContentHost(active = active) {
                TextField(state = state)
            }
        }

        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        field.select(0, 6)
        awaitIdle()
        assertEquals(TextRange(0, 6), state.selection)

        // Snapshot the document's listener census while the field is bound: the state's own document
        // listener is part of it, so an unchanged census across the park proves the state survived.
        val documentListenersWhileBound = document.documentListeners.size

        active = false
        awaitIdle()

        // The state genuinely survives the park (it was never remembered, so it is not forgotten): its own
        // document listener is still attached. This is what makes the red exercise the binding's detach
        // rather than a full state teardown.
        assertEquals(
            documentListenersWhileBound,
            document.documentListeners.size,
            "the directly-constructed state is not forgotten by the park",
        )

        // The parked field's caret must not reach the surviving state.
        field.select(1, 3)
        awaitIdle()
        assertEquals(TextRange(0, 6), state.selection, "a parked field's caret does not drive the state")

        active = true
        awaitIdle()

        val reattached = onNodeOfType<JTextField>().fetch<JTextField>()
        assertSame(state.document, reattached.document, "reactivating restores the binding")
        assertEquals(TextRange(0, 6), state.selection, "the state's selection survives parking")
    }
}
