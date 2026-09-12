package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConstrainedScopeTest {
    private val scope = object : ConstrainedScope {}

    @Test
    fun sizeAndFillExtensionsProduceExpectedModifierElements() {
        with(scope) {
            val elements = mutableListOf<SwingModifier.Element>()
            val modifier =
                SwingModifier
                    .fillMaxWidth(0.8f)
                    .fillMaxHeight(0.8f)
                    .fillMaxSize(0.8f)
                    .width(10)
                    .width(IntrinsicSize.Min)
                    .height(20)
                    .height(IntrinsicSize.Max)
                    .size(30)
                    .size(40, 50)
                    .widthIn(min = 5, max = 15)
                    .heightIn(min = 5, max = 15)
                    .sizeIn(minWidth = 5, minHeight = 10, maxWidth = 15, maxHeight = 20)
                    .requiredWidth(10)
                    .requiredWidth(IntrinsicSize.Min)
                    .requiredHeight(20)
                    .requiredHeight(IntrinsicSize.Max)
                    .requiredSize(30)
                    .requiredSize(40, 50)
                    .requiredWidthIn(min = 5, max = 15)
                    .requiredHeightIn(min = 5, max = 15)
                    .requiredSizeIn(minWidth = 5, minHeight = 10, maxWidth = 15, maxHeight = 20)

            modifier.foldIn(Unit) { _, element -> elements.add(element) }

            assertEquals(21, elements.size)
            assertIs<FillMaxElement>(elements[0])
            assertIs<FillMaxElement>(elements[1])
            assertIs<FillMaxElement>(elements[2])
            assertIs<SizeElement>(elements[3])
            assertIs<IntrinsicWidthElement>(elements[4])
            assertIs<SizeElement>(elements[5])
            assertIs<IntrinsicHeightElement>(elements[6])
            assertIs<SizeElement>(elements[7])
            assertIs<SizeElement>(elements[8])
            assertIs<SizeElement>(elements[9])
            assertIs<SizeElement>(elements[10])
            assertIs<SizeElement>(elements[11])
            assertIs<SizeElement>(elements[12])
            assertIs<IntrinsicWidthElement>(elements[13])
            assertIs<SizeElement>(elements[14])
            assertIs<IntrinsicHeightElement>(elements[15])
            assertIs<SizeElement>(elements[16])
            assertIs<SizeElement>(elements[17])
            assertIs<SizeElement>(elements[18])
            assertIs<SizeElement>(elements[19])
            assertIs<SizeElement>(elements[20])
        }
    }

    @Test
    fun wrapperPaddingAndOffsetExtensionsProduceExpectedModifierElements() {
        with(scope) {
            val elements = mutableListOf<SwingModifier.Element>()
            val modifier =
                SwingModifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .wrapContentSize()
                    .defaultMinSize(minWidth = 10, minHeight = 20)
                    .padding(4)
                    .padding(horizontal = 2, vertical = 4)
                    .padding(start = 1, top = 2, end = 3, bottom = 4)
                    .absolutePadding(left = 1, top = 2, right = 3, bottom = 4)
                    .offset(5, 6)
                    .absoluteOffset(7, 8)
                    .aspectRatio(1.5f, matchHeightConstraintsFirst = true)

            modifier.foldIn(Unit) { _, element -> elements.add(element) }

            assertEquals(11, elements.size)
            assertIs<WrapContentElement>(elements[0])
            assertIs<WrapContentElement>(elements[1])
            assertIs<WrapContentElement>(elements[2])
            assertIs<DefaultMinSizeElement>(elements[3])
            assertIs<PaddingElement>(elements[4])
            assertIs<PaddingElement>(elements[5])
            assertIs<PaddingElement>(elements[6])
            assertIs<AbsolutePaddingElement>(elements[7])
            assertIs<OffsetElement>(elements[8])
            assertIs<AbsoluteOffsetElement>(elements[9])
            assertIs<AspectRatioElement>(elements[10])
        }
    }

    @Test
    fun scopeExtensionsWithDefaultArgumentsProduceElements() {
        with(scope) {
            val elements = mutableListOf<SwingModifier.Element>()
            val modifier =
                SwingModifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .fillMaxSize()
                    .widthIn()
                    .heightIn()
                    .sizeIn()
                    .requiredWidthIn()
                    .requiredHeightIn()
                    .requiredSizeIn()
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .wrapContentSize()
                    .defaultMinSize()
                    .padding(horizontal = 4)
                    .padding(vertical = 4)
                    .padding(start = 1, top = 2)
                    .absolutePadding(left = 1, top = 2)
                    .offset(x = 5)
                    .offset(y = 6)
                    .absoluteOffset(x = 5)
                    .absoluteOffset(y = 6)
                    .aspectRatio(1.5f)

            modifier.foldIn(Unit) { _, element -> elements.add(element) }
            assertEquals(22, elements.size)
        }
    }
}
