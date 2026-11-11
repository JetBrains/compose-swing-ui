// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.SwingMatcher
import java.awt.Window

/**
 * A lazy handle to the set of realized windows matched by a window query. The match set is resolved
 * against the current set of realized windows each time it is needed, so it reflects windows appearing and
 * disappearing across recomposition.
 *
 * All methods are intended to be called from a [org.jetbrains.compose.swing.test.runComposeSwingTest]
 * body, which runs on the EDT.
 */
public class SwingWindowInteractionCollection internal constructor(
    private val matcher: SwingMatcher,
) {
    @PublishedApi
    internal fun resolveAll(): List<Window> = realizedWindows().filter(matcher::matches)

    /** Human-readable description of this collection's query, for [fetchAll] failure messages. */
    @PublishedApi
    internal val matcherDescription: String
        get() = matcher.description

    /**
     * Asserts that exactly [expected] realized windows match.
     *
     * @param expected the number of matches required; the failure lists every realized window.
     * @return this collection, for chaining a further assertion.
     */
    public fun assertCountEquals(expected: Int): SwingWindowInteractionCollection {
        val actual = resolveAll().size
        if (actual != expected) {
            throw AssertionError(
                "Expected $expected realized windows matching '${matcher.description}' " +
                    "but found $actual.\n${realizedWindowsSummary()}",
            )
        }
        return this
    }

    /** Returns the number of currently matching realized windows. */
    public fun fetchSize(): Int = resolveAll().size

    /**
     * Resolves every matching window and returns them typed as [T], in creation order. This is the
     * last resort, for a call that requires the matched windows themselves or a subject that is the whole set. To
     * read a value of one window, use [SwingWindowInteraction.assertProperty] or an assertion.
     *
     * @throws AssertionError if any matched window is not a [T].
     */
    public inline fun <reified T : Window> fetchAll(): List<T> =
        resolveAll().map { window -> window.castOrFail<T>("Window", matcherDescription) }
}
