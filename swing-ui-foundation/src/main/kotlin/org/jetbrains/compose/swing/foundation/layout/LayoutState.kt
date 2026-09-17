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
 * Adapted from androidx.compose.ui.node.LayoutNodeLayoutDelegate in AndroidX's ui; see this
 * module's META-INF/NOTICE for the synced version. The measured-twice and measured-by-parent
 * error strings are upstream's verbatim, and trackMeasurementByParent mirrors
 * MeasurePassDelegate's check-then-record logic.
 */

package org.jetbrains.compose.swing.foundation.layout

/** Which block of its policy a container is running. */
internal enum class LayoutState {
    Idle,
    Measuring,
    LayingOut,
}

/**
 * Starts running the block [state] names over the children of the last layout pass, and answers the state to set
 * [layoutState] back to. A measure block starts a pass; a placement block may measure and place again a child the
 * previous placement run measured and placed.
 */
internal fun ChildMeasurables.enter(state: LayoutState): LayoutState {
    for (index in layoutPass.indices) {
        val child = layoutPass[index]
        if (state == LayoutState.Measuring || child.lineReadDuring == LayoutState.LayingOut) {
            child.lineReadDuring = LayoutState.Idle
        }
        if (state == LayoutState.Measuring || child.measuredByParent == LayoutState.LayingOut) {
            child.measuredByParent = LayoutState.Idle
        }
    }
    if (state == LayoutState.LayingOut) placementRun++
    val previous = layoutState
    layoutState = state
    return previous
}

/**
 * Records this child as measured by the block its container is running, and fails where the pass already measured
 * it or where the container runs neither block.
 */
internal fun ChildMeasurable.trackMeasurementByParent() {
    check(measuredByParent == LayoutState.Idle) {
        "measure() may not be called multiple times on the same Measurable. If you want to " +
            "get the content size of the Measurable before calculating the final constraints, " +
            "please use methods like minIntrinsicWidth()/maxIntrinsicWidth() and " +
            "minIntrinsicHeight()/maxIntrinsicHeight()"
    }
    val state = owner.layoutState
    check(state != LayoutState.Idle) {
        "Measurable could be only measured from the parent's measure or layout block. Parents state is $state"
    }
    measuredByParent = state
}
