// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.math.BigDecimal
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The class a number spinner's bounds are held in. A `SpinnerNumberModel` compares each bound against
 * the value through `Comparable`, which a concrete `Number` implements against its own class alone, so
 * bounds are converted to the value's class rather than left to throw a `ClassCastException`.
 */
class SpinnerBoundsClassTest {
    @Test
    fun aBoundDeclaredInAnotherClassIsHeldInTheValues() = runComposeSwingTest {
        setContent { Spinner(value = 1, onValueChange = {}, min = 0.0, max = 10.0) }

        val spinner = onNodeOfType<JSpinner>().fetch<JSpinner>()
        val model = spinner.model as SpinnerNumberModel

        assertEquals(0, model.minimum, "the bound is held as the value's own class")
        assertEquals(10, model.maximum, "and so is the other one")
        assertEquals(2, spinner.nextValue, "so the model can compare them and step the value")
    }

    @Test
    fun boundsFollowAValueDeclaredInAnotherClass() = runComposeSwingTest {
        // The bounds outlive the class they were declared under: a value declared afresh in another class
        // would otherwise leave the model comparing across two, which throws where the user steps it.
        var value by mutableStateOf<Number>(1)
        setContent { Spinner(value = value, onValueChange = { value = it }, min = 0, max = 10, step = 0.5) }

        val spinner = onNodeOfType<JSpinner>().fetch<JSpinner>()
        value = 2.5
        awaitIdle()

        val model = spinner.model as SpinnerNumberModel
        assertEquals(0.0, model.minimum, "the bound is held as the value's new class")
        assertEquals(10.0, model.maximum, "and so is the other one")
        assertEquals(3.0, spinner.nextValue, "a step against bounds of that class")
        assertEquals(2.0, spinner.previousValue, "and one the other way")
    }

    @Test
    fun aBoundTheValuesClassCannotHoldIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent { Spinner(value = 1, onValueChange = {}, min = 0.5) }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("0.5") &&
                failure.message.orEmpty().contains("java.lang.Integer"),
            "the refusal must name the bound and the class that cannot hold it: ${failure.message}",
        )
    }

    @Test
    fun aBoundOverAValueTheModelCannotStepInItsOwnClassIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent { Spinner(value = BigDecimal("1"), onValueChange = {}, max = 10) }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("java.math.BigDecimal"),
            "the refusal must name the value's class, which no bound can be compared against: " +
                "${failure.message}",
        )
    }

    @Test
    fun anInfiniteBoundIsHeldInTheValuesClassLikeAnyOther() = runComposeSwingTest {
        setContent {
            Spinner(value = 1.0f, onValueChange = {}, min = Double.NEGATIVE_INFINITY)
        }

        val model = onNodeOfType<JSpinner>().fetch<JSpinner>().model as SpinnerNumberModel

        assertEquals(Float.NEGATIVE_INFINITY, model.minimum, "an infinity a Float holds exactly is carried across")
    }

    @Test
    fun aBoundIsCarriedAcrossAsTheNumberItHeldRatherThanAsItsText() = runComposeSwingTest {
        // A Double holds every Float exactly, and what it holds is the Float's own value, not the
        // shortest decimal that prints it.
        setContent {
            Spinner(value = 1.0, onValueChange = {}, min = 0.1f)
        }

        val model = onNodeOfType<JSpinner>().fetch<JSpinner>().model as SpinnerNumberModel

        assertEquals(0.1f.toDouble(), model.minimum, "the bound stands as the number the Float held")
    }

    @Test
    fun aBoundNoNarrowingHoldsExactlyIsRefused() = runComposeSwingTest {
        // 0.1 is a Double no Float holds, so it is refused rather than narrowed into one the spinner
        // would honor.
        val failure =
            assertFailsWith<IllegalArgumentException> {
                setContent {
                    Spinner(value = 1.0f, onValueChange = {}, min = 0.1)
                }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("java.lang.Float"),
            "the refusal must name the class that cannot hold the bound: ${failure.message}",
        )
    }
}
