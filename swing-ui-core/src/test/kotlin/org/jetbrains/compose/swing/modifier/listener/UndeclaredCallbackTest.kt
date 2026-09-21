// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.onFocus
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import kotlin.test.Test

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
        assertRefused("focusListener") { SwingModifier.focusListener() }
        assertRefused("keyListener") { SwingModifier.keyListener() }
        assertRefused("mouseListener") { SwingModifier.mouseListener() }
        assertRefused("mouseMotionListener") { SwingModifier.mouseMotionListener() }
        assertRefused("onHover") { SwingModifier.onHover() }
        assertRefused("onFocus") { SwingModifier.onFocus() }
        assertRefused("onPointerEvent") { SwingModifier.onPointerEvent() }
    }

    @Test
    fun aBuilderDeclaringOneCallbackIsAccepted() {
        assertDeclares("componentListener") { SwingModifier.componentListener(onComponentMoved = {}) }
        assertDeclares("containerListener") { SwingModifier.containerListener(onComponentRemoved = {}) }
        assertDeclares("focusListener") { SwingModifier.focusListener(onFocusLost = {}) }
        assertDeclares("keyListener") { SwingModifier.keyListener(onKeyReleased = {}) }
        assertDeclares("mouseListener") { SwingModifier.mouseListener(onMouseExited = {}) }
        assertDeclares("mouseMotionListener") { SwingModifier.mouseMotionListener(onMouseMoved = {}) }
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
