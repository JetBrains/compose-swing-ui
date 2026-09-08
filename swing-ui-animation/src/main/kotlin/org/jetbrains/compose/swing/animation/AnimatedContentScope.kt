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

// Derived from androidx.compose.animation.AnimatedContentScope.

/**
 * The receiver of an [AnimatedContent]'s content.
 *
 * It adds no members to [AnimatedVisibilityScope]. It exists so that a helper declared as
 * `AnimatedContentScope.() -> Unit` cannot be passed to an [AnimatedVisibility].
 */
public sealed interface AnimatedContentScope : AnimatedVisibilityScope

/**
 * The [AnimatedContentScope] one content of an [AnimatedContent] is composed under.
 *
 * @param scope the scope of the animated container holding that content, which every member delegates to.
 */
internal class AnimatedContentScopeImpl(
    scope: AnimatedVisibilityScope,
) : AnimatedContentScope,
    AnimatedVisibilityScope by scope
