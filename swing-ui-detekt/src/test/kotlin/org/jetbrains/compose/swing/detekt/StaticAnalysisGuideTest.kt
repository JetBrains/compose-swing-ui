// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StaticAnalysisGuideTest {
    private val ruleSets =
        ComposeSwingRuleSets.ruleSets
            .filterKeys { it != "compose-swing-internal" }
            .map { (ruleSetId, rules) -> ruleSetId to rules.map { it.id } }
    private val guide =
        File(assertNotNull(System.getProperty("staticAnalysisGuide"), "staticAnalysisGuide")).readLines()

    @Test
    fun `guide lists every rule under its rule-set ID in catalog order`() {
        val ruleSetId = Regex("""Rule-set ID `(.+)`\.""")
        val sections = mutableListOf<Pair<String, MutableList<String>>>()
        guide.forEach { line ->
            ruleSetId.matchEntire(line)?.let { sections += it.groupValues[1] to mutableListOf() }
            if (line.startsWith("### ")) sections.last().second += line.removePrefix("### ")
        }

        assertEquals(ruleSets, sections, "guide rule-set ids, rule ids and their order differ from the catalog")
    }
}
