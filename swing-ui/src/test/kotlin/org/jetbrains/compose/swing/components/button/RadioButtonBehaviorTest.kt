// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.button

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.button.RadioButtonFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral coverage for [RadioButton], asserted on the live `JRadioButton`.
 *
 * The selected state is controlled: the button shows whatever `selected` holds, a value pushed in from
 * composition applies without echoing back as a callback, and both edges of a user gesture - choosing
 * and clearing the button - report through `onSelectedChange`. A gesture the caller does not adopt does
 * not stand: the next settled pass writes the declared state back over it.
 */
class RadioButtonBehaviorTest {
    @Test
    fun anUndeclaredRadioButtonIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { RadioButton(text = "", selected = false, onSelectedChange = {}) }
        onNodeOfType<JRadioButton>().assertTreeMatches(JRadioButton())
    }

    @Test
    fun clickingReportsTheNewSelectedStateAndTheButtonKeepsIt() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            RadioButton(
                text = "Compact",
                selected = selected,
                onSelectedChange = {
                    reported += it
                    selected = it
                },
            )
        }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        assertEquals(listOf(true), reported, "the click reports the button being chosen")
        radioButton.assert(SwingMatcher.isSelected())

        // A button with no group is cleared by a second click, and that edge reaches the caller too.
        radioButton.performClick()
        assertEquals(listOf(true, false), reported, "the second click reports the button being cleared")
        radioButton.assert(SwingMatcher.isSelected(false))
    }

    @Test
    fun aSelectedStatePushedFromCompositionDoesNotFireTheCallback() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        var selects = 0
        setContent {
            RadioButton(text = "Compact", selected = selected, onSelectedChange = { selects++ })
        }

        selected = true
        awaitIdle()
        onNodeOfType<JRadioButton>().assert(SwingMatcher.isSelected())
        assertEquals(0, selects, "only a user gesture reports through the callback")
    }

    @Test
    fun choosingTheButtonReportsThroughTheMostRecentlyComposedCallback() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        var round by mutableStateOf(1)
        val runs = mutableListOf<Int>()
        setContent {
            // Each pass captures `round` at composition time, so a callback keeps reporting the round
            // it was built with, however often the declaration changes.
            val current = round
            RadioButton(
                text = "Compact",
                selected = selected,
                onSelectedChange = {
                    runs += current
                    selected = it
                },
            )
        }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        assertEquals(listOf(1), runs, "choosing the button runs the composed callback")
        radioButton.assert(SwingMatcher.isSelected())

        round = 2
        selected = false
        awaitIdle()
        radioButton.performClick()
        assertEquals(listOf(1, 2), runs, "the gesture runs the recomposed callback, not the one it replaced")
        radioButton.assertProperty(1, "recomposition must not stack up action listeners") { actionListeners.size }
    }

    @Test
    fun theModifierFollowsTheStateDrivingIt() = runComposeSwingTest {
        var tip by mutableStateOf<String?>("Denser layout")
        setContent {
            RadioButton(
                text = "Compact",
                selected = false,
                onSelectedChange = {},
                modifier = tip?.let { SwingModifier.name(it) } ?: SwingModifier,
            )
        }

        val radio = onNodeOfType<JRadioButton>()
        radio.assertProperty("Denser layout", "the modifier applies to the button") { name }

        tip = "Fits more rows on screen"
        awaitIdle()
        radio.assertProperty("Fits more rows on screen", "the modifier follows the state driving it") { name }

        tip = null
        awaitIdle()
        radio.assertProperty(null, "dropping the element restores the name the button had without it") { name }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("declaredSelections")
    fun aClickTheCallerDoesNotAdoptDoesNotStand(declared: Boolean) = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        setContent { RadioButton(text = text, selected = declared, onSelectedChange = {}) }

        val radioButton = onNodeOfType<JRadioButton>()
        radioButton.performClick()
        radioButton.assert(SwingMatcher.isSelected(declared))

        // An unrelated recomposition changes nothing: the button already showed the declared state
        // right after the click, not only once this pass ran.
        text = "Comfortable"
        awaitIdle()
        radioButton.assert(SwingMatcher.isSelected(declared))
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JRadioButton::class.java,
            declared = false,
            content = { report -> RadioButton(text = "Nightly", selected = false, onSelectedChange = { report() }) },
            change = { it.click() },
            read = { it.isSelected },
        )
    }

    @TestFactory
    fun radioButtonRawSwing() = RadioButtonFixture.rawSwingTests()

    @TestFactory
    fun radioButtonCounts() = RadioButtonFixture.countTests()

    companion object {
        @JvmStatic
        fun declaredSelections(): List<Named<Boolean>> = listOf(
            Named.of("a selecting click", false),
            Named.of("a clearing click", true),
        )
    }
}
