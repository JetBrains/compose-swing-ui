// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import dev.detekt.api.SourceLocation
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.createEnvironment
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.rules.runtime.ComposeFqNames
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import kotlin.test.assertEquals

internal fun Rule.lintWithAnalysisApi(
    @Language("kotlin") content: String,
    vararg dependencyContents: String,
    allowCompilationErrors: Boolean = false,
): List<Finding> {
    require(this is RequiresAnalysisApi)
    val previousRuntime = ComposeFqNames.runtime
    val previousRuntimeSaveable = ComposeFqNames.runtimeSaveable
    val previousRuntimeRetain = ComposeFqNames.runtimeRetain

    return try {
        val fakeComposeFqName = FqName("com.example.compose.fake")
        ComposeFqNames.runtime = fakeComposeFqName
        ComposeFqNames.runtimeSaveable = fakeComposeFqName
        ComposeFqNames.runtimeRetain = fakeComposeFqName

        val environment = createEnvironment()
        lintWithContext(
            environment = environment,
            content = content,
            dependencyContents = arrayOf(fakeComposeRuntime(), *dependencyContents),
            allowCompilationErrors = allowCompilationErrors,
        )
    } finally {
        ComposeFqNames.runtime = previousRuntime
        ComposeFqNames.runtimeSaveable = previousRuntimeSaveable
        ComposeFqNames.runtimeRetain = previousRuntimeRetain
    }
}

@Language("kotlin")
internal fun codeWithFakeCompose(
    @Language("kotlin") code: String,
): String =
    """
    package com.example.compose.fake

    $code
    """.trimIndent()

@Language("kotlin")
// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.
@Suppress("LongMethod")
private fun fakeComposeRuntime(): String =
    codeWithFakeCompose(
        """
    import kotlinx.coroutines.CoroutineScope

    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.TYPE,
        AnnotationTarget.TYPE_PARAMETER,
        AnnotationTarget.PROPERTY_GETTER
    )
    annotation class Composable

    @Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
    annotation class ReadOnlyComposable

    @Composable
    fun <T> remember(vararg keys: Any?, calculation: () -> T): T = calculation()

    @Composable
    fun <T> remember(calculation: () -> T): T = calculation()

    @Composable
    fun <T> rememberSaveable(vararg keys: Any?, calculation: () -> T): T = calculation()

    @Composable
    fun <T> rememberSaveable(key: String?, calculation: () -> T): T = calculation()

    @Composable
    fun <T> rememberSaveable(inputs: Array<Any?>, init: () -> T): T = init()

    @Composable
    fun <T> rememberSaveable(key: String?, init: () -> T): T = init()

    @Composable
    fun <T> rememberSaveable(calculation: () -> T): T = calculation()

    @Composable
    fun <T> retain(vararg keys: Any?, calculation: () -> T): T = calculation()

    @Composable
    fun <T> retain(calculation: () -> T): T = calculation()

    @Composable
    fun LaunchedEffect(key1: Any?, block: suspend CoroutineScope.() -> Unit) = Unit

    @Composable
    fun SideEffect(effect: () -> Unit) = Unit

    @Composable
    fun SideEffect(key1: Any?, effect: () -> Unit) = Unit

    interface State<T> {
        val value: T
    }

    interface MutableState<T> : State<T> {
        override var value: T
    }

    interface IntState : State<Int> {
        val intValue: Int
        override val value: Int get() = intValue
    }

    interface MutableIntState : IntState, MutableState<Int> {
        override var intValue: Int
        override var value: Int
            get() = intValue
            set(value) {
                intValue = value
            }
    }

    interface LongState : State<Long> {
        val longValue: Long
        override val value: Long get() = longValue
    }

    interface MutableLongState : LongState, MutableState<Long> {
        override var longValue: Long
        override var value: Long
            get() = longValue
            set(value) {
                longValue = value
            }
    }

    interface FloatState : State<Float> {
        val floatValue: Float
        override val value: Float get() = floatValue
    }

    interface MutableFloatState : FloatState, MutableState<Float> {
        override var floatValue: Float
        override var value: Float
            get() = floatValue
            set(value) {
                floatValue = value
            }
    }

    interface DoubleState : State<Double> {
        val doubleValue: Double
        override val value: Double get() = doubleValue
    }

    interface MutableDoubleState : DoubleState, MutableState<Double> {
        override var doubleValue: Double
        override var value: Double
            get() = doubleValue
            set(value) {
                doubleValue = value
            }
    }

    @Composable
    fun <T> rememberUpdatedState(newValue: T): State<T> = object : State<T> {
        override val value: T get() = newValue
    }

    @Composable
    operator fun <T> State<T>.getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value

    operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
        this.value = value
    }

    open class CompositionLocal<T>(val current: T)

    class ProvidableCompositionLocal<T>(current: T) : CompositionLocal<T>(current)

    fun <T> compositionLocalOf(defaultFactory: () -> T): ProvidableCompositionLocal<T> =
        ProvidableCompositionLocal(defaultFactory())
    """,
    )

/** Asserts the findings start at exactly [locations], in any order. */
internal fun assertStartSourceLocations(
    findings: List<Finding>,
    vararg locations: SourceLocation,
) = assertEquals(
    locations.sortedWith(compareBy({ it.line }, { it.column })),
    findings.map { it.entity.location.source }.sortedWith(compareBy({ it.line }, { it.column })),
)

/** Asserts the findings sit on declarations with exactly these [names], in any order. */
internal fun assertTextLocations(
    findings: List<Finding>,
    vararg names: String,
) = assertEquals(
    names.sorted(),
    findings.map { (it.entity.ktElement as? KtNamedDeclaration)?.name ?: it.entity.signature }.sorted(),
)
