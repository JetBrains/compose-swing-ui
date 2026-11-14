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

class RememberInCompositionGetterTest {
    @Test
    fun notRemembered_getterInheritanceHierarchy() {
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(2, findings.size)
        val expectedLines =
            listOf(
                12,
                13,
            )
        findings.zip(expectedLines).forEach { (finding, line) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        }
    }

    @Test
    fun composableGettersRespectNestedBoundaries() {
        val source =
            RememberInCompositionFixtures.composableGetters("androidx.compose.runtime.foo") +
                "\n\n" + RememberInCompositionFixtures.source
        val findings = RuntimeRuleLint.lint("RememberInComposition", source)
        assertEquals(2, findings.size)
        findings.zip(listOf(9, 10)).forEach { (finding, line) ->
            assertEquals(line, source.substring(0, finding.entity.location.text.start).count { it == '\n' } + 1)
        }
    }
}

private val source =
    """
    package androidx.compose.runtime.foo

    import androidx.compose.runtime.*

    @Composable
    fun Test(
        topLevel: TopLevelWithoutAnnotation,
        innerLevel: InnerLevelWithAnnotation,
        lowerLevel: LowerLevelWithoutAnnotation
    ) {
        topLevel.value
        innerLevel.value
        lowerLevel.value
    }
    """.trimIndent() + "\n\n" + RememberInCompositionFixtures.source
