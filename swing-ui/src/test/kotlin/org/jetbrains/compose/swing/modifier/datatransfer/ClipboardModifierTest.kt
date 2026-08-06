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
 * Behavioral tests for the clipboard data-transfer modifier and its key bindings. They drive the
 * real `TransferHandler` the modifier installs on the live component - its `exportToClipboard`/
 * `importData` for copy/cut/paste round-trips through a [ClipboardHandle], and its `InputMap`/
 * `ActionMap` for the standard copy/cut/paste keystrokes - asserting the observable outcomes (a
 * value that survives copy-then-paste, the action a completed export reports, the keystrokes bound
 * or unbound as `bindKeys` changes). No native peer is required.
 */
class ClipboardModifierTest {
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
                        ).onExportDone { _, action -> exports += action },
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
            // A handle over an absent clipboard, so the behavior is pinned on every host, not only on
            // those lacking one.
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
                        ).onExportDone { _, action -> exports += action },
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
            // No host can be asked for the state a clipboard is left in while another application
            // holds it open, so the test supplies one that refuses every access.
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
                        ).onExportDone { data, action -> exports += data to action },
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
    // platform clipboard open.
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
    fun recomposingWithBindKeysFlippedBindsOrUnbindsTheStandardKeystrokes() = runComposeSwingTest {
        var bindKeys by mutableStateOf(true)
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier.clipboard(
                        transferable = { StringSelection("x") },
                        onPaste = { true },
                        bindKeys = bindKeys,
                    ),
            )
        }
        val component = onNodeOfType<JTextField>().fetch()

        fun boundActions(): Set<Any> {
            val inputMap = component.getInputMap(JComponent.WHEN_FOCUSED)
            val actionMap = component.actionMap
            return inputMap
                .allKeys()
                .orEmpty()
                .mapNotNull { stroke -> actionMap.get(inputMap.get(stroke)) }
                .toSet()
        }
        assertTrue(TransferHandler.getCopyAction() in boundActions(), "copy must be bound while bindKeys is true")

        bindKeys = false
        awaitIdle()
        assertTrue(
            TransferHandler.getCopyAction() !in boundActions(),
            "copy must no longer be bound once recomposition declares bindKeys false",
        )

        bindKeys = true
        awaitIdle()
        assertTrue(
            TransferHandler.getCopyAction() in boundActions(),
            "copy must be re-bound once recomposition declares bindKeys true again",
        )
    }
}
