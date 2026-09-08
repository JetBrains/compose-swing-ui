/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.spring
import java.awt.Dimension

// Derived from androidx.compose.animation.ContentTransform, SizeTransform and togetherWith, with
// java.awt.Dimension standing in for IntSize.

/**
 * How one content of an [AnimatedContent] replaces another: the target content's enter, the initial
 * content's exit, their paint order, and how the container's size animates.
 *
 * [togetherWith] builds one from an [EnterTransition] and an [ExitTransition], and
 * [AnimatedContentTransitionScope.using] replaces its [sizeTransform]:
 *
 * ```
 * AnimatedContent(
 *     targetState = page,
 *     transitionSpec = { fadeIn() togetherWith fadeOut() using null },
 * ) { Page(it) }
 * ```
 *
 * @property targetContentEnter how the content of the target state appears.
 * @property initialContentExit how the content of the state being left disappears.
 * @param targetContentZIndex the paint order of the target content; `0f` by default.
 * @param sizeTransform how the container's size animates, or `null` for a container that takes the room
 *     both contents need and animates nothing. `SizeTransform()` by default.
 */
public class ContentTransform(
    public val targetContentEnter: EnterTransition,
    public val initialContentExit: ExitTransition,
    targetContentZIndex: Float = 0f,
    sizeTransform: SizeTransform? = SizeTransform(),
) {
    /**
     * The paint order of the target content. Content with a higher value paints over content with a
     * lower one; equal values keep composition order, which leaves the target content on top.
     *
     * It is snapshot state, so a value written after the transform was built reaches the container.
     */
    public var targetContentZIndex: Float by mutableFloatStateOf(targetContentZIndex)

    /**
     * How the container's size animates, or `null` for a container that takes the room its content needs.
     * Set with [AnimatedContentTransitionScope.using].
     */
    public var sizeTransform: SizeTransform? = sizeTransform
        internal set
}

/**
 * Combines this enter transition with [exit] into a [ContentTransform], as an [AnimatedContent]
 * `transitionSpec` returns.
 *
 * @param exit how the content being left disappears.
 * @return the content transform.
 */
public infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform = ContentTransform(this, exit)

/**
 * Builds a [SizeTransform] with the provided [clip] and [sizeAnimationSpec].
 *
 * @param clip whether to clip content to the animated size; true by default.
 * @param sizeAnimationSpec the size animation spec, given the container's initial size and the target
 *     content's size; a medium-low stiffness spring by default.
 * @return the size transform.
 */
public fun SizeTransform(
    clip: Boolean = true,
    sizeAnimationSpec: (initialSize: Dimension, targetSize: Dimension) -> FiniteAnimationSpec<Dimension> =
        { _, _ ->
            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = dimensionVisibilityThreshold())
        },
): SizeTransform =
    object : SizeTransform {
        override val clip: Boolean = clip

        override fun createAnimationSpec(
            initialSize: Dimension,
            targetSize: Dimension,
        ): FiniteAnimationSpec<Dimension> = sizeAnimationSpec(initialSize, targetSize)
    }

/**
 * How an [AnimatedContent] container's size animates from the initial content's size to the target
 * content's size.
 *
 * The animation clips content outside its animated size when [clip] is true. When false, content may
 * paint beyond that size, subject to clipping by its Swing ancestors.
 */
public interface SizeTransform {
    /** Whether content is clipped to the animated size. */
    public val clip: Boolean

    /**
     * Creates the size animation spec.
     *
     * @param initialSize the container's size when the transition starts.
     * @param targetSize the target content's size.
     * @return the spec the size animates under.
     */
    public fun createAnimationSpec(
        initialSize: Dimension,
        targetSize: Dimension,
    ): FiniteAnimationSpec<Dimension>
}
