/*
 * Copyright 2025 The Android Open Source Project
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

package org.jetbrains.compose.swing.detekt

internal object RememberInCompositionFixtures {
    val source =
        """
        @androidx.compose.runtime.annotation.RememberInComposition
        fun test(): Int = 5
        class Foo @androidx.compose.runtime.annotation.RememberInComposition constructor() {
            @get:androidx.compose.runtime.annotation.RememberInComposition var value: Int = 5
        }
        interface Bar {
            @androidx.compose.runtime.annotation.RememberInComposition fun calculateValue(): Int
            @get:androidx.compose.runtime.annotation.RememberInComposition var value: Int
        }
        class BarImpl : Bar {
            override fun calculateValue(): Int = 5
            override var value = 5
        }
        interface TopLevelWithoutAnnotation { var value: Int }
        abstract class InnerLevelWithAnnotation : TopLevelWithoutAnnotation {
            @get:androidx.compose.runtime.annotation.RememberInComposition override var value: Int = 5
        }
        class LowerLevelWithoutAnnotation : InnerLevelWithAnnotation() { override var value: Int = 5 }
        """.trimIndent()

    fun composableGetters(packageName: String): String =
        """
        package $packageName

        import androidx.compose.runtime.*

        fun deferred(block: () -> Unit) {}

        val composed: Int
            @Composable get() {
                test()
                run { test() }
                remember { test() }
                deferred { test() }
                fun local() { test() }
                return 0
            }

        val ordinary: Int
            get() {
                test()
                run { test() }
                return 0
            }
        """.trimIndent()
}
