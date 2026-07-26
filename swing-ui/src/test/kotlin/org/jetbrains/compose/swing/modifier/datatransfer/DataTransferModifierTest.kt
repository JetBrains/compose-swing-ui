package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.GraphicsEnvironment
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.dnd.DragSource
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import javax.swing.JComponent
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the data-transfer modifiers. They drive the real `TransferHandler` the
 * modifiers install on the live component — its `getSourceActions`/`createTransferable` for a drag
 * source, its `canImport`/`importData` for a drop target, and `exportToClipboard`/`importData` for
 * clipboard round-trips — asserting the observable outcomes (the produced `Transferable`, the fired
 * `onDrop`, the gated flavors, a value that survives copy-then-paste). No native peer is required.
 */
class DataTransferModifierTest {
    private fun support(
        component: JComponent,
        transferable: Transferable,
    ): TransferHandler.TransferSupport = TransferHandler.TransferSupport(component, transferable)

    // A local clipboard standing in for the system clipboard, a shared environment-dependent global
    // that is absent entirely without a display. Driving the installed handler's
    // exportToClipboard/importData against it asserts the exact copy/paste round-trip the public
    // helpers perform on the real clipboard.
    private fun localClipboard(): Clipboard = Clipboard("data-transfer-test")

    @Test
    fun transferActionMembershipAndCombination() {
        // The @TransferAction typed Int is a TransferHandler action bit-mask; membership is a bitwise
        // and, and combination is a bitwise or — the contract documented on the modifiers.
        assertTrue((TransferHandler.COPY_OR_MOVE and TransferHandler.COPY) != 0, "CopyOrMove must contain Copy")
        assertTrue((TransferHandler.COPY_OR_MOVE and TransferHandler.MOVE) != 0, "CopyOrMove must contain Move")
        assertEquals(0, TransferHandler.COPY_OR_MOVE and TransferHandler.LINK, "CopyOrMove must not contain Link")
        assertEquals(
            TransferHandler.COPY_OR_MOVE,
            TransferHandler.COPY or TransferHandler.MOVE,
            "COPY or MOVE must equal COPY_OR_MOVE",
        )
        // NONE is the empty mask: it shares no bit with any action.
        assertEquals(
            0,
            TransferHandler.COPY_OR_MOVE and TransferHandler.NONE,
            "the empty mask is no member of CopyOrMove",
        )
        assertEquals(
            TransferHandler.NONE,
            TransferHandler.COPY and TransferHandler.NONE,
            "the empty mask has no member",
        )
    }

