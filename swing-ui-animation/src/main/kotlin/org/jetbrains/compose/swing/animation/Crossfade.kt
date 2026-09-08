/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.PlacementLayerScope
import org.jetbrains.compose.swing.modifier.SwingModifier

// Derived from androidx.compose.animation.Crossfade: the list of currently visible states, the content map keyed by
// contentKey and one faded box per content are upstream's.

/**
 * A container that fades between the contents of its states. When [targetState] changes, the new content
 * fades in and the old content fades out, both under [animationSpec].
 *
 * The container is a `Box` holding one box per content on screen, so it takes the room both contents need
 * while both are on screen, then the room the new content needs. It does not animate its size.
 *
 * ```
 * Crossfade(targetState = page) { shown ->
 *     when (shown) {
 *         Page.Summary -> Summary()
 *         Page.Details -> Details()
 *     }
 * }
 * ```
 *
 * The content lambda must show the state it is passed, never a state read elsewhere: the container
 * composes it once per state on screen, and the old content must keep showing its state while it fades
 * out.
 *
 * Each content fades as one image. Text loses LCD subpixel antialiasing while a fade runs, and a
 * heavyweight child stays at full opacity; see [AnimatedContent].
 *
 * @param targetState the state whose content the container settles on.
 * @param modifier the [SwingModifier] applied to the container.
 * @param animationSpec the spec both the fade in and the fade out run under.
 * @param label names the transition in a tool that inspects a composition. It is read once, when the
 *     transition is created.
 * @param content the composable content of one state of the container.
 */
@Composable
public fun <T> Crossfade(
    targetState: T,
    modifier: SwingModifier = SwingModifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    label: String = "Crossfade",
    content: @Composable (T) -> Unit,
) {
    val transition = updateTransition(targetState, label)
    transition.Crossfade(modifier, animationSpec, content = content)
}

/**
 * A container that fades between the contents of this transition's states. Containers built on one
 * transition animate together.
 *
 * ```
 * val transition = updateTransition(page)
 * transition.Crossfade { Body(it) }
 * transition.Crossfade { Footer(it) }
 * ```
 *
 * Otherwise behaves as the overload taking a target state.
 *
 * @param modifier the [SwingModifier] applied to the container.
 * @param animationSpec the spec both the fade in and the fade out run under.
 * @param contentKey states with equal keys share one composition and component, so a change between them
 *     fades nothing. The state itself by default.
 * @param content the composable content of one state of the container.
 */
@ExperimentalAnimationApi
@Composable
public fun <T> Transition<T>.Crossfade(
    modifier: SwingModifier = SwingModifier,
    animationSpec: FiniteAnimationSpec<Float> = tween(),
    contentKey: (targetState: T) -> Any? = { it },
    content: @Composable (targetState: T) -> Unit,
) {
    val currentlyVisible = remember { mutableStateListOf<T>().apply { add(currentState) } }
    val contentMap = remember { mutableScatterMapOf<T, @Composable () -> Unit>() }
    if (currentState == targetState) {
        // If not animating, just display the current state
        if (currentlyVisible.size != 1 || currentlyVisible[0] != targetState) {
            // Remove all the intermediate items from the list once the animation is finished.
            currentlyVisible.removeAll { it != targetState }
            contentMap.clear()
        }
    }
    if (targetState !in contentMap) {
        // Replace target with the same key if any
        val replacementId = currentlyVisible.indexOfFirst { contentKey(it) == contentKey(targetState) }
        if (replacementId == -1) {
            currentlyVisible.add(targetState)
        } else {
            currentlyVisible[replacementId] = targetState
        }
        contentMap.clear()
        for (index in currentlyVisible.indices) {
            val stateForContent = currentlyVisible[index]
            contentMap[stateForContent] = {
                val alpha =
                    animateFloat(transitionSpec = { animationSpec }) { if (it == stateForContent) 1f else 0f }
                Box(SwingModifier.then(remember(alpha) { AlphaLayerElement(alpha) })) { content(stateForContent) }
            }
        }
    }

    Box(modifier) {
        for (index in currentlyVisible.indices) {
            val state = currentlyVisible[index]
            key(contentKey(state)) { contentMap[state]?.invoke() }
        }
    }
}

/** Upstream's `graphicsLayer { this.alpha = alpha }`: places the content with a layer reading [alpha] at paint. */
private class AlphaLayerElement(
    val alpha: State<Float>,
) : LayoutModifierNodeElement<AlphaLayerNode>() {
    override fun create(): AlphaLayerNode = AlphaLayerNode(alpha)

    override fun update(node: AlphaLayerNode) {
        node.alpha = alpha
    }

    override fun equals(other: Any?): Boolean = other is AlphaLayerElement && other.alpha === alpha

    override fun hashCode(): Int = System.identityHashCode(alpha)
}

private class AlphaLayerNode(
    var alpha: State<Float>,
) : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "graphicsLayer"

    // One instance, so a placement with an unchanged layer repaints nothing.
    private val layerBlock: PlacementLayerScope.() -> Unit = { alpha = this@AlphaLayerNode.alpha.value }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = layerBlock) }
    }
}
