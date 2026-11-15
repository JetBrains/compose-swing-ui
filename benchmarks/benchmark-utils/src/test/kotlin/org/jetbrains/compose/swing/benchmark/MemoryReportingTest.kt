// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import org.junit.jupiter.api.Test
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Tests the heap census, the class histogram report, and the instance growth rule of the memory measurement. */
class MemoryReportingTest {
    @Test
    fun `heap census preserves individual classes and excludes collector filler`() {
        val histogram =
            """
            num     #instances         #bytes  class name (module)
            1:              5            120  java.lang.String (java.base)
            2:              1             64  jdk.internal.vm.Filler (java.base)
            3:              2             48  [B (java.base)
            4:              1             24  java.lang.String (java.base)
            Total           9            256
            """.trimIndent()
        val census = LiveHeap().parse(histogram)
        assertEquals(setOf(String::class.java.name, ByteArray::class.java.name), census.keys)
        assertEquals(6L, census.getValue(String::class.java.name).instances)
        assertEquals(144L, census.getValue(String::class.java.name).bytes)
        assertEquals(2L, census.getValue(ByteArray::class.java.name).instances)
        assertEquals(48L, census.getValue(ByteArray::class.java.name).bytes)
    }

    @Test
    fun `census reuses storage and clears classes absent from the next collection`() {
        val heap = LiveHeap()
        val name = JLabel::class.java.name
        val absent = JPanel::class.java.name
        val census = heap.parse("1: 2 48 $name\n2: 1 24 $absent")
        val usage = census.getValue(name)
        assertSame(census, heap.parse("1: 3 72 $name", census))
        assertSame(usage, census.getValue(name))
        assertEquals(3L, usage.instances)
        assertEquals(72L, usage.bytes)
        assertEquals(0L, census.getValue(absent).instances)
        assertEquals(0L, census.getValue(absent).bytes)
    }

    @Test
    fun `reporter lists every class that grew, largest first`() {
        val entries = mutableListOf<Map<String, String>>()
        val measurement = MemoryMeasurement(TreeFixture) { entries.add(it) }
        val names =
            listOf(
                String::class.java,
                ByteArray::class.java,
                IntArray::class.java,
                LongArray::class.java,
                FloatArray::class.java,
                DoubleArray::class.java,
                BooleanArray::class.java,
                CharArray::class.java,
                ShortArray::class.java,
                JPanel::class.java,
                JLabel::class.java,
                TreeFixture::class.java,
            ).map { it.name }
        val before = names.associateWith { LiveHeap.ClassUsage(2, 1000) }
        val after =
            names.withIndex().associate { (index, name) ->
                name to LiveHeap.ClassUsage(index + 2L, 1000 + index * 100L)
            } + (MemoryReportingTest::class.java.name to LiveHeap.ClassUsage(1, 1))
        val summary =
            "(org.jetbrains.compose.swing.benchmark.TreeFixture, 11, 1100), (javax.swing.JLabel, 10, 1000), " +
                "(javax.swing.JPanel, 9, 900), ([S, 8, 800), ([C, 7, 700), ([Z, 6, 600), ([D, 5, 500), " +
                "([F, 4, 400), ([J, 3, 300), ([I, 2, 200), ([B, 1, 100), " +
                "(org.jetbrains.compose.swing.benchmark.MemoryReportingTest, 1, 1)"
        measurement.publishHistogram(100, "afterDispose", before, after)
        assertEquals(mapOf("Tree/retained/100/afterDispose/histogram" to summary), entries.single())
    }

    @Test
    fun `one excess instance per added widget fails and names the class`() {
        val measurement = MemoryMeasurement(TreeFixture) {}
        val name = TreeFixture::class.java.name
        val disposed =
            mapOf(
                100 to mapOf(name to LiveHeap.ClassUsage(100, 100)),
                1000 to mapOf(name to LiveHeap.ClassUsage(1000, 1000)),
            )
        val failure = assertFailsWith<IllegalStateException> { measurement.retainedAfterDispose(disposed) }
        assertTrue(failure.message.orEmpty().contains(name))
    }

    @Test
    fun `instance growth matching the Swing twin and constant residue pass`() {
        val measurement = MemoryMeasurement(TreeFixture) {}
        val name = TreeFixture::class.java.name
        val growing =
            mapOf(
                100 to mapOf(name to LiveHeap.ClassUsage(100, 2400)),
                1000 to mapOf(name to LiveHeap.ClassUsage(1000, 24000)),
            )
        measurement.retainedAfterDispose(growing, growing)
        val constant = mapOf(name to LiveHeap.ClassUsage(1, 24))
        measurement.retainedAfterDispose(mapOf(100 to constant, 1000 to constant))
    }

    @Test
    fun `a class absent at the medium size is still checked`() {
        val measurement = MemoryMeasurement(TreeFixture) {}
        val name = JLabel::class.java.name
        val failure =
            assertFailsWith<IllegalStateException> {
                measurement.retainedAfterDispose(
                    mapOf(
                        100 to emptyMap(),
                        1000 to mapOf(name to LiveHeap.ClassUsage(900, 900)),
                    ),
                )
            }
        assertTrue(failure.message.orEmpty().contains(name))
    }

    @Test
    fun `one growing shared array stays outside the instance rule`() {
        val measurement = MemoryMeasurement(TreeFixture) {}
        val name = ByteArray::class.java.name
        measurement.retainedAfterDispose(
            mapOf(
                100 to mapOf(name to LiveHeap.ClassUsage(1, 100)),
                1000 to mapOf(name to LiveHeap.ClassUsage(1, 1000)),
            ),
        )
    }
}
