// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import io.nlopez.compose.rules.ComposableAnnotationNaming
import io.nlopez.compose.rules.ContentSlotReused
import io.nlopez.compose.rules.ContentTrailingLambda
import io.nlopez.compose.rules.MutableParameters
import io.nlopez.compose.rules.MutableStateParameter
import io.nlopez.compose.rules.ParameterNaming
import io.nlopez.compose.rules.RememberContentMissing
import io.nlopez.compose.rules.StateParameter
import io.nlopez.compose.rules.UnstableCollections
import org.jetbrains.compose.swing.rules.composerules.ConditionHoist
import org.jetbrains.compose.swing.rules.composerules.InvalidReadOnlyComposable
import org.jetbrains.compose.swing.rules.composerules.MissingNonRestartableComposable
import org.jetbrains.compose.swing.rules.composerules.MissingReadOnlyComposable
import org.jetbrains.compose.swing.rules.composerules.StaleRememberUpdatedStateInRemember
import org.jetbrains.compose.swing.rules.composerules.UnnecessaryComposable
import org.jetbrains.compose.swing.rules.composerules.UnnecessaryLaunchedEffect
import org.jetbrains.compose.swing.rules.composerules.VarsWithoutStateBacking

/** Every rule this library carries, by the id of the rule set that holds it. */
public object ComposeSwingRuleSets {
    /** The rule sets: `compose-swing`, `compose-swing-internal` and `compose-runtime`. */
    public val ruleSets: Map<String, List<ComposeSwingRule>> =
        mapOf(
            "compose-swing" to
                listOf(
                    ComposeRulesRule(
                        id = "ComposableAnnotationNaming",
                        description = "Composable annotation classes should be properly named",
                        visitor = ::ComposableAnnotationNaming,
                        findingMessage = COMPOSABLE_ANNOTATION_NAMING_MESSAGE,
                    ),
                    ComposeRulesRule(
                        id = "ComposableNestingDepth",
                        description = "Limit how deeply content emitters can be nested inside a @Composable",
                        configuredVisitor = ::ComposableNestingDepth,
                        activeByDefault = false,
                    ),
                    ParameterOrder,
                    ComposableSwingModifierFactory,
                    CompositionLocalAllowlist,
                    ConditionHoist,
                    ContentEmitterReturningValues,
                    ComposeRulesRule(
                        id = "ContentSlotReused",
                        description = "Content lambda parameters should not be invoked more than once",
                        visitor = ::ContentSlotReused,
                        findingMessage = CONTENT_SLOT_REUSED_MESSAGE,
                    ),
                    ComposeRulesRule(
                        id = "ContentTrailingLambda",
                        description = "Content lambda parameters should be placed at the end of parameter list",
                        visitor = ::ContentTrailingLambda,
                        findingMessage = CONTENT_TRAILING_LAMBDA_MESSAGE,
                    ),
                    ComposeRulesRule(
                        id = "DefaultsVisibility",
                        description = "Composable function defaults should have appropriate visibility",
                        configuredVisitor = { DefaultsVisibility() },
                    ),
                    InvalidReadOnlyComposable,
                    ComposeRulesRule(
                        id = "LambdaParameterEventTrailing",
                        description = "Event lambda parameters should be placed at the end of parameter list",
                        configuredVisitor = ::LambdaParameterEventTrailing,
                    ),
                    LambdaParameterInRestartableEffect,
                    MissingNonRestartableComposable,
                    MissingReadOnlyComposable,
                    ModifierComposed,
                    ModifierMissing,
                    ComposeRulesRule(
                        id = "ModifierNaming",
                        description = "Composable modifier parameters should use consistent names",
                        configuredVisitor = ::ModifierNaming,
                    ),
                    ModifierNodeInspectableProperties,
                    ComposeRulesRule(
                        id = "ModifierNotUsedAtRoot",
                        description = "Modifier parameters should be used at the root level composable",
                        configuredVisitor = ::ModifierNotUsedAtRoot,
                    ),
                    ComposeRulesRule(
                        id = "ModifierReused",
                        description = "Modifiers should not be reused",
                        configuredVisitor = ::ModifierReused,
                    ),
                    ModifierShouldBeForwardedToEveryBranch,
                    MultipleContentEmitters,
                    MultipleSwingModifierParameters,
                    MultipleSwingNodeCalls,
                    ComposeRulesRule(
                        id = "MutableParams",
                        description = "Composable function parameters should be immutable",
                        visitor = ::MutableParameters,
                        findingMessage = MUTABLE_PARAMS_MESSAGE,
                    ),
                    ComposeRulesRule(
                        id = "MutableStateParam",
                        description = "Avoid passing MutableState as a parameter",
                        visitor = ::MutableStateParameter,
                        findingMessage = MUTABLE_STATE_PARAM_MESSAGE,
                    ),
                    ComposeRulesRule(
                        id = "ParameterNaming",
                        description = "Parameters in Composable functions should follow proper naming conventions",
                        visitor = ::ParameterNaming,
                        findingMessage = PARAMETER_NAMING_MESSAGE,
                    ),
                    PreviewAnnotationNaming,
                    PreviewNaming,
                    PreviewNeedsComposableAnnotation,
                    PreviewPublic,
                    PreviewSignature,
                    ComposeRulesRule(
                        id = "RememberContentMissing",
                        description = "Movable content should be remembered",
                        visitor = ::RememberContentMissing,
                        findingMessage = REMEMBER_CONTENT_MISSING_MESSAGE,
                    ),
                    StaleRememberUpdatedStateInRemember,
                    ComposeRulesRule(
                        id = "StateParam",
                        description = "Avoid passing State as a parameter",
                        visitor = ::StateParameter,
                        findingMessage = STATE_PARAM_MESSAGE,
                    ),
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
                    UnnecessaryComposable,
                    UnnecessaryLaunchedEffect,
                    ComposeRulesRule(
                        id = "UnstableCollections",
                        description = "Avoid using unstable collections in Composable function signatures",
                        visitor = ::UnstableCollections,
                        activeByDefault = false,
                        findingMessage = UNSTABLE_COLLECTIONS_MESSAGE,
                    ),
                    UnusedAnimatedContentTargetState,
                    UnusedTransitionTargetState,
                    VarsWithoutStateBacking,
                ),
            "compose-swing-internal" to listOf(PreferImportsOverFqn, PublicDataClass),
            "compose-runtime" to emptyList(),
        )
}

