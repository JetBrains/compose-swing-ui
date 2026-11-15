// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import java.awt.Component
import java.awt.Container

/**
 * Walks the mounted tree under [host], including [host], so that metrics read what is mounted rather than what was
 * built.
 */
internal fun componentTree(host: Component): Sequence<Component> =
    sequence {
        yield(host)
        if (host is Container) host.components.forEach { yieldAll(componentTree(it)) }
    }

/**
 * Counts the containers in [host] that Swing has marked invalid, which is the number of layouts the next validation
 * will run. The harness lists them before it validates because validation clears the flag.
 */
internal fun invalidContainers(host: Container): Long {
    val own = if (host.isValid) 0L else 1L
    return own + host.components.filterIsInstance<Container>().sumOf { invalidContainers(it) }
}
