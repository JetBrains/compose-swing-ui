package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.SwingMatcher
import java.awt.Window

/**
 * A lazy handle to the set of realized windows matched by a window query. The match set is resolved
 * against the live set of realized windows each time it is needed, so it reflects windows appearing and
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

    /** Asserts that exactly [expected] realized windows match. */
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
     * Resolves every matching window and returns them typed as [T], in creation order, for reading
     * or driving each window's own API.
     *
     * @throws AssertionError if any matched window is not a [T].
     */
    public inline fun <reified T : Window> fetchAll(): List<T> =
        resolveAll().map { window -> window.castOrFail<T>("Window", matcherDescription) }
}
