// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import java.awt.Component

/**
 * One setter under test. The [host] composes the component and declares the setter with the value it is handed
 * while that value is not null; [node] finds the component the setter writes to.
 */
internal class SetterCase(
    val name: String,
    val first: Any,
    val second: Any,
    val node: ComposeSwingTest.() -> SwingNodeInteraction<Component>,
    val read: (Component) -> Any?,
    val host: @Composable (value: () -> Any?) -> Unit,
)

/**
 * One test per case, each mounting twice: once with [SetterCase.first] declared from the start, and once declaring
 * it after the component is attached. The two resolve through different paths. [around] wraps both mounts.
 */
internal fun setterCaseTests(
    cases: List<SetterCase>,
    around: (body: () -> Unit) -> Unit = { it() },
): List<DynamicTest> = cases.map { case ->
    dynamicTest(case.name) {
        around {
            runComposeSwingTest { assertWritesWhenMountedDeclared(case) }
            runComposeSwingTest { assertWritesRewritesAndRestores(case) }
        }
    }
}

private fun ComposeSwingTest.assertWritesWhenMountedDeclared(case: SetterCase) {
    setContent { case.host { case.first } }
    case.node(this).assertProperty(case.first, "${case.name}: the style writes the value it is mounted with") {
        case.read(this)
    }
}

private suspend fun ComposeSwingTest.assertWritesRewritesAndRestores(case: SetterCase) {
    var value: Any? by mutableStateOf(null)
    val current = { value }
    setContent { case.host(current) }
    val node = case.node(this)
    var original: Any? = null
    node.assertProperty(Unit) { original = case.read(this) }

    value = case.first
    awaitIdle()
    node.assertProperty(case.first, "${case.name}: the style writes its value") { case.read(this) }

    value = case.second
    awaitIdle()
    node.assertProperty(case.second, "${case.name}: a changed value is written") { case.read(this) }

    value = null
    awaitIdle()
    node.assertProperty(original, "${case.name}: withdrawal restores the original value") { case.read(this) }
}
