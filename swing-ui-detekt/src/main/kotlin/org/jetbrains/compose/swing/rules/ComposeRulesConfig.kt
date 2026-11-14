// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.rules

import io.nlopez.compose.core.ComposeKtConfig

/** Presents a rule's settings to compose-rules through its `ComposeKtConfig` interface. */
internal class ComposeRulesConfig(
    private val config: ComposeSwingRuleConfig,
) : ComposeKtConfig {
    override fun getInt(
        key: String,
        default: Int,
    ): Int = config.valueOrDefault(key, default)

    override fun getString(
        key: String,
        default: String?,
    ): String? = config.valueOrDefault(key, default)

    override fun getList(
        key: String,
        default: List<String>,
    ): List<String> = config.valueOrDefault(key, default)

    override fun getSet(
        key: String,
        default: Set<String>,
    ): Set<String> = getList(key, default.toList()).toSet()

    override fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean = config.valueOrDefault(key, default)
}
