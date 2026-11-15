// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests the class histogram of the live heap. */
class LiveHeapTest {
    private class CensusObject

    @Test
    fun `histogram counts reachable objects and no unreachable ones`() {
        val heap = LiveHeap()
        val name = CensusObject::class.java.name
        val retained = ArrayList<CensusObject>()
        repeat(1000) { retained.add(CensusObject()) }
        assertEquals(1000, heap.histogram().getValue(name).instances)
        retained.clear()
        assertNull(heap.histogram()[name])
    }
}
