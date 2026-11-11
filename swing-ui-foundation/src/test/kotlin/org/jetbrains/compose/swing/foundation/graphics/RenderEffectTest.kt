/*
 * Copyright 2021 The Android Open Source Project
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
 * Adapted from androidx.compose.ui.graphics.RenderEffectTest in AndroidX's
 * ui-graphics; see this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.graphics

import kotlin.test.Test
import kotlin.test.assertEquals

class RenderEffectTest {
    @Test
    fun testBlurEffectEquality() {
        val blur1 = BlurEffect(5f, 10f, TileMode.Clamp)
        val blur2 = BlurEffect(5f, 10f, TileMode.Clamp)
        assertEquals(blur1, blur2)
    }

    @Test
    fun testBlurEffectHashcode() {
        val blur1 = BlurEffect(5f, 10f, TileMode.Clamp)
        val blur2 = BlurEffect(5f, 10f, TileMode.Clamp)
        assertEquals(blur1.hashCode(), blur2.hashCode())
    }

    @Test
    fun testBlurEffectToString() {
        assertEquals(
            "BlurEffect(radiusX=5.0, radiusY=10.0, edgeTreatment=Clamp)",
            BlurEffect(5f, 10.0f, TileMode.Clamp).toString(),
        )
    }
}