private const val COMPOSABLE_ANNOTATION_NAMING_MESSAGE =
    "This annotation marks composable targets but its name is missing the `Composable` suffix. " +
        "The suffix makes its purpose clear at use sites. Rename the annotation to end in `Composable`."

private const val CONTENT_SLOT_REUSED_MESSAGE =
    "This content slot is used in multiple places. Each use has its own state. " +
        "Invoke the slot once, or remember it as movable content if it needs to move between branches."

private const val CONTENT_TRAILING_LAMBDA_MESSAGE =
    "The main content parameter is followed by other parameters. This prevents callers from using trailing lambda " +
        "syntax for the content. Move the main content parameter to the end of the parameter list."

private const val MUTABLE_PARAMS_MESSAGE =
    "This composable takes a mutable parameter whose changes Compose cannot observe. " +
        "The UI may show stale data after a mutation. Pass an immutable value or use observable state."

private const val PARAMETER_NAMING_MESSAGE =
    "This event callback has a past-tense name. It describes an event as already completed " +
        "instead of naming what the callback handles. Rename it in the present tense."

private const val UNSTABLE_COLLECTIONS_MESSAGE =
    "This composable takes a collection type that the Compose compiler cannot infer as stable, " +
        "even when its element type is stable. That can limit when the compiler skips recomposition. " +
        "Use a Kotlinx immutable collection or wrap the collection in a genuinely immutable type marked `@Immutable`."

private const val MUTABLE_STATE_PARAM_MESSAGE =
    "This composable takes mutable state directly. That gives both the component and its caller control of " +
        "the same state. Pass the current value and callbacks so the caller owns changes. If the component must " +
        "update the state directly, give it a state holder with named, observable properties for the values it manages."

private const val STATE_PARAM_MESSAGE =
    "This composable takes a `State` object. That ties the component's inputs to how the caller stores its state. " +
        "Pass the current value and use callbacks to request changes."

private const val REMEMBER_CONTENT_MISSING_MESSAGE =
    "This movable content is created during composition without being remembered. " +
        "Recreating it gives it a new identity and can discard its state. Remember the movable content."
