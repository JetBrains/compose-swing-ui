package org.jetbrains.compose.swing.samples.agent.i18n

import java.awt.ComponentOrientation
import java.text.Bidi

/**
 * The direction [text] reads in, from its first letter that has one, as HTML's `dir="auto"` decides it. Content
 * such as a prompt or an answer follows its own language rather than the window's orientation, so English prose
 * stays left to right in a right-to-left window.
 */
internal fun componentOrientationOf(text: String): ComponentOrientation =
    if (Bidi(text, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT).baseIsLeftToRight()) {
        ComponentOrientation.LEFT_TO_RIGHT
    } else {
        ComponentOrientation.RIGHT_TO_LEFT
    }
