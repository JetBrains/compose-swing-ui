// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/** The composable displaying the value keeps the same [State] when its animation is replaced. */
class SingleValueAnimationSwingTest {
    @ParameterizedTest(name = "label={0}, replace converter={1}")
    @CsvSource("after, false", "before, true")
    fun `changing the label or converter updates the rendered value`(
        nextLabel: String,
        replaceConverter: Boolean,
    ) = runComposeSwingTest {
        var replacementReceivedTarget = false
        val replacementConverter =
            TwoWayConverter(
                convertToVector = { value: Int ->
                    if (value == 100) replacementReceivedTarget = true
                    Int.VectorConverter.convertToVector(value)
                },
                convertFromVector = Int.VectorConverter.convertFromVector,
            )
        var target by mutableIntStateOf(0)
        mainClock.autoAdvance = false
        setContent {
            val updated = target == 100
            AnimatedLabel(
                animateValueAsState(
                    targetValue = target,
                    typeConverter = if (updated && replaceConverter) replacementConverter else Int.VectorConverter,
                    animationSpec = tween(100),
                    label = if (updated) nextLabel else "before",
                ),
            )
        }
        onNodeWithText("0").assertExists()

        target = 100
        mainClock.advanceTimeBy(160.milliseconds)
        awaitIdle()

        onNodeWithText("100").assertExists()
        assertEquals(replaceConverter, replacementReceivedTarget)
    }
}

@Composable
private fun AnimatedLabel(value: State<Int>) {
    Label(text = value.value.toString())
}
