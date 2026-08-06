package org.jetbrains.compose.swing.text

/**
 * A range of characters in a text document, expressed as a directional anchor([start])/caret([end])
 * pair of offsets.
 *
 * [start] is the fixed anchor of a selection and [end] is the moving caret, so a range where
 * `start != end` is directional: `TextRange(2, 5)` anchors at 2 with the caret at 5, while
 * `TextRange(5, 2)` anchors at 5 with the caret at 2. A range where [start] equals [end] is a
 * collapsed caret at that offset with no selected text.
 */
@JvmInline
public value class TextRange private constructor(
    private val packedValue: Long,
) {
    public constructor(start: Int, end: Int) : this(packRange(start, end))

    /** The anchor offset of this range. */
    public val start: Int get() = (packedValue shr Int.SIZE_BITS).toInt()

    /** The caret offset of this range. */
    public val end: Int get() = (packedValue and LOW_MASK).toInt()

    override fun toString(): String = "TextRange($start, $end)"
}

private const val LOW_MASK = 0xFFFF_FFFFL

private fun packRange(
    start: Int,
    end: Int,
): Long = (start.toLong() shl Int.SIZE_BITS) or (end.toLong() and LOW_MASK)
