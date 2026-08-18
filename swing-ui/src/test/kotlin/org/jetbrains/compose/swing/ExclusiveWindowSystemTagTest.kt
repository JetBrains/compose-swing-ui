package org.jetbrains.compose.swing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Holds the build's half of the test split to the tag [ExclusiveWindowSystem] actually carries.
 *
 * The two are written separately - once here in Kotlin, once as a literal in the module's build script -
 * and nothing but this test connects them. Left unconnected they fail in the one direction that reports
 * nothing: the task filtering for a tag no test carries selects an empty suite, which passes, while every
 * test meant to run alone falls back into the parallel task and skips on a window system it now shares.
 *
 * This test is deliberately untagged, so it runs in that parallel task on every build.
 */
class ExclusiveWindowSystemTagTest {
    @Test
    fun theBuildSplitsTheSuiteOnTheTagTheAnnotationCarries() {
        val fromBuild = System.getProperty(TAG_PROPERTY)
        assertNotNull(fromBuild, "the build must hand every test task the tag it splits on, as $TAG_PROPERTY")
        assertEquals(EXCLUSIVE_WINDOW_SYSTEM_TAG, fromBuild, "the build splits on a tag no test carries")
    }
}

private const val TAG_PROPERTY = "compose.swing.test.exclusiveWindowSystemTag"
