// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A callback a composable declares reaches a host or model channel through one holder that outlives the
 * pass declaring it, and what it reports is the callback the latest pass declared.
 */
class RememberDeclaredCallbackTest {
    /** Equal to every other instance, so a holder that kept the first one by equality would never change. */
    private class EqualCallback(
        val tag: String,
        val calls: MutableList<String>,
    ) : () -> Unit {
        override fun invoke() {
            calls += tag
        }

        override fun equals(other: Any?): Boolean = other is EqualCallback

        override fun hashCode(): Int = 0
    }

    @Test
    fun theHolderReportsTheLatestCallbackAfterARecompositionWithAnEqualLookingOne() = runComposeSwingTest {
        val calls = mutableListOf<String>()
        var tag by mutableStateOf("first")
        val seen = mutableListOf<DeclaredCallback<() -> Unit>>()
        setContent { seen += rememberDeclaredCallback<() -> Unit>(EqualCallback(tag, calls)) }
        val holder = seen.first()
        holder.report { it() }

        tag = "second"
        awaitIdle()
        holder.report { it() }

        assertTrue(seen.size > 1, "the content recomposed")
        assertEquals(1, seen.distinct().size, "the holder is the same object across recompositions")
        assertEquals(listOf("first", "second"), calls, "each report calls the callback the latest pass declared")
    }

    @Test
    fun aThrowOutOfTheCallbackIsReportedToTheUncaughtHandler() = runComposeSwingTest {
        val failure = IllegalStateException("callback")
        lateinit var holder: DeclaredCallback<() -> Unit>
        setContent { holder = rememberDeclaredCallback { throw failure } }

        holder.report { it() }

        assertSame(failure, assertFailsWith<IllegalStateException> { awaitIdle() })
    }

    @Test
    fun anAbsentCallbackIsYieldedAsNull() = runComposeSwingTest {
        var declared: (() -> Unit)? by mutableStateOf(null)
        lateinit var holder: DeclaredCallback<(() -> Unit)?>
        setContent { holder = rememberDeclaredCallback(declared) }
        var reached = 0

        assertNull(holder.current, "nothing is declared")
        var reportedAbsent = false
        holder.report { reportedAbsent = it == null }
        awaitIdle()
        assertTrue(reportedAbsent, "the absent callback was reported as absent")

        declared = { reached++ }
        awaitIdle()
        holder.report { it?.invoke() }
        assertEquals(1, reached, "the declared callback then ran")
    }
}
