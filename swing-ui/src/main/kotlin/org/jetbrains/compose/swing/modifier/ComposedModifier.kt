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
 *
 * Adapted from compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/ComposedModifier.kt in AndroidX's
 * ui; see this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer

/**
 * Declares a modifier built by calling composables.
 * [factory] runs in the composition of each component the modifier reaches, before the modifier is applied,
 * and what it returns is applied in place of this entry, so a `remember` inside it keeps one value per
 * component.
 *
 * Not recommended: per-component state, coroutines and composition locals belong in a
 * [SwingModifier.ComponentNode] (see its node capabilities). Use [composed] only when the modifier can only
 * be built by calling composables. Each call creates an element unequal to the previous pass's, so the
 * composable taking it cannot skip.
 *
 * The counterpart of AndroidX `Modifier.composed`.
 */
public fun SwingModifier.composed(factory: @Composable SwingModifier.() -> SwingModifier): SwingModifier =
    this.then(ComposedModifier(factory))

internal class ComposedModifier(
    val factory: @Composable SwingModifier.() -> SwingModifier,
) : SwingModifier.Element

/**
 * Materialize any instance-specific [composed modifiers][composed] for applying to a raw tree node.
 * Call right before setting the returned modifier on an emitted node.
 */
@PublishedApi
internal fun Composer.materialize(modifier: SwingModifier): SwingModifier {
    // A group is required here so the number of slot added to the caller's group
    // is unconditionally the same (in this case, none) as is now required by the runtime.
    startReplaceGroup(MATERIALIZE_GROUP_KEY)
    val result = materializeImpl(modifier)
    endReplaceGroup()
    return result
}

private fun Composer.materializeImpl(modifier: SwingModifier): SwingModifier {
    if (modifier.foldIn(true) { all, element -> all && element !is ComposedModifier }) {
        return modifier
    }

    // This is a fake composable function that invokes the compose runtime directly so that it
    // can call the element factory functions from the non-@Composable lambda of SwingModifier.foldIn.

    startReplaceableGroup(MATERIALIZE_ELEMENTS_GROUP_KEY)

    val result =
        modifier.foldIn<SwingModifier>(SwingModifier) { acc, element ->
            acc.then(
                if (element is ComposedModifier) {
                    // A @Composable lambda compiles to one taking the composer and the changed flags.
                    @Suppress("UNCHECKED_CAST")
                    val factory = element.factory as SwingModifier.(Composer, Int) -> SwingModifier
                    val composedMod = factory(SwingModifier, this, 0)
                    materializeImpl(composedMod)
                } else {
                    element
                },
            )
        }

    endReplaceableGroup()
    return result
}

/**
 * The key of the group [materialize] runs in: `ComposedModifier::class.java.name.hashCode()`, baked in
 * because a group key must be a constant. It only has to stay fixed and differ from its sibling group
 * keys.
 */
private const val MATERIALIZE_GROUP_KEY: Int = 0x7fd9ceb2

/**
 * The key of the group [materializeImpl] folds the modifier in:
 * `"${ComposedModifier::class.java.name}.materializeImpl".hashCode()`, baked in for the same reason as
 * [MATERIALIZE_GROUP_KEY].
 */
private const val MATERIALIZE_ELEMENTS_GROUP_KEY: Int = -0x61180af
