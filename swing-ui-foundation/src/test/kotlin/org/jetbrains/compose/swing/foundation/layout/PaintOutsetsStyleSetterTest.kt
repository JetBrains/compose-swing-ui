// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.style.TextFieldStyleScope
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The style setter `paintOutsets` leaves the component with what it writes, and removing it restores the value the
 * component held before, which the harness's restore check covers.
 */
class PaintOutsetsStyleSetterTest {
    enum class Declared { None, Style }

    enum class Forwarder(
        val content: @Composable (Declared) -> Unit,
    ) {
        PaintOutsetsStyle({ declared ->
            LinedField(
                "target",
                style =
                    if (declared == Declared.Style) {
                        SwingStyle<TextFieldStyleScope> { paintOutsets(PaintOutsets.FullInsets) }
                    } else {
                        SwingStyle
                    },
            )
        }),
    }

    @ParameterizedTest
    @EnumSource(Forwarder::class)
    fun paintOutsetsLeavesWhatTheStyleSetterWritesAndRemovingItRestoresTheOriginal(forwarder: Forwarder) =
        runComposeSwingTest {
            var declared by mutableStateOf(Declared.None)
            setContent {
                Column { forwarder.content(declared) }
            }
            onNodeWithTag("target").assertProperty(0) { layoutBounds.x }

            declared = Declared.Style
            awaitIdle()
            onNodeWithTag("target")
                .assertProperty(-3, "paintOutsets: the style setter writes the literal value") { layoutBounds.x }

            declared = Declared.None
            awaitIdle()
            onNodeWithTag("target").assertProperty(0) { layoutBounds.x }
        }
}
