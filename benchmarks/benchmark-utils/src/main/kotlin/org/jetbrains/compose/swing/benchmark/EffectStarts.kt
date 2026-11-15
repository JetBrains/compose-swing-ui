// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package org.jetbrains.compose.swing.benchmark

import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Counts every coroutine start under the effect context of the recomposer.
 *
 * Kotlin offers no start hook, so the count rides on [CopyableThreadContextElement.copyForChild], which the runtime
 * calls once for each child coroutine it creates.
 */
internal class EffectStarts :
    AbstractCoroutineContextElement(EffectStartsKey),
    CopyableThreadContextElement<Unit> {
    override fun copyForChild(): CopyableThreadContextElement<Unit> {
        BenchmarkInstrumentation.metrics?.add("effectStarts")
        return this
    }

    override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext = overwritingElement

    override fun updateThreadContext(context: CoroutineContext): Unit = Unit

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Unit,
    ): Unit = Unit

    companion object EffectStartsKey : CoroutineContext.Key<EffectStarts>
}