    @Test
    fun draggableInstallsAHandlerThatExportsTheTransferable() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("src").draggable(TransferHandler.COPY) { StringSelection("payload") },
            )
        }
        val source = onNodeWithName("src").fetch<JComponent>()

        val clipboard = localClipboard()
        val exported =
            run {
                val handler = assertNotNull(source.transferHandler, "draggable must install a TransferHandler")
                assertEquals(
                    TransferHandler.COPY,
                    handler.getSourceActions(source),
                    "exported actions must be reported to Swing",
                )
                // Drive the public export path the drag/clipboard machinery uses to produce the
                // Transferable, then read the value the source produced back out.
                handler.exportToClipboard(source, clipboard, TransferHandler.COPY)
                clipboard.getData(DataFlavor.stringFlavor) as String
            }
        assertEquals("payload", exported, "the drag source must produce its Transferable for export")
    }

    // A TransferHandler that records its drag-export entry point so a test can prove the drag
    // gesture reached exportAsDrag, distinct from the clipboard exportToClipboard path. Its
    // createTransferable mirrors the source's, so the recorded drag carries the same payload a real
    // SharedTransferHandler would export.
    private class RecordingHandler(
        private val produce: () -> Transferable,
    ) : TransferHandler() {
        var draggedAction: Int? = null
        var draggedTransferable: Transferable? = null
        var clipboardAction: Int? = null

        override fun getSourceActions(c: JComponent?): Int = COPY

        override fun createTransferable(c: JComponent?): Transferable = produce()

        override fun exportAsDrag(
            comp: JComponent?,
            e: InputEvent?,
            action: Int,
        ) {
            draggedAction = action
            // Pull the payload exactly as the real DnD machinery would the moment a drag begins.
            draggedTransferable = createTransferable(comp)
        }

        override fun exportToClipboard(
            comp: JComponent?,
            clip: Clipboard?,
            action: Int,
        ) {
            clipboardAction = action
        }
    }

    @Test
    fun draggableGestureStartsADragPastTheThresholdDistinctFromClipboard() = runComposeSwingTest {
        val payload = StringSelection("dragged-payload")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.name("src").draggable(TransferHandler.COPY) { payload },
            )
        }
        val source = onNodeWithName("src").fetch<JComponent>()

        // Capture the gesture the modifier installed (one instance, both a MouseListener and a
        // MouseMotionListener), then swap in a recording handler the live gesture will call.
        val recording = RecordingHandler { payload }
        val gesture =
            run {
                val motion = source.mouseMotionListeners.lastOrNull()
                assertNotNull(motion, "draggable must install a drag-gesture MouseMotionListener")
                source.transferHandler = recording
                motion
            }
        val press = gesture as MouseListener
        val threshold = DragSource.getDragThreshold()

        // A press then a tiny move under the threshold must NOT start a drag.
        press.mousePressed(mouseEvent(source, MouseEvent.MOUSE_PRESSED, 10, 10))
        gesture.mouseDragged(mouseEvent(source, MouseEvent.MOUSE_DRAGGED, 11, 11))
        assertNull(recording.draggedAction, "a sub-threshold move must not start a drag")

        // A press then a move past the threshold must export the drag through exportAsDrag,
        // carrying the source's Transferable, and must not touch the clipboard path.
        press.mousePressed(mouseEvent(source, MouseEvent.MOUSE_PRESSED, 10, 10))
        gesture.mouseDragged(mouseEvent(source, MouseEvent.MOUSE_DRAGGED, 10 + threshold + 5, 10))
        assertEquals(
            TransferHandler.COPY,
            recording.draggedAction,
            "a past-threshold drag must call exportAsDrag with the source's offered action",
        )
        assertNull(recording.clipboardAction, "the drag gesture must not route through the clipboard export path")
        assertSame(
            payload,
            recording.draggedTransferable,
            "the drag export must carry the source's Transferable",
        )
    }

    private fun mouseEvent(
        component: JComponent,
        id: Int,
        x: Int,
        y: Int,
    ): MouseEvent = MouseEvent(component, id, System.currentTimeMillis(), 0, x, y, 1, false)

    @Test
    fun dropTargetImportFiresOnDropWithTheTransferable() = runComposeSwingTest {
        var dropped: String? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("dst").dropTarget(
                        acceptedActions = TransferHandler.COPY,
                        onDrop = { t ->
                            dropped = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                    ),
            )
        }
        val target = onNodeWithName("dst").fetch<JComponent>()

        val accepted =
            run {
                val handler = assertNotNull(target.transferHandler, "the drop target should install a transfer handler")
                handler.importData(support(target, StringSelection("dragged")))
            }
        assertTrue(accepted, "importData must report success when onDrop returns true")
        assertEquals("dragged", dropped, "onDrop must receive the dropped Transferable")
    }

    @Test
    fun canImportGatesFlavorsBeforeOnDrop() = runComposeSwingTest {
        var dropCalls = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("dst").dropTarget(
                        acceptedActions = TransferHandler.COPY,
                        onDrop = {
                            dropCalls++
                            true
                        },
                        canImport = { flavors -> DataFlavor.javaFileListFlavor in flavors },
                    ),
            )
        }
        val target = onNodeWithName("dst").fetch<JComponent>()

        val stringSupport = support(target, StringSelection("text"))
        val canImportString = target.transferHandler.canImport(stringSupport)
        assertFalse(canImportString, "canImport must reject a flavor the predicate does not accept")

        val imported = target.transferHandler.importData(stringSupport)
        assertFalse(imported, "importData must be refused when canImport rejects the flavors")
        assertEquals(0, dropCalls, "onDrop must not fire for a rejected flavor")
    }

    @Test
    fun clipboardCopyThenPasteRoundTripsAValue() = runComposeSwingTest {
        var pasted: String? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("clip").clipboard(
                        transferable = { StringSelection("roundtrip") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeWithName("clip").fetch<JComponent>()
        val clipboard = localClipboard()

        val accepted =
            run {
                val handler = assertNotNull(component.transferHandler, "clipboard must install a TransferHandler")
                handler.exportToClipboard(component, clipboard, TransferHandler.COPY)
                handler.importData(support(component, clipboard.getContents(null)))
            }
        assertTrue(accepted, "paste must succeed after a copy placed a value on the clipboard")
        assertEquals("roundtrip", pasted, "the value must survive copy then paste")
    }

    @Test
    fun clipboardCutExportsAsMoveAndPastes() = runComposeSwingTest {
        var pasted: String? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("clip").clipboard(
                        transferable = { StringSelection("cut-me") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeWithName("clip").fetch<JComponent>()
        val clipboard = localClipboard()

        val accepted =
            run {
                val handler = component.transferHandler
                // Cut exports with the MOVE action; the value still lands on the clipboard for paste.
                handler.exportToClipboard(component, clipboard, TransferHandler.MOVE)
                handler.importData(support(component, clipboard.getContents(null)))
            }
        assertTrue(accepted, "importData must report success after a cut round-trip")
        assertEquals("cut-me", pasted, "cut must place the value on the clipboard for a later paste")
    }

    @Test
    fun clipboardHandleRoundTripsThroughTheBoundComponent() = runComposeSwingTest {
        var pasted: String? = null
        lateinit var handle: ClipboardHandle
        setContent {
            handle = rememberClipboardHandle()
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("clip").clipboard(
                        transferable = { StringSelection("handle-roundtrip") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                        handle = handle,
                    ),
            )
        }

        // The handle drives copy/cut/paste on the bound component through the real system clipboard,
        // so it must run cleanly whether or not one is reachable: where one is absent (no display)
        // paste reports failure and onPaste never fires; where one is present the value the bound
        // component exported on copy round-trips back through onPaste on paste.
        handle.copy()
        handle.cut()
        val imported = handle.paste()
        if (GraphicsEnvironment.isHeadless()) {
            assertFalse(imported, "paste reports failure when no system clipboard is reachable")
            assertNull(pasted, "onPaste must not fire when there is nothing to import")
        } else {
            assertTrue(imported, "paste imports the round-tripped value when a system clipboard is present")
            assertEquals(
                "handle-roundtrip",
                pasted,
                "the value the bound component exported must survive copy then paste",
            )
        }
    }

    @Test
    fun clipboardHandleIsInertWhileUnbound() = runComposeSwingTest {
        lateinit var handle: ClipboardHandle
        setContent {
            handle = rememberClipboardHandle()
            // The handle is created but never passed to a clipboard modifier, so it binds no component.
            TextField(value = "", onValueChange = {})
        }

        // An unbound handle has no component to act on: copy and cut are no-ops that must not throw,
        // and paste reports failure without touching any component.
        handle.copy()
        handle.cut()
        assertFalse(handle.paste(), "an unbound handle's paste must report failure")
    }

    @Test
    fun clipboardBindsTheStandardKeystrokesWhenRequested() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("clip").clipboard(
                        transferable = { StringSelection("x") },
                        onPaste = { true },
                    ),
            )
        }
        val component = onNodeWithName("clip").fetch<JComponent>()

        // Read the bindings through the real InputMap/ActionMap without reconstructing the
        // platform shortcut mask (unavailable headless): every copy/cut/paste keystroke the
        // modifier installed must resolve to one of the standard TransferHandler actions.
        val inputMap = component.getInputMap(JComponent.WHEN_FOCUSED)
        val actionMap = component.actionMap
        val boundActions =
            inputMap
                .allKeys()
                .orEmpty()
                .mapNotNull { stroke -> actionMap.get(inputMap.get(stroke)) }
                .toSet()
        assertTrue(TransferHandler.getCopyAction() in boundActions, "copy must be bound to a keystroke")
        assertTrue(TransferHandler.getCutAction() in boundActions, "cut must be bound to a keystroke")
        assertTrue(TransferHandler.getPasteAction() in boundActions, "paste must be bound to a keystroke")
    }

    @Test
    fun draggableAndDropTargetShareOneHandlerOnTheSameComponent() = runComposeSwingTest {
        var dropped: String? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .name("both")
                        .draggable(TransferHandler.MOVE) { StringSelection("from-source") }
                        .dropTarget(
                            acceptedActions = TransferHandler.COPY,
                            onDrop = { t ->
                                dropped = t.getTransferData(DataFlavor.stringFlavor) as String
                                true
                            },
                        ),
            )
        }
        val component = onNodeWithName("both").fetch<JComponent>()

        val handler =
            assertNotNull(component.transferHandler, "the shared modifier should install one transfer handler")
        // One handler exposes BOTH the drag source's export and the drop target's import.
        assertEquals(
            TransferHandler.MOVE,
            handler.getSourceActions(component),
            "the handler should expose the drag source's MOVE action",
        )
        assertTrue(
            handler.importData(support(component, StringSelection("dropped-in"))),
            "the same handler should accept the drop",
        )
        assertEquals("dropped-in", dropped, "the drop should deliver the imported value")
    }

    @Test
    fun removingTheDropTargetModifierRestoresTheOriginalHandler() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.name("dst").let {
                        if (enabled) it.dropTarget(TransferHandler.COPY, onDrop = { true }) else it
                    },
            )
        }
        val component = onNodeWithName("dst").fetch<JComponent>()
        // A TextField ships with its own TransferHandler; while installed the modifier's handler
        // is in place and accepts the drop.
        val installed = component.transferHandler
        assertEquals(
            "SharedTransferHandler",
            installed?.javaClass?.simpleName,
            "the modifier must install its own handler while present",
        )
        assertTrue(
            component.transferHandler.importData(support(component, StringSelection("x"))),
            "while installed the modifier's handler accepts the drop",
        )

        enabled = false
        awaitIdle()

        // The TextField's own (non-modifier) handler is restored once the modifier leaves the chain.
        val restored = component.transferHandler
        assertNotNull(restored, "removing the modifier must restore the component's original handler")
        assertTrue(
            restored.javaClass.simpleName != "SharedTransferHandler",
            "the modifier's handler must be gone, leaving the component's original in place",
        )
    }

    @Test
    fun draggableSeesTheLatestExporterAcrossRecomposition() = runComposeSwingTest {
        var payload by mutableStateOf("first")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.name("src").draggable(TransferHandler.COPY) { StringSelection(payload) },
            )
        }
        val source = onNodeWithName("src").fetch<JComponent>()
        val clipboard = localClipboard()

        fun exportNow(): String {
            source.transferHandler.exportToClipboard(source, clipboard, TransferHandler.COPY)
            return clipboard.getData(DataFlavor.stringFlavor) as String
        }
        assertEquals("first", exportNow(), "the exporter should start with the first payload")

        payload = "second"
        awaitIdle()
        assertEquals("second", exportNow(), "the drag source must export the latest value after recomposition")
    }
}
