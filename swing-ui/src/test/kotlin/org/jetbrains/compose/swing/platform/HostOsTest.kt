package org.jetbrains.compose.swing.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the host-OS resolution the platform-dependent code branches on: the family an `os.name` value
 * names, the predicate that selects a single family, and the family of the running JVM.
 *
 * The names are asserted directly rather than through the running host, so the mapping is held for
 * every family on any machine the suite runs on.
 */
class HostOsTest {
    @Test
    fun anOsNameResolvesToItsFamilyWhateverTheCaseOfTheName() {
        assertEquals(HostOs.MacOs, hostOsOf("Mac OS X"), "Mac OS X")
        assertEquals(HostOs.MacOs, hostOsOf("mac os x"), "mac os x")
        assertEquals(HostOs.Windows, hostOsOf("Windows 11"), "Windows 11")
        assertEquals(HostOs.Windows, hostOsOf("windows 11"), "windows 11")
        assertEquals(HostOs.Linux, hostOsOf("Linux"), "Linux")
        assertEquals(HostOs.Linux, hostOsOf("linux"), "linux")
    }

    @Test
    fun aNameOutsideTheFamiliesIsUnknown() {
        assertEquals(HostOs.Unknown, hostOsOf("FreeBSD"), "FreeBSD")
        assertEquals(HostOs.Unknown, hostOsOf("AIX"), "AIX")
        assertEquals(HostOs.Unknown, hostOsOf(""), "the empty name a JVM without the property leaves")
    }

    @Test
    fun theMacOsPredicateHoldsForThatFamilyAlone() {
        for (os in HostOs.entries) {
            assertEquals(os == HostOs.MacOs, os.isMacOs, "$os.isMacOs")
        }
    }

    @Test
    fun theHostFamilyMatchesTheOsNamePropertyItself() {
        val osName = System.getProperty("os.name").orEmpty()
        assertEquals(hostOsOf(osName), hostOs, "unexpected family for os.name '$osName'")
    }
}
