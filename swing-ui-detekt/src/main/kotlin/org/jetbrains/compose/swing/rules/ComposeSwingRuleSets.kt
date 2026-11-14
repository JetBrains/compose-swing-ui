// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

/** Every rule this library carries, by the id of the rule set that holds it. */
public object ComposeSwingRuleSets {
    /** The rule sets: `compose-swing`, `compose-swing-internal` and `compose-runtime`. */
    public val ruleSets: Map<String, List<ComposeSwingRule>> =
        mapOf(
            "compose-swing" to
                listOf(
                    ComposableSwingModifierFactory,
                    ModifierNodeInspectableProperties,
                    MultipleSwingModifierParameters,
                    MultipleSwingNodeCalls,
                    SwingComponentInComposableSignature,
                    SwingModifierFactoryExtensionFunction,
                    SwingModifierFactoryReturnType,
                    SwingModifierThen,
                    SwingModifierUnreferencedReceiver,
                    SwingModifierWithoutDefault,
                    SwingNodeFactoryReadsDeclaredValue,
                    SwingNodeFactoryReusesComponent,
                    SwingNodeUpdateConditionalCall,
                    SwingNodeUpdateRepeatsInvalidation,
                    SwingNodeUpdateStoresCallback,
                    SwingNodeUpdateWritesComponentPart,
                    SwingStyleWithoutDefault,
                    UnheldColumnComparator,
                    UnusedAnimatedContentTargetState,
                    UnusedTransitionTargetState,
                ),
            "compose-swing-internal" to listOf(PreferImportsOverFqn, PublicDataClass),
            "compose-runtime" to emptyList(),
        )
}
