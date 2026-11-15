// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.Test
import java.awt.event.ActionListener
import java.beans.VetoableChangeListener
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.event.CaretListener
import javax.swing.event.ChangeListener
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests that [componentListeners] finds the listeners of every public listener API. */
class ComponentListenerCountTest {
    @Test
    fun `public listener APIs include widget and bean listeners`() =
        runSwingTest {
            val button = JButton()
            val slider = JSlider()
            val text = JTextField()
            val panel =
                JPanel().apply {
                    add(button)
                    add(slider)
                    add(text)
                }
            val action = ActionListener {}
            val change = ChangeListener {}
            val caret = CaretListener {}
            val vetoable = VetoableChangeListener {}
            button.addActionListener(action)
            slider.addChangeListener(change)
            text.addCaretListener(caret)
            panel.addVetoableChangeListener(vetoable)
            assertTrue(action in componentListeners(button))
            assertTrue(change in componentListeners(slider))
            assertTrue(caret in componentListeners(text))
            assertTrue(vetoable in componentListeners(panel))
            button.removeActionListener(action)
            slider.removeChangeListener(change)
            text.removeCaretListener(caret)
            panel.removeVetoableChangeListener(vetoable)
            assertFalse(action in componentListeners(button))
            assertFalse(change in componentListeners(slider))
            assertFalse(caret in componentListeners(text))
            assertFalse(vetoable in componentListeners(panel))
        }
}
