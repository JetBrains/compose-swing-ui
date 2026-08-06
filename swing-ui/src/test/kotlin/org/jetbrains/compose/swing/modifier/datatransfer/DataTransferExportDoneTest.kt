package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.JTextField
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Behavioral tests for the export-completion contract of the data-transfer modifiers: every export
 * reports the transferred data and the completed action (`COPY`, `MOVE`, or `NONE`) through the
 * component's `onExportDone` modifier, the seam where a source implements move semantics by removing
 * the moved data. They drive the real `TransferHandler` the modifiers install on the live component
 * and observe outcomes through its public export surface. No native peer is required.
 */
class DataTransferExportDoneTest {
    // A local clipboard standing in for the system clipboard, a shared environment-dependent global
    // that is absent entirely without a display. Driving the installed handler's exportToClipboard
    // against it asserts the exact export the public helpers perform on the real clipboard.
    private fun localClipboard(): Clipboard = Clipboard("data-transfer-export-done-test")

    @Test
    fun cutAndCopyExportTheSameValueDifferingOnlyInTheReportedAction() = runComposeSwingTest {
        // Counts how many times the source produced its Transferable: cut must NOT export more or
        // differently than copy - they share one exporter; only the action constant differs.
        var exports = 0
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = {
                            exports++
                            StringSelection("payload")
                        },
                        onPaste = { true },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        val copyClipboard = localClipboard()
        val cutClipboard = localClipboard()

        val (copied, cut) =
            run {
                val handler =
                    assertNotNull(component.transferHandler, "the clipboard modifier should install a transfer handler")
                handler.exportToClipboard(component, copyClipboard, TransferHandler.COPY)
                val copiedValue = copyClipboard.getData(DataFlavor.stringFlavor) as String
                handler.exportToClipboard(component, cutClipboard, TransferHandler.MOVE)
                val cutValue = cutClipboard.getData(DataFlavor.stringFlavor) as String
                copiedValue to cutValue
            }

        // The semantic contract: cut and copy export byte-for-byte the same value. The MOVE vs COPY
        // action is reported to Swing for the operation's intent, but the exported payload is
        // identical because both route through the single `transferable` exporter.
        assertEquals("payload", copied, "copy must export the source payload")
        assertEquals(copied, cut, "cut must export the same value copy does; only the action differs")
        assertEquals(2, exports, "both exports must go through the one source exporter")
    }

    @Test
    fun cutWithoutAnOnExportDoneModifierLeavesTheSourceIntact() = runComposeSwingTest {
        // A completed MOVE (cut) export reports through the component's export-completion seam; with
        // no onExportDone modifier the seam is empty - like TransferHandler.exportDone's own no-op -
        // so there is no automatic removal: the source still produces its value after a cut, and
        // removing the moved data is the exporter's onExportDone responsibility.
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection("cut-me") },
                        onPaste = { true },
                        bindKeys = false,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        val cutClipboard = localClipboard()
        val afterCutClipboard = localClipboard()

