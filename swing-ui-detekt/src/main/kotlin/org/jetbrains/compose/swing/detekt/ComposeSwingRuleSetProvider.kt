// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

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
 *
 * Animation: whether animation lambdas use their target state.
 */
public class ComposeSwingRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-swing")

    private val rules: Map<RuleName, (Config) -> Rule> =
        mapOf(
            RuleName("ComposableSwingModifierFactory") to { config: Config ->
                ComposableSwingModifierFactory(config)
            },
            RuleName("ModifierNodeInspectableProperties") to { config: Config ->
                ModifierNodeInspectableProperties(config)
            },
            RuleName("MultipleSwingModifierParameters") to { config: Config ->
                MultipleSwingModifierParameters(config)
            },
            RuleName("MultipleSwingNodeCalls") to { config: Config ->
                MultipleSwingNodeCalls(config)
            },
            RuleName("SwingComponentInComposableSignature") to { config: Config ->
                SwingComponentInComposableSignature(config)
            },
            RuleName("SwingModifierFactoryExtensionFunction") to { config: Config ->
                SwingModifierFactoryExtensionFunction(config)
            },
            RuleName("SwingModifierFactoryReturnType") to { config: Config ->
                SwingModifierFactoryReturnType(config)
            },
            RuleName("SwingModifierThen") to { config: Config ->
                SwingModifierThen(config)
            },
            RuleName("SwingModifierUnreferencedReceiver") to { config: Config ->
                SwingModifierUnreferencedReceiver(config)
            },
            RuleName("SwingModifierWithoutDefault") to { config: Config ->
                SwingModifierWithoutDefault(config)
            },
            RuleName("SwingNodeFactoryReadsDeclaredValue") to { config: Config ->
                SwingNodeFactoryReadsDeclaredValue(config)
            },
            RuleName("SwingNodeFactoryReusesComponent") to { config: Config ->
                SwingNodeFactoryReusesComponent(config)
            },
            RuleName("SwingNodeUpdateWritesComponentPart") to { config: Config ->
                SwingNodeUpdateWritesComponentPart(config)
            },
            RuleName("SwingStyleWithoutDefault") to { config: Config ->
                SwingStyleWithoutDefault(config)
            },
            RuleName("UnheldColumnComparator") to { config: Config ->
                UnheldColumnComparator(config)
            },
            RuleName("UnusedAnimatedContentTargetState") to { config: Config ->
                UnusedAnimatedContentTargetState(config)
            },
            RuleName("UnusedTransitionTargetState") to { config: Config ->
                UnusedTransitionTargetState(config)
            },
        )

    override fun instance(): RuleSet = RuleSet(id = ruleSetId, rules = rules)
}
