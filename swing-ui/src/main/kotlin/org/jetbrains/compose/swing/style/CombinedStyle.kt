@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.util.fastForEach

/**
 * Combines this style with [other]. [other] runs after this style, so where both set a property, [other]'s value
 * wins.
 *
 * Combining with the empty style returns the other style itself. Combined styles are equal when they run equal styles
 * in the same order, so a combination built again on recomposition does not restyle the component.
 *
 * @param other the style that runs after this one.
 * @return the style that runs this style and then [other].
 */
public infix fun <S : SwingStyleScope> SwingStyle<S>.then(other: SwingStyle<S>): SwingStyle<S> = SwingStyle(this, other)

/**
 * Combines [styles] into one style that runs them in order, so where several set a property, the last one's value
 * wins. As [then], empty styles are skipped and combinations with equal styles in the same order are equal.
 *
 * @param styles the styles to run, in order.
 * @return the combined style; the empty style when [styles] holds no other style.
 */
public fun <S : SwingStyleScope> SwingStyle(vararg styles: SwingStyle<S>): SwingStyle<S> {
    val parts = ArrayList<SwingStyle<S>>(styles.size)
    styles.asList().fastForEach { if (it !== SwingStyle) parts += it }
    return when (parts.size) {
        0 -> SwingStyle
        1 -> parts[0]
        else -> CombinedStyle(parts)
    }
}

/**
 * Runs [style] at this point of the enclosing style, as if its block were written here: a property this style sets
 * after the call wins over [style]'s value, and [style]'s value wins over one set before the call.
 *
 * @param style the style to run.
 */
@Suppress("NOTHING_TO_INLINE") // Inline so a call adds no frame between the enclosing style and the one it runs.
public inline fun <S : SwingStyleScope> S.apply(style: SwingStyle<S>) {
    with(style) { this@apply.applyStyle() }
}

private class CombinedStyle<S : SwingStyleScope>(
    val styles: List<SwingStyle<S>>,
) : SwingStyle<S> {
    override fun S.applyStyle() {
        styles.fastForEach { apply(it) }
    }

    override fun equals(other: Any?): Boolean = other is CombinedStyle<*> && styles == other.styles

    override fun hashCode(): Int = styles.hashCode()
}
