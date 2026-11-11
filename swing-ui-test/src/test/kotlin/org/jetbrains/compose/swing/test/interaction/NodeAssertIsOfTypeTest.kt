// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.JButton
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Pins [assertIsOfType] for matching, mismatched, and absent nodes. */
class NodeAssertIsOfTypeTest {
    @Test
    fun aMatchingNodeIsReturnedTypedAsTheAssertedType() = runComposeSwingTest {
        setContent { Label(text = "caption") }

        // The root query names no type; after the assertion the properties are read off a JLabel.
        onRoot()
            .onChild()
            .assertIsOfType<JLabel>()
            .assertProperty("caption") { text }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingQueries")
    fun aMismatchOrAbsentNodeFails(query: ComposeSwingTest.() -> Unit) = runComposeSwingTest {
        setContent { Label(text = "caption") }

        assertFailsWith<AssertionError> { query() }
    }

    companion object {
        @JvmStatic
        fun failingQueries(): List<Named<ComposeSwingTest.() -> Unit>> = listOf(
            Named.of<ComposeSwingTest.() -> Unit>("a node of another type") {
                onNodeWithText("caption").assertIsOfType<JButton>()
            },
            Named.of<ComposeSwingTest.() -> Unit>("an absent node") {
                onNodeWithText("absent").assertIsOfType<JLabel>()
            },
        )
    }
}
