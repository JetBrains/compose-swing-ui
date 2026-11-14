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

import kotlin.test.Test
import kotlin.test.assertEquals

class RetainRememberObserverTest {
    @Test
    fun testRetainObject_onlyRememberObserver() {
        val source =
            """
            package androidx.compose.runtime.foo

            import androidx.compose.runtime.RememberObserver
            import androidx.compose.runtime.retain.retain

            fun test() { val foo = retain { Foo() } }

            class Foo : RememberObserver {
                override fun onRemembered() {}
                override fun onForgotten() {}
                override fun onAbandoned() {}
            }
            """.trimIndent()
        val findings = RuntimeRuleLint.lint("RetainRememberObserver", source)
        assertEquals(1, findings.size)
        assertEquals(
            "Declared retained type `androidx.compose.runtime.foo.Foo` implements `RememberObserver` " +
                "but not `RetainObserver`.",
            findings.single().message,
        )
        val location =
            findings
                .single()
                .entity.location.text
        assertEquals("retain", source.substring(location.start, location.end))
    }

    @Test
    fun testRetainObject_neitherCallback() {
        val source =
            """
            import androidx.compose.runtime.retain.retain
            class Foo
            fun test() { retain { Foo() } }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("RetainRememberObserver", source).size)
    }

    @Test
    fun testRetainObject_rememberAndRetainObserver() {
        val source =
            """
            import androidx.compose.runtime.RememberObserver
            import androidx.compose.runtime.retain.RetainObserver
            import androidx.compose.runtime.retain.retain
            class Foo : RememberObserver, RetainObserver {
                override fun onRemembered() {}
                override fun onForgotten() {}
                override fun onAbandoned() {}
                override fun onRetained() {}
                override fun onEnteredComposition() {}
                override fun onExitedComposition() {}
                override fun onRetired() {}
            }
            fun test() { retain { Foo() } }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("RetainRememberObserver", source).size)
    }

    @Test
    fun testRetainObject_onlyRetainObserver() {
        val source =
            """
            import androidx.compose.runtime.retain.RetainObserver
            import androidx.compose.runtime.retain.retain
            class Foo : RetainObserver {
                override fun onAbandoned() {}
                override fun onRetained() {}
                override fun onEnteredComposition() {}
                override fun onExitedComposition() {}
                override fun onRetired() {}
            }
            fun test() { retain { Foo() } }
            """.trimIndent()
        assertEquals(0, RuntimeRuleLint.lint("RetainRememberObserver", source).size)
    }
}
