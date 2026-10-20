package org.jetbrains.compose.swing.samples.agent.harness

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecutableTest {
    @TempDir
    lateinit var folder: Path

    @Test
    fun `Windows resolves a pi shim through PATHEXT instead of the POSIX launcher`() {
        folder.resolve("pi").writeText("#!/bin/sh")
        folder.resolve("pi").toFile().setExecutable(true)
        val cmd = folder.resolve("pi.CMD").toFile().apply { writeText("@echo off") }

        assertEquals(cmd, executable("pi", "$folder", ".EXE;.CMD", isWindows = true))
        assertEquals(cmd, executable("${folder.resolve("pi")}", "", ".EXE;.CMD", isWindows = true))
        assertEquals(cmd, executable("pi.CMD", "$folder", ".EXE;.CMD", isWindows = true))
        assertNull(executable("missing", "$folder", ".CMD", isWindows = true))
    }

    @Test
    fun `POSIX requires executable permission and honors PATH order`() {
        val script = folder.resolve("pi").toFile().apply { writeText("#!/bin/sh") }
        assertNull(executable("pi", "$folder", ".CMD", isWindows = false))
        assertTrue(script.setExecutable(true))
        assertEquals(script, executable("pi", "$folder", ".CMD", isWindows = false))
    }
}
