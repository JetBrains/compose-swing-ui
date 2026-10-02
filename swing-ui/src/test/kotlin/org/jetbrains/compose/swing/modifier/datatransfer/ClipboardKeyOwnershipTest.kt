package org.jetbrains.compose.swing.modifier.datatransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.keyboard.onKeyStroke
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.KeyStroke
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ClipboardKeyOwnershipTest {
    @Test
    fun clipboardWithdrawalPreservesASiblingsDeclaredCopyShortcut() = runComposeSwingTest {
        var bindKeys by mutableStateOf(true)
        val shortcut = KeyStroke.getKeyStroke('C'.code, menuShortcutMask())
        val action: () -> Unit = {}
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            transferable = { StringSelection("scratch") },
                            onPaste = { true },
                            bindKeys = bindKeys,
                        ).onKeyStroke(shortcut, onAction = action),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val inputMap = field.getInputMap(JComponent.WHEN_FOCUSED)
        val held = inputMap.get(shortcut)
        assertTrue(held != null, "the sibling shortcut must initially be installed")
        bindKeys = false
        awaitIdle()
        assertSame(held, inputMap.get(shortcut), "withdrawing clipboard keys must preserve the sibling shortcut")
    }

    @Test
    fun clipboardAndShortcutRemovalRestoreLocalAndInheritedBindings() = runComposeSwingTest {
        val stroke = KeyStroke.getKeyStroke('C'.code, menuShortcutMask())
        var clipboardShown by mutableStateOf(true)
        var shortcutShown by mutableStateOf(true)
        val action: () -> Unit = {}
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    (if (clipboardShown) SwingModifier.clipboard({ null }, { false }) else SwingModifier)
                        .then(
                            if (shortcutShown) SwingModifier.onKeyStroke(stroke, onAction = action) else SwingModifier,
                        ),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val map = field.getInputMap(JComponent.WHEN_FOCUSED)
        val inherited = map.parent.get(stroke)
        val sibling = map.get(stroke)
        assertNotNull(sibling, "the declared shortcut must own the copy stroke")
        clipboardShown = false
        awaitIdle()
        assertSame(sibling, map.get(stroke), "the sibling must survive clipboard removal")
        shortcutShown = false
        awaitIdle()
        assertSame(inherited, map.get(stroke), "removing both declarations must restore the inherited binding")
        assertFalse(map.keys()?.contains(stroke) ?: false, "an inherited binding stays inherited")
    }

    @TestFactory
    fun shortcutOwnersRestoreDisplacedLocalEntriesInBothOrders(): List<DynamicTest> =
        listOf("copy" to 'C', "cut" to 'X', "paste" to 'V').flatMap { (action, letter) ->
            listOf(true, false).flatMap { clipboardFirst ->
                listOf(true, false).map { removeClipboardFirst ->
                    val stroke = KeyStroke.getKeyStroke(letter.code, menuShortcutMask())
                    val case =
                        "$action stroke=$stroke clipboardFirst=$clipboardFirst " +
                            "removeClipboardFirst=$removeClipboardFirst"
                    dynamicTest(case) {
                        assertShortcutOwnership(letter, clipboardFirst, removeClipboardFirst, case)
                    }
                }
            }
        }

    private fun assertShortcutOwnership(
        letter: Char,
        clipboardFirst: Boolean,
        removeClipboardFirst: Boolean,
        case: String,
    ) = runComposeSwingTest {
        val stroke = KeyStroke.getKeyStroke(letter.code, menuShortcutMask())
        val original = Any()
        val field = JTextField().apply { getInputMap().put(stroke, original) }
        var clipboardShown by mutableStateOf(true)
        var shortcutShown by mutableStateOf(true)
        var called = 0
        val callback: () -> Unit = { called++ }
        setContent {
            val clipboard = if (clipboardShown) SwingModifier.clipboard({ null }, { false }) else SwingModifier
            val shortcut = if (shortcutShown) SwingModifier.onKeyStroke(stroke, onAction = callback) else SwingModifier
            SwingNode(
                factory = { field },
                modifier =
                    if (clipboardFirst) {
                        clipboard then shortcut
                    } else {
                        shortcut then
                            clipboard
                    },
            )
        }
        val map = field.getInputMap()
        assertNotNull(map.get(stroke), "$case: the last declaration owns the stroke")
        assertNotSame(original, map.get(stroke), "$case: a declaration must displace the original local entry")
        if (removeClipboardFirst) clipboardShown = false else shortcutShown = false
        awaitIdle()
        assertNotNull(map.get(stroke), "$case: the surviving declaration still owns the stroke")
        assertNotSame(original, map.get(stroke), "$case: a declaration must displace the original local entry")
        if (removeClipboardFirst) {
            field.actionMap.get(map.get(stroke)).actionPerformed(null)
            assertEquals(1, called, "$case: the surviving shortcut must invoke its callback once")
        } else {
            val expected =
                when (letter) {
                    'C' -> TransferHandler.getCopyAction()
                    'X' -> TransferHandler.getCutAction()
                    else -> TransferHandler.getPasteAction()
                }
            assertSame(
                expected,
                field.actionMap.get(map.get(stroke)),
                "$case: the surviving clipboard action must be installed",
            )
        }
        clipboardShown = false
        shortcutShown = false
        awaitIdle()
        assertSame(original, map.get(stroke), "$case: removing both declarations must restore the original local entry")
    }

    @Test
    fun aCallerReplacingShortcutEntriesKeepsThemAfterDetach() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val strokes = listOf('C', 'X', 'V').map { KeyStroke.getKeyStroke(it.code, menuShortcutMask()) }
        val replacements = strokes.map { Any() }
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    if (shown) {
                        SwingModifier
                            .clipboard(
                                { null },
                                { false },
                            ).onKeyStroke(strokes.first()) {}
                    } else {
                        SwingModifier
                    },
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val actionKeys = strokes.map { field.getInputMap().get(it) }
        val externalAction =
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) = Unit
            }
        actionKeys.forEach { field.actionMap.put(it, externalAction) }
        strokes.forEachIndexed { index, stroke -> field.getInputMap().put(stroke, replacements[index]) }
        shown = false
        awaitIdle()
        strokes.forEachIndexed { index, stroke ->
            assertSame(
                replacements[index],
                field.getInputMap().get(stroke),
                "$stroke: caller replacement must survive detach",
            )
        }
        actionKeys.forEach { key ->
            assertSame(externalAction, field.actionMap.get(key), "$key: caller action must survive detach")
        }
    }

    @Test
    fun clipboardKeyTogglesLeaveInheritedDefaultsAndOtherStrokesUntouched() = runComposeSwingTest {
        var bindKeys by mutableStateOf(false)
        val unaffected = KeyStroke.getKeyStroke("control F")
        var called = 0
        val callback: () -> Unit = { called++ }
        setContent {
            TextField(
                value = "",
                onValueChange = {},
                modifier =
                    SwingModifier
                        .clipboard(
                            { null },
                            { false },
                            bindKeys = bindKeys,
                        ).onKeyStroke(unaffected, onAction = callback),
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        val map = field.getInputMap()
        val strokes = listOf('C', 'X', 'V').map { KeyStroke.getKeyStroke(it.code, menuShortcutMask()) }
        val defaults = strokes.map { map.get(it) }
        val sibling = map.get(unaffected)
        repeat(2) {
            bindKeys = true
            awaitIdle()
            strokes.forEach { stroke ->
                assertNotNull(map.get(stroke), "$stroke: enabling clipboard keys must install the binding")
            }
            assertSame(sibling, map.get(unaffected), "clipboard toggles must preserve the unrelated shortcut")
            bindKeys = false
            awaitIdle()
            strokes.forEachIndexed { index, stroke ->
                assertSame(
                    defaults[index],
                    map.get(stroke),
                    "$stroke: disabling clipboard keys must restore the default",
                )
                assertFalse(map.keys()?.contains(stroke) ?: false, "$stroke: an inherited default must stay inherited")
            }
            assertSame(sibling, map.get(unaffected), "clipboard toggles must preserve the unrelated shortcut")
        }
        field.actionMap.get(sibling).actionPerformed(null)
        assertEquals(1, called, "the unrelated shortcut must invoke its callback once")
    }
}
