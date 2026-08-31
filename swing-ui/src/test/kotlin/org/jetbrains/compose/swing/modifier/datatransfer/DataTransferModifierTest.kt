package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.datatransfer.Clipboard
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
 * Behavioral tests for the drag-and-drop data-transfer modifiers. They drive the real
 * `TransferHandler` the modifiers install on the live component - its `getSourceActions`/
 * `createTransferable` for a drag source and its `canImport`/`importData` for a drop target -
 * asserting the observable outcomes (the produced `Transferable`, the fired `onDrop`, the gated
 * flavors, the handler a component keeps or loses as declarations combine). No native peer is
 * required.
 */
class DataTransferModifierTest {
    @Test
    fun transferActionMembershipAndCombination() {
        // The @TransferAction typed Int is a TransferHandler action bit-mask - membership is a bitwise
        // and, combination a bitwise or - the contract documented on the modifiers.
        assertTrue((TransferHandler.COPY_OR_MOVE and TransferHandler.COPY) != 0, "CopyOrMove must contain Copy")
        assertTrue((TransferHandler.COPY_OR_MOVE and TransferHandler.MOVE) != 0, "CopyOrMove must contain Move")
        assertEquals(0, TransferHandler.COPY_OR_MOVE and TransferHandler.LINK, "CopyOrMove must not contain Link")
        assertEquals(
            TransferHandler.COPY_OR_MOVE,
            TransferHandler.COPY or TransferHandler.MOVE,
            "COPY or MOVE must equal COPY_OR_MOVE",
        )
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
                // TransferHandler.createTransferable is protected, so exporting to a clipboard is the
                // only public entry that makes the source produce its Transferable.
                handler.exportToClipboard(source, clipboard, TransferHandler.COPY)
                clipboard.getData(DataFlavor.stringFlavor) as String
            }
        assertEquals("payload", exported, "the drag source must produce its Transferable for export")
    }

    // Records its drag-export entry point so a test can prove the drag gesture reached exportAsDrag,
    // distinct from the clipboard exportToClipboard path.
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
            // Pull the payload exactly as the real DnD machinery would the moment a drag begins, so the
            // recorded drag carries the same instance a real SharedTransferHandler would export.
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

        // The gesture the modifier installed is one instance, both a MouseListener and a MouseMotionListener.
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

        press.mousePressed(mouseEvent(source, MouseEvent.MOUSE_PRESSED, 10, 10))
        gesture.mouseDragged(mouseEvent(source, MouseEvent.MOUSE_DRAGGED, 11, 11))
        assertNull(recording.draggedAction, "a sub-threshold move must not start a drag")

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
    fun theChainsLastImportDeclarationOwnsTheImportAcrossRecompositions() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        var pasted: String? = null
        var dropped: String? = null
        // A clipboard and a drop target both declare the component's import, and the one that comes
        // last in the chain owns it. The two declarations are deliberately asymmetric: onDrop is one
        // instance across passes, while onPaste captures the pass it was written on and is a new
        // instance each time.
        val onDrop: (Transferable) -> Boolean = { transferable ->
            dropped = transferable.getTransferData(DataFlavor.stringFlavor) as String
            true
        }
        setContent {
            val pass = tick
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection("exported") },
                            onPaste = {
                                pasted = "pasted on pass $pass"
                                true
                            },
                        ).dropTarget(acceptedActions = TransferHandler.COPY, onDrop = onDrop),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()

        tick++
        awaitIdle()

        val arrived = support(component, StringSelection("arrived"))
        assertTrue(component.transferHandler.importData(arrived), "the import declared last must accept the transfer")
        assertEquals("arrived", dropped, "the chain's last import declaration must receive the transfer")
        assertNull(pasted, "an earlier import declaration must not take the import from a later one")
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

        val incoming = support(source, StringSelection("pasted-in"))
        assertTrue(handler.canImport(incoming), "a component with no declared drop keeps its own import")
        assertTrue(handler.importData(incoming), "the component's own import must run the paste")
        assertEquals("pasted-in", edited, "the pasted value must reach the text field")
    }

    @Test
    fun aDropOnlyComponentKeepsTheExportItAlreadyHad() = runComposeSwingTest {
        setContent {
            TextField(
                value = "copy me",
                onValueChange = {},
                modifier =
                    SwingModifier.dropTarget(TransferHandler.COPY, onDrop = { true }),
            )
        }
        val target = onNodeOfType<JTextField>().fetch()
        val handler = assertNotNull(target.transferHandler, "dropTarget must install a TransferHandler")
        // A selection is what a text field exports, so give it one for the export to carry.
        target.selectAll()

        assertTrue(
            (handler.getSourceActions(target) and TransferHandler.COPY) != 0,
            "a component with no declared drag source keeps the actions it offers on its own",
        )
        val clipboard = localClipboard()
        handler.exportToClipboard(target, clipboard, TransferHandler.COPY)
        assertTrue(
            clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor),
            "a component with no declared drag source keeps the export it performs on its own",
        )
        assertEquals(
            "copy me",
            clipboard.getData(DataFlavor.stringFlavor) as String,
            "the export must carry the value the component exports on its own",
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

    @Test
    fun everyDataTransferBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { draggable(TransferHandler.COPY) { null } }
        assertDeclaredChainCarriedOnce { dropTarget(TransferHandler.COPY, onDrop = { true }) }
        assertDeclaredChainCarriedOnce { onExportDone { _, _ -> } }
    }
}
