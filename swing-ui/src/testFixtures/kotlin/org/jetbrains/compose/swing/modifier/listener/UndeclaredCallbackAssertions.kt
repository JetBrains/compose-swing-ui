// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Asserts that [declare] throws [IllegalArgumentException] when a modifier builder is called without declaring any
 * callback.
 * The [builder] name appears in the failure message so a broken builder is easy to identify.
 */
fun assertRefused(
    builder: String,
    declare: () -> SwingModifier,
) {
    assertFailsWith<IllegalArgumentException>("$builder should refuse a call that declares no callback") {
        declare()
    }
}

/**
 * Asserts that [declare] returns a modifier chain that differs from [SwingModifier], meaning the builder added an
 * element.
 * The [builder] name appears in the failure message so a builder that adds nothing is easy to identify.
 */
fun assertDeclares(
    builder: String,
    declare: () -> SwingModifier,
) {
    assertNotEquals(SwingModifier, declare(), "$builder should add an element to the chain")
}
