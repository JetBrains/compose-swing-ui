package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.dnd.DragSource
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.JTextField
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
 * modifiers install on the live component - its `getSourceActions`/`createTransferable` for a drag
 * source, its `canImport`/`importData` for a drop target, and `exportToClipboard`/`importData` for
 * clipboard round-trips - asserting the observable outcomes (the produced `Transferable`, the fired
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
        // and, and combination is a bitwise or - the contract documented on the modifiers.
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
                    SwingModifier.draggable(TransferHandler.COPY) { StringSelection("payload") },
            )
        }
        val source = onNodeOfType<JTextField>().fetch()

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
                modifier = SwingModifier.draggable(TransferHandler.COPY) { payload },
            )
        }
        val source = onNodeOfType<JTextField>().fetch()

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
                    SwingModifier.dropTarget(
                        acceptedActions = TransferHandler.COPY,
                        onDrop = { t ->
                            dropped = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                    ),
            )
        }
        val target = onNodeOfType<JTextField>().fetch()

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
                    SwingModifier.dropTarget(
                        acceptedActions = TransferHandler.COPY,
                        onDrop = {
                            dropCalls++
                            true
                        },
                        canImport = { flavors -> DataFlavor.javaFileListFlavor in flavors },
                    ),
            )
        }
        val target = onNodeOfType<JTextField>().fetch()

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
                    SwingModifier.clipboard(
                        transferable = { StringSelection("roundtrip") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
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
                    SwingModifier.clipboard(
                        transferable = { StringSelection("cut-me") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
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
        val clipboard = localClipboard()
        var pasted: String? = null
        lateinit var handle: ClipboardHandle
        setContent {
            handle = remember { ClipboardHandle { clipboard } }
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection("handle-copy-then-paste") },
                        onPaste = { t ->
                            pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                            true
                        },
                        bindKeys = false,
                        handle = handle,
                    ),
            )
        }

        handle.copy()
        assertEquals(
            "handle-copy-then-paste",
            clipboard.getData(DataFlavor.stringFlavor) as String,
            "copy must export the value the bound component produces onto the handle's clipboard",
        )
        assertTrue(handle.paste(), "paste must report the import the bound component performed")
        assertEquals(
            "handle-copy-then-paste",
            pasted,
            "the value the bound component exported must survive copy then paste",
        )
    }

    @Test
    fun clipboardHandlePasteReportsARefusedImport() = runComposeSwingTest {
        val clipboard = localClipboard()
        var pasteCalls = 0
        lateinit var handle: ClipboardHandle
        setContent {
            handle = remember { ClipboardHandle { clipboard } }
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection("handle-refused-import") },
                        onPaste = {
                            pasteCalls++
                            false
                        },
                        bindKeys = false,
                        handle = handle,
                    ),
            )
        }

        handle.copy()
        assertFalse(handle.paste(), "paste must report the import the bound component refused")
        assertEquals(1, pasteCalls, "the refusal must come from onPaste having run, not from skipping it")
    }

    @Test
    fun clipboardHandleCopiesAsCopyAndCutsAsMove() = runComposeSwingTest {
        val clipboard = localClipboard()
        val exports = mutableListOf<Int>()
        lateinit var handle: ClipboardHandle
        setContent {
            handle = remember { ClipboardHandle { clipboard } }
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection("handle-export-actions") },
                            onPaste = { true },
                            bindKeys = false,
                            handle = handle,
                        ).onExportDone { _, _, action -> exports += action },
            )
        }

        handle.copy()
        handle.cut()
        assertEquals(
            listOf(TransferHandler.COPY, TransferHandler.MOVE),
            exports,
            "copy must complete as COPY and cut as MOVE, so a source removes only the moved data",
        )
    }

    @Test
    fun clipboardHandleTargetsTheSystemClipboard() = runComposeSwingTest {
        assumeTrue(systemClipboard != null, "requires a reachable system clipboard")
        lateinit var handle: ClipboardHandle
        setContent {
            handle = rememberClipboardHandle()
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection("handle-never-exported") },
                        onPaste = { false },
                        bindKeys = false,
                        handle = handle,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()

        // Interpose a handler that records the clipboard the handle picked and exports nothing, so the
        // choice is observable without putting a value on the clipboard this host shares with every
        // other application running on it.
        val recording = RecordingClipboardHandler()
        component.transferHandler = recording

        handle.copy()
        assertSame(
            Toolkit.getDefaultToolkit().systemClipboard,
            recording.clipboard,
            "a handle from rememberClipboardHandle must export to the system clipboard",
        )
        assertEquals(TransferHandler.COPY, recording.action, "copy must ask for a COPY export")

        handle.cut()
        assertEquals(TransferHandler.MOVE, recording.action, "cut must ask for a MOVE export")
    }

    // A TransferHandler that records the clipboard and action a clipboard export was asked for and
    // exports nothing, so a test can observe which clipboard drives an export without writing to it.
    private class RecordingClipboardHandler : TransferHandler() {
        var clipboard: Clipboard? = null
        var action: Int? = null

        override fun exportToClipboard(
            comp: JComponent?,
            clip: Clipboard?,
            action: Int,
        ) {
            clipboard = clip
            this.action = action
        }
    }

    @Test
    fun clipboardHandleIsInertWhileNoClipboardIsReachable() = runComposeSwingTest {
        val exports = mutableListOf<Int>()
        var pasted: String? = null
        lateinit var handle: ClipboardHandle
        setContent {
            // A handle over an absent clipboard - what an environment without one leaves the handle
            // holding - so the behavior is pinned on every host, not only on those lacking one.
            handle = remember { ClipboardHandle { null } }
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection("handle-unreachable-clipboard") },
                            onPaste = { t ->
                                pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                                true
                            },
                            bindKeys = false,
                            handle = handle,
                        ).onExportDone { _, _, action -> exports += action },
            )
        }

        handle.copy()
        handle.cut()
        assertEquals(emptyList(), exports, "no export can start while no clipboard is reachable")
        assertFalse(handle.paste(), "paste must report failure while no clipboard is reachable")
        assertNull(pasted, "onPaste must not fire while there is no clipboard to read")
    }

    @Test
    fun clipboardHandleTransfersNothingWhileTheClipboardIsUnavailable() = runComposeSwingTest {
        val payload = StringSelection("handle-unavailable-clipboard")
        val exports = mutableListOf<Pair<Transferable?, Int>>()
        var pasted: String? = null
        lateinit var handle: ClipboardHandle
        setContent {
            // A clipboard that is reachable but refuses every access, the state one another application
            // holds open leaves it in. No host can be asked for that state, so the test supplies it.
            handle = remember { ClipboardHandle { UnavailableClipboard() } }
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { payload },
                            onPaste = { t ->
                                pasted = t.getTransferData(DataFlavor.stringFlavor) as String
                                true
                            },
                            bindKeys = false,
                            handle = handle,
                        ).onExportDone { _, data, action -> exports += data to action },
            )
        }

        // A refused clipboard is an environment failure, not a caller error: copy and cut report the
        // export as having transferred nothing instead of failing the call.
        handle.copy()
        handle.cut()
        val refused: List<Pair<Transferable?, Int>> =
            listOf(payload to TransferHandler.NONE, payload to TransferHandler.NONE)
        assertEquals(
            refused,
            exports,
            "a refused export must be reported as transferring nothing, carrying the data it could not take",
        )
        assertFalse(handle.paste(), "paste must report failure while the clipboard refuses to be read")
        assertNull(pasted, "onPaste must not fire while the clipboard cannot be read")
    }

    // A clipboard that refuses every access, as the JDK's own does while another application holds the
    // platform clipboard open: both directions throw IllegalStateException.
    private class UnavailableClipboard : Clipboard("unavailable") {
        override fun setContents(
            contents: Transferable?,
            owner: ClipboardOwner?,
        ): Unit = error("the clipboard cannot be opened")

        override fun getContents(requestor: Any?): Transferable = error("the clipboard cannot be opened")
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
                    SwingModifier.clipboard(
                        transferable = { StringSelection("x") },
                        onPaste = { true },
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()

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
        val component = onNodeOfType<JTextField>().fetch()

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
    fun aDragOnlyComponentKeepsTheImportItAlreadyHad() = runComposeSwingTest {
        var edited: String? = null
        setContent {
            TextField(
                value = "",
                onValueChange = { edited = it },
                modifier = SwingModifier.draggable(TransferHandler.COPY) { StringSelection("out") },
            )
        }
        val source = onNodeOfType<JTextField>().fetch()
        val handler = assertNotNull(source.transferHandler, "draggable must install a TransferHandler")

        // The component declares no drop, so its import stays its own: a text field's paste still
        // inserts the value, exactly as it does with no data-transfer modifier applied.
        val incoming = support(source, StringSelection("pasted-in"))
        assertTrue(handler.canImport(incoming), "a component with no declared drop keeps its own import")
        assertTrue(handler.importData(incoming), "the component's own import must run the paste")
        assertEquals("pasted-in", edited, "the pasted value must reach the text field")
    }

    @Test
    fun aDropOnlyComponentKeepsTheActionsItOffersOnItsOwn() = runComposeSwingTest {
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.dropTarget(TransferHandler.COPY, onDrop = { true }),
            )
        }
        val target = onNodeOfType<JTextField>().fetch()
        val handler = assertNotNull(target.transferHandler, "dropTarget must install a TransferHandler")

        // No drag source is declared, so the offered actions stay the editable text field's own -
        // which include the copy it offers with no data-transfer modifier applied.
        assertTrue(
            (handler.getSourceActions(target) and TransferHandler.COPY) != 0,
            "a component with no declared drag source keeps the actions it offers on its own",
        )
        // The declared export is the only one this handler produces, so with none declared it
        // produces nothing and a drag or clipboard export started on it carries no payload.
        val clipboard = localClipboard()
        handler.exportToClipboard(target, clipboard, TransferHandler.COPY)
        assertFalse(
            clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor),
            "a component with no declared drag source exports nothing",
        )
    }

    @Test
    fun aDeclaredDropOwnsTheImportInPlaceOfTheComponentsOwn() = runComposeSwingTest {
        var edited: String? = null
        var drops = 0
        setContent {
            TextField(
                value = "",
                onValueChange = { edited = it },
                modifier =
                    SwingModifier.dropTarget(
                        acceptedActions = TransferHandler.COPY,
                        onDrop = {
                            drops++
                            false
                        },
                    ),
            )
        }
        val target = onNodeOfType<JTextField>().fetch()
        val handler = assertNotNull(target.transferHandler, "dropTarget must install a TransferHandler")

        // The declared drop decides the import alone: its refusal is the outcome, and the paste the
        // text field performs on its own never runs beside it.
        assertFalse(
            handler.importData(support(target, StringSelection("dropped"))),
            "the declared drop's refusal must be the import's outcome",
        )
        assertEquals(1, drops, "the declared onDrop must run for the import")
        assertNull(edited, "the component's own import must not run beside the declared one")
    }

    @Test
    fun removingTheDropTargetModifierRestoresTheOriginalHandler() = runComposeSwingTest {
        var enabled by mutableStateOf(true)
        var drops = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    if (enabled) {
                        SwingModifier.dropTarget(
                            acceptedActions = TransferHandler.COPY,
                            onDrop = {
                                drops++
                                true
                            },
                        )
                    } else {
                        SwingModifier
                    },
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        assertTrue(
            component.transferHandler.importData(support(component, StringSelection("x"))),
            "while installed the modifier's handler accepts the drop",
        )
        assertEquals(1, drops, "the declared onDrop ran for the accepted drop")

        enabled = false
        awaitIdle()

        // A TextField ships with a TransferHandler of its own, and that is what a drop reaches once
        // the modifier leaves the chain - so the declared onDrop is no longer part of the import.
        val restored = assertNotNull(component.transferHandler, "the component's own handler must be back")
        restored.importData(support(component, StringSelection("y")))
        assertEquals(1, drops, "the declared onDrop must not run once its element leaves the chain")
    }

    @Test
    fun draggableSeesTheLatestExporterAcrossRecomposition() = runComposeSwingTest {
        var payload by mutableStateOf("first")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier = SwingModifier.draggable(TransferHandler.COPY) { StringSelection(payload) },
            )
        }
        val source = onNodeOfType<JTextField>().fetch()
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
