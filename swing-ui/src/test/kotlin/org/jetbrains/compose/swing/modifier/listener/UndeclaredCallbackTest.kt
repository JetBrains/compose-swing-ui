package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A builder taking one callback per method of a listener interface defaults every one of them, so that a
 * caller names only the methods they want - which leaves a call naming none of them compiling. Such a
 * call is refused, since the listener it would register is built out of the callbacks the call names and
 * can therefore never reach the caller, however the declaration changes afterwards.
 */
class UndeclaredCallbackTest {
    @Test
    fun aBuilderDeclaringNoCallbackIsRefused() {
        assertRefused("componentListener") { SwingModifier.componentListener() }
        assertRefused("containerListener") { SwingModifier.containerListener() }
        assertRefused("documentListener") { SwingModifier.documentListener() }
        assertRefused("focusListener") { SwingModifier.focusListener() }
        assertRefused("internalFrameListener") { SwingModifier.internalFrameListener() }
        assertRefused("keyListener") { SwingModifier.keyListener() }
        assertRefused("mouseListener") { SwingModifier.mouseListener() }
        assertRefused("mouseMotionListener") { SwingModifier.mouseMotionListener() }
        assertRefused("treeExpansionListener") { SwingModifier.treeExpansionListener() }
        assertRefused("treeWillExpandListener") { SwingModifier.treeWillExpandListener() }
        assertRefused("onHover") { SwingModifier.onHover() }
        assertRefused("onFocus") { SwingModifier.onFocus() }
        assertRefused("onPointerEvent") { SwingModifier.onPointerEvent() }
    }

    @Test
    fun aBuilderDeclaringOneCallbackIsAccepted() {
        assertDeclares("componentListener") { SwingModifier.componentListener(onComponentMoved = {}) }
        assertDeclares("containerListener") { SwingModifier.containerListener(onComponentRemoved = {}) }
        assertDeclares("documentListener") { SwingModifier.documentListener(onChange = {}) }
        assertDeclares("focusListener") { SwingModifier.focusListener(onFocusLost = {}) }
        assertDeclares("internalFrameListener") { SwingModifier.internalFrameListener(onFrameClosing = {}) }
        assertDeclares("keyListener") { SwingModifier.keyListener(onKeyReleased = {}) }
        assertDeclares("mouseListener") { SwingModifier.mouseListener(onMouseExited = {}) }
        assertDeclares("mouseMotionListener") { SwingModifier.mouseMotionListener(onMouseMoved = {}) }
        assertDeclares("treeExpansionListener") { SwingModifier.treeExpansionListener(onTreeCollapsed = {}) }
        assertDeclares("treeWillExpandListener") { SwingModifier.treeWillExpandListener(onWillCollapse = { true }) }
        assertDeclares("onHover") { SwingModifier.onHover(onExit = {}) }
        assertDeclares("onFocus") { SwingModifier.onFocus(onLost = {}) }
        assertDeclares("onPointerEvent") { SwingModifier.onPointerEvent(onClick = {}) }
    }

    /**
     * A callback that happens to do nothing is still the caller's own declaration: it reaches the widget's
     * listener list and a later pass can replace it, which is what tells it apart from a method the call
     * never named.
     */
    @Test
    fun aDoNothingLambdaIsADeclaration() {
        val doNothing: () -> Unit = {}
        assertDeclares("onHover") { SwingModifier.onHover(onEnter = doNothing, onExit = doNothing) }
        assertDeclares("mouseListener") { SwingModifier.mouseListener { } }
    }
}

private fun assertRefused(
    builder: String,
    declare: () -> SwingModifier,
) {
    val failure =
        assertFailsWith<IllegalArgumentException>("$builder should refuse a call that declares no callback") {
            declare()
        }
    assertTrue(
        failure.message.orEmpty().startsWith("$builder declares no callback"),
        "the refusal should name $builder, but said: ${failure.message}",
    )
}

private fun assertDeclares(
    builder: String,
    declare: () -> SwingModifier,
) {
    assertNotEquals(SwingModifier, declare(), "$builder should add an element to the chain")
}