        val stillExportsSameValue =
            run {
                val handler =
                    assertNotNull(component.transferHandler, "the clipboard modifier should install a transfer handler")
                // Perform the cut.
                handler.exportToClipboard(component, cutClipboard, TransferHandler.MOVE)
                // No source-side removal ran, so a second export still produces the same value,
                // proving the cut alone did not clear the source.
                handler.exportToClipboard(component, afterCutClipboard, TransferHandler.COPY)
                afterCutClipboard.getData(DataFlavor.stringFlavor) as String
            }
        assertEquals(
            "cut-me",
            stillExportsSameValue,
            "cut must not auto-clear the source: removal is the exporter's onExportDone responsibility",
        )
    }

    @Test
    fun cutReportsMoveThroughOnExportDoneSoTheSourceCanRemoveTheData() = runComposeSwingTest {
        // Move semantics end to end: the source offers its value, and once the export completes as a
        // MOVE, onExportDone tells the source so it can remove the moved data - Swing's export
        // contract, driven entirely through the modifier's public surface.
        var value = "move-me"
        val reportedActions = mutableListOf<Int>()
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection(value) },
                            onPaste = { true },
                            bindKeys = false,
                        ).onExportDone { _, _, action ->
                            reportedActions += action
                            if (action == TransferHandler.MOVE) value = ""
                        },
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        val cutClipboard = localClipboard()
        val afterCutClipboard = localClipboard()

        val (cutValue, afterCut) =
            run {
                val handler =
                    assertNotNull(component.transferHandler, "the clipboard modifier should install a transfer handler")
                handler.exportToClipboard(component, cutClipboard, TransferHandler.MOVE)
                val cutValue = cutClipboard.getData(DataFlavor.stringFlavor) as String
                // A COPY probe re-reads the source, observing through the public export surface
                // whether the cut's MOVE cleanup ran.
                handler.exportToClipboard(component, afterCutClipboard, TransferHandler.COPY)
                cutValue to afterCutClipboard.getData(DataFlavor.stringFlavor) as String
            }
        assertEquals(
            listOf(TransferHandler.MOVE, TransferHandler.COPY),
            reportedActions,
            "each completed export must report its action through onExportDone",
        )
        assertEquals("move-me", cutValue, "the cut must place the moved value on the clipboard")
        assertEquals("", afterCut, "the source must be able to remove the moved data in onExportDone")
    }

    @Test
    fun copyReportsCopyThroughOnExportDoneAndTheSourceKeepsItsValue() = runComposeSwingTest {
        // The same move-aware source, driven as a COPY: the callback reports COPY, so the
        // MOVE-conditioned removal does not run and the source keeps its value.
        var value = "keep-me"
        var reportedAction: Int? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection(value) },
                            onPaste = { true },
                            bindKeys = false,
                        ).onExportDone { _, _, action ->
                            reportedAction = action
                            if (action == TransferHandler.MOVE) value = ""
                        },
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        val copyClipboard = localClipboard()
        val secondClipboard = localClipboard()

        val (copied, second) =
            run {
                val handler =
                    assertNotNull(component.transferHandler, "the clipboard modifier should install a transfer handler")
                handler.exportToClipboard(component, copyClipboard, TransferHandler.COPY)
                val copied = copyClipboard.getData(DataFlavor.stringFlavor) as String
                handler.exportToClipboard(component, secondClipboard, TransferHandler.COPY)
                copied to secondClipboard.getData(DataFlavor.stringFlavor) as String
            }
        assertEquals(TransferHandler.COPY, reportedAction, "a copy must report COPY through onExportDone")
        assertEquals("keep-me", copied, "the copy must place the value on the clipboard")
        assertEquals("keep-me", second, "a copy must leave the source's value in place")
    }

    @Test
    fun anExportThatProducesNothingReportsNoneWithNoData() = runComposeSwingTest {
        // When the source produces no Transferable, the export still completes its contract: the
        // callback receives NONE and null data, so the source knows nothing was transferred.
        var reportedAction: Int? = null
        var reportedData: Transferable? = StringSelection("sentinel")
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { null },
                            onPaste = { true },
                            bindKeys = false,
                        ).onExportDone { _, data, action ->
                            reportedAction = action
                            reportedData = data
                        },
            )
        }
        val component = onNodeOfType<JTextField>().fetch()

        component.transferHandler.exportToClipboard(component, localClipboard(), TransferHandler.COPY)

        assertEquals(TransferHandler.NONE, reportedAction, "an export with nothing to transfer must report NONE")
        assertNull(reportedData, "an export that transferred nothing carries no data")
    }

    @Test
    fun textFieldCutRemovesTheSelectionWhenOnExportDoneDeletesItOnMove() = runComposeSwingTest {
        // A field-level cut in miniature: the exporter offers the selected range and onExportDone
        // deletes it once told the export completed as a MOVE, so the field visibly loses the cut
        // text while the clipboard gains it.
        var text by mutableStateOf("keep CUT")
        val selection = 5..7
        setContent {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection(text.substring(selection)) },
                            onPaste = { true },
                            bindKeys = false,
                        ).onExportDone { _, _, action ->
                            if (action == TransferHandler.MOVE) text = text.removeRange(selection)
                        },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val clipboard = localClipboard()

        field.transferHandler.exportToClipboard(field, clipboard, TransferHandler.MOVE)
        awaitIdle()

        assertEquals(
            "CUT",
            clipboard.getData(DataFlavor.stringFlavor) as String,
            "the cut selection must land on the clipboard",
        )
        assertEquals("keep ", field.text, "the field must lose the cut selection")
    }

    @Test
    fun anOnExportDoneOnlyComponentKeepsTheCopyItAlreadyHad() = runComposeSwingTest {
        // Declaring onExportDone alone claims no source: with nothing of its own to export, the
        // component's own copy must still work, exactly as if no data-transfer modifier were declared.
        setContent {
            TextField(
                value = "copy me",
                onValueChange = {},
                modifier = SwingModifier.onExportDone { _, _, _ -> },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        field.selectAll()
        val clipboard = localClipboard()

        field.transferHandler.exportToClipboard(field, clipboard, TransferHandler.COPY)

        assertEquals(
            "copy me",
            clipboard.getData(DataFlavor.stringFlavor) as String,
            "an onExportDone-only component must keep the copy it already had",
        )
    }

    @Test
    fun theSeamReceivesExportsRegardlessOfWhichSiblingModifierOwnsTheExporter() = runComposeSwingTest {
        // One component, one handler, one completion seam: with a draggable owning the export slice
        // beside a clipboard modifier, a clipboard export carries the draggable's payload - and the
        // component's onExportDone still receives the completed action.
        var reportedAction: Int? = null
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .draggable(TransferHandler.COPY_OR_MOVE) { StringSelection("shared") }
                        .clipboard(
                            transferable = { StringSelection("unowned") },
                            onPaste = { true },
                            bindKeys = false,
                        ).onExportDone { _, _, action -> reportedAction = action },
            )
        }
        val component = onNodeOfType<JTextField>().fetch()
        val clipboard = localClipboard()

        component.transferHandler.exportToClipboard(component, clipboard, TransferHandler.MOVE)

        assertEquals(
            "shared",
            clipboard.getData(DataFlavor.stringFlavor) as String,
            "the owning draggable's exporter must produce the clipboard payload",
        )
        assertEquals(
            TransferHandler.MOVE,
            reportedAction,
            "the component's onExportDone must receive the completed action",
        )
    }
}
