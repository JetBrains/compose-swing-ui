package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.ComposeSwingTest
import kotlin.test.assertFailsWith

/** Runs [block], expecting it to fail, and answers the messages of the failure and every cause. */
internal inline fun ComposeSwingTest.failureOf(block: ComposeSwingTest.() -> Unit): String {
    val failure = assertFailsWith<RuntimeException> { block() }
    return generateSequence<Throwable>(failure) { it.cause }.joinToString(" / ") { it.message.orEmpty() }
}
