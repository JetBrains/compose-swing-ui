// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import dev.detekt.api.Config
import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * The rules this library contributes to detekt: how a `SwingModifier` is declared, taken and passed on,
 * whether a `SwingStyle` parameter defaults to the empty style, what a declaration hands a component that
 * adopts it by identity, and whether animation lambdas use their target state.
 */
public class ComposeSwingRuleSetProvider : RuleSetProvider {
    override val ruleSetId: RuleSetId = RuleSetId("compose-swing")

    override fun instance(): RuleSet =
        RuleSet(
            id = ruleSetId,
            rules =
                mapOf(
                    RuleName("ComposableSwingModifierFactory") to { config: Config ->
                        ComposableSwingModifierFactory(config)
                    },
                    RuleName("ModifierNodeInspectableProperties") to { config: Config ->
                        ModifierNodeInspectableProperties(config)
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
                ),
        )
}
