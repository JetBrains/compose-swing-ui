// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider
import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets

/**
 * The rules this library contributes to detekt.
 *
 * Modifiers and styles: how a `SwingModifier` is declared, taken and passed on.
 * Whether a `SwingStyle` parameter defaults to the empty style.
 * What a declaration hands a component that adopts it by identity.
 *
 * Signatures: whether a composable's signature hands a Swing component around.
 *
 * Node factories: whether a node factory reuses one component for every node.
 * Whether a node factory reads a value its composable declares.
 *
 * Node calls: whether a composable calls `SwingNode` more than once.
 *
 * Node updates: whether a node update writes a part of a component that source code assembles.
 * Whether a node update repeats a layout or repaint request that its property writes already make.
 * Whether a node update stores a callback on a component that listens to itself.
 * Whether a node update makes a positional updater call that can be skipped during composition.
 *
 * Animation: whether animation lambdas use their target state.
 */
public class ComposeSwingRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-swing")

    override fun instance(): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules = adaptedRules(ComposeSwingRuleSets.ruleSets.getValue(ruleSetId.value)),
        )
}
