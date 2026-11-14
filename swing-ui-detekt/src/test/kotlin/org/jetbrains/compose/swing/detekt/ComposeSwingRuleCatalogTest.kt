// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import org.jetbrains.compose.swing.rules.ComposeSwingRule
import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeSwingRuleCatalogTest {
    private val rules = ComposeSwingRuleSets.ruleSets.flatMap { (setId, rules) -> rules.map { setId to it } }

    private val emitters = mapOf("contentEmitters" to "[]", "contentEmittersDenylist" to "[]")
    private val modifiers = mapOf("customModifiers" to "[]")
    private val lambdas = mapOf("treatAsLambda" to "[]", "treatAsComposableLambda" to "[]")

    private val upstreamSettings =
        mapOf(
            "ComposableAnnotationNaming" to emptyMap(),
            "ComposableNestingDepth" to emitters + ("composableNestingDepthThreshold" to "3"),
            "ComposableParamOrder" to modifiers + lambdas,
            "CompositionLocalAllowlist" to mapOf("allowedCompositionLocals" to "[]"),
            "ConditionHoist" to
                mapOf(
                    "contentEmitters" to "[]",
                    "ignoreCallsWithArgumentNames" to
                        "[\"modifier\", \"contentAlignment\", \"horizontalArrangement\", \"verticalAlignment\"]",
                ),
            "ContentEmitterReturningValues" to emitters,
            "ContentSlotReused" to lambdas,
            "ContentTrailingLambda" to lambdas,
            "DefaultsVisibility" to emptyMap(),
            "LambdaParameterEventTrailing" to modifiers + emitters,
            "LambdaParameterInRestartableEffect" to mapOf("treatAsLambda" to "[]"),
            "MissingNonRestartableComposable" to mapOf("ignoresPreviews" to "true"),
            "ModifierComposed" to modifiers,
            "ModifierMissing" to
                modifiers + emitters +
                mapOf(
                    "ignoreAnnotated" to "[]",
                    "modifierMissingIgnoreAnnotated" to "[]",
                    "checkModifiersForVisibility" to "'only_public'",
                ),
            "ModifierNaming" to modifiers,
            "ModifierNotUsedAtRoot" to modifiers + emitters,
            "ModifierReused" to modifiers + emitters,
            "ModifierShouldBeForwardedToEveryBranch" to modifiers,
            "MultipleEmitters" to emitters,
            "MutableParams" to emptyMap(),
            "MutableStateParam" to emptyMap(),
            "ParameterNaming" to mapOf("treatAsLambda" to "[]", "allowedLambdaParameterNames" to "[]"),
            "PreviewAnnotationNaming" to emptyMap(),
            "PreviewNaming" to mapOf("previewNamingStrategy" to "'suffix'"),
            "PreviewPublic" to emptyMap(),
            "RememberContentMissing" to emptyMap(),
            "StateParam" to emptyMap(),
            "UnnecessaryLaunchedEffect" to
                mapOf(
                    "allowedCallReceiverTypes" to "[]",
                    "allowedCallNames" to "[]",
                ),
            "UnstableCollections" to emptyMap(),
        )

    @Test
    fun `rule sets are the three the library declares`() {
        assertEquals(
            setOf("compose-runtime", "compose-swing", "compose-swing-internal"),
            ComposeSwingRuleSets.ruleSets.keys,
        )
    }

    @Test
    fun `rule ids are unique across rule sets`() {
        val ids = rules.map { (_, rule) -> rule.id }

        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `config declares every rule under its rule set with its default activity and severity`() {
        val config = configLines()
        rules.forEach { (setId, rule) ->
            val block = config.ruleBlock(setId, rule.id)
            assertEquals(rule.activeByDefault.toString(), block["active"], "active of $setId>${rule.id}")
            val severity =
                when (rule.severity) {
                    ComposeSwingRule.Severity.Error -> null
                    else -> rule.severity.name.lowercase()
                }
            assertEquals(severity, block["severity"], "severity of $setId>${rule.id}")
        }
    }

    @Test
    fun `adapted rules declare their upstream configuration keys and defaults`() {
        val config = configLines()
        upstreamSettings.forEach { (id, defaults) ->
            val (setId, _) = rules.single { (_, rule) -> rule.id == id }
            assertEquals(defaults, config.ruleBlock(setId, id) - setOf("active", "severity"), id)
        }
    }

    private fun configLines(): List<String> =
        assertNotNull(javaClass.getResource("/config/config.yml"), "config.yml").readText().lines()

    /** The `key: value` entries indented under rule [ruleId] of rule set [setId], read without a YAML parser. */
    private fun List<String>.ruleBlock(
        setId: String,
        ruleId: String,
    ): Map<String, String> {
        val setStart = indexOfFirst { it == "$setId:" }
        assertTrue(setStart >= 0, "config.yml has no rule set $setId")
        val setLines = drop(setStart + 1).takeWhile { it.isBlank() || it.startsWith(" ") || it.startsWith("#") }
        val ruleStart = setLines.indexOfFirst { it == "  $ruleId:" }
        assertTrue(ruleStart >= 0, "config.yml has no $ruleId under $setId")
        return setLines
            .drop(ruleStart + 1)
            .takeWhile { it.startsWith("    ") || it.isBlank() }
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line -> line.substringBefore(':') to line.substringAfter(':').trim() }
    }
}
