// Copyright 2026 Nacho Lopez
// SPDX-License-Identifier: Apache-2.0
// Assertions use kotlin.test in place of AssertJ.

package org.jetbrains.compose.swing.detekt.composerules

import dev.detekt.api.Config
import dev.detekt.api.SourceLocation
import org.intellij.lang.annotations.Language
import org.jetbrains.compose.swing.detekt.toDetektRule
import org.jetbrains.compose.swing.rules.runtime.StaleRememberUpdatedStateInRemember
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Upstream compose-rules structure, kept so later releases replay onto the fork without restructuring.
@Suppress("LargeClass")
class StaleRememberUpdatedStateInRememberCheckTest {
    private val rule = StaleRememberUpdatedStateInRemember.toDetektRule(Config.empty)

    @Test
    fun `reports rememberUpdatedState delegated value read directly inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState state value read directly inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss = rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(latestOnDismiss.value)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report rememberUpdatedState state object passed inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss = rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: State<() -> Unit>)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report rememberUpdatedState state value when remember is keyed by source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss = rememberUpdatedState(onDismiss)
                val holder = remember(onDismiss) {
                    Holder(latestOnDismiss.value)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report rememberUpdatedState state value when remember is keyed by state value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss = rememberUpdatedState(onDismiss)
                val holder = remember(latestOnDismiss.value) {
                    Holder(latestOnDismiss.value)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports fully qualified rememberUpdatedState delegated value read directly inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by com.example.compose.fake.rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read inside named remember calculation`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit, other: Any) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember(calculation = {
                    Holder(latestOnDismiss)
                }, keys = arrayOf(other))
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read directly inside rememberSaveable and retain`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            import com.example.compose.fake.retain as retainValue
            @Composable
            fun Example(onDismiss: () -> Unit, onConfirm: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val latestOnConfirm by rememberUpdatedState(onConfirm)
                val dismissHolder = rememberSaveable {
                    Holder(latestOnDismiss)
                }
                val confirmHolder = retainValue {
                    Holder(latestOnConfirm)
                }
            }

            class Holder(val callback: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(2, findings.size)
        assertStartSourceLocations(
            findings,
            SourceLocation(10, 24),
            SourceLocation(13, 24),
        )
        assertEquals(
            listOf(expectedStaleReadMessage("rememberSaveable"), expectedStaleReadMessage("retain")),
            findings.map { it.message },
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read when rememberSaveable key is not an input`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(key: String, onDismiss: () -> Unit) {
                val latestKey by rememberUpdatedState(key)
                val holder = rememberSaveable(key = key, calculation = {
                    Holder(latestKey.length)
                })
            }

            class Holder(val length: Int)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("rememberSaveable"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read inside named rememberSaveable init`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(key: String, onDismiss: () -> Unit) {
                val latestKey by rememberUpdatedState(key)
                val holder = rememberSaveable(init = {
                    Holder(latestKey.length)
                }, key = key)
            }

            class Holder(val length: Int)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("rememberSaveable"),
            findings.single().message,
        )
    }

    @Test
    fun `reports read when nested out-of-scope rememberUpdatedState property has the same name`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit, other: () -> Unit, condition: Boolean) {
                val latest by rememberUpdatedState(onDismiss)
                if (condition) {
                    val latest by rememberUpdatedState(other)
                }
                val holder = remember {
                    Holder(latest)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(11, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report when remember is keyed by the rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember(onDismiss) {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when remember is keyed by named array containing rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember(keys = arrayOf(onDismiss), calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when remember is keyed by array variable containing source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports when remember key array variable is mutable`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                var keys = arrayOf(onDismiss)
                keys = arrayOf(Unit)
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(10, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports when remember key array variable contents are mutated`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                keys[0] = {}
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(10, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports when remember key array variable contents are mutated from eager run lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                run {
                    keys[0] = {}
                }
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(12, 24),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report when remember key array variable is mutated only from a deferred lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                val mutateLater = { keys[0] = {} }
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
                mutateLater()
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when shadowed key array variable contents are mutated before remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                run {
                    val keys = arrayOf(Unit)
                    keys[0] = Unit
                }
                val holder = remember(keys = keys, calculation = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when rememberSaveable is keyed by inputs containing rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = rememberSaveable(inputs = arrayOf(onDismiss), init = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when rememberSaveable is keyed by inputs variable containing source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val inputs = arrayOf(onDismiss)
                val holder = rememberSaveable(inputs = inputs, init = {
                    Holder(latestOnDismiss)
                })
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when retain is keyed by spread array containing rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            import com.example.compose.fake.retain as retainValue
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val keys = arrayOf(onDismiss)
                val holder = retainValue(*keys) {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports rememberUpdatedState delegated value read inside eager run lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    run {
                        Holder(latestOnDismiss)
                    }
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(9, 28),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read inside eager let lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    onDismiss.let {
                        Holder(latestOnDismiss)
                    }
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(9, 28),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `reports rememberUpdatedState delegated value read inside eager takeIf and takeUnless lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: (() -> Unit)?) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    val enabled = onDismiss.takeIf { latestOnDismiss != null }
                    val disabled = onDismiss.takeUnless { latestOnDismiss != null }
                    Holder(enabled, disabled)
                }
            }

            class Holder(val enabled: (() -> Unit)?, val disabled: (() -> Unit)?)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(2, findings.size)
        assertStartSourceLocations(
            findings,
            SourceLocation(8, 50),
            SourceLocation(9, 55),
        )
        for (finding in findings) {
            assertEquals(
                expectedStaleReadMessage("remember"),
                finding.message,
            )
        }
    }

    @Test
    fun `reports rememberUpdatedState delegated value copied to local property inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    val captured = latestOnDismiss
                    Holder(captured)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(8, 32),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report rememberUpdatedState read inside custom deferred run lambda`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Scheduler {
                fun run(block: () -> Unit) {}
            }

            @Composable
            fun Example(onDismiss: () -> Unit, scheduler: Scheduler) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    scheduler.run {
                        latestOnDismiss()
                    }
                    Holder(onDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports when remember key shadows rememberUpdatedState source name`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    val onDismiss = {}
                    remember(onDismiss) {
                        Holder(latestOnDismiss)
                    }
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(10, 28),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report when remember is keyed by qualified rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Model(val onDismiss: () -> Unit)

            @Composable
            fun Example(model: Model) {
                val latestOnDismiss by rememberUpdatedState(model.onDismiss)
                val holder = remember(model.onDismiss) {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when remember is keyed by nested qualified rememberUpdatedState source value`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Model(val callbacks: Callbacks)
            class Callbacks(val onDismiss: () -> Unit)

            @Composable
            fun Example(model: Model) {
                val latestOnDismiss by rememberUpdatedState(model.callbacks.onDismiss)
                val holder = remember(model.callbacks.onDismiss) {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports when qualified remember key receiver shadows source receiver`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            class Model(val onDismiss: () -> Unit)

            @Composable
            fun Example(model: Model, other: Model) {
                val latestOnDismiss by rememberUpdatedState(model.onDismiss)
                val holder = remember {
                    val model = other
                    remember(model.onDismiss) {
                        Holder(latestOnDismiss)
                    }
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertEquals(1, findings.size)
        assertEquals(
            SourceLocation(12, 28),
            findings
                .single()
                .entity.location.source,
        )
        assertEquals(
            expectedStaleReadMessage("remember"),
            findings.single().message,
        )
    }

    @Test
    fun `does not report deferred reads inside nested lambdas`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(onDismiss = { latestOnDismiss() })
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report shadowed rememberUpdatedState name inside remember`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    val latestOnDismiss = onDismiss
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report deferred reads inside nested functions`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    fun invokeLater() {
                        latestOnDismiss()
                    }
                    Holder(::invokeLater)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report deferred reads inside property getters`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            interface Callback {
                val isEnabled: Boolean
            }

            @Composable
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val callback = remember {
                    object : Callback {
                        override val isEnabled: Boolean
                            get() = latestOnDismiss.hashCode() > 0
                    }
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report rememberUpdatedState property declaration itself`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            @Composable
            fun Example(onDismiss: () -> Unit) {
                val holder = remember {
                    val latestOnDismiss by rememberUpdatedState(onDismiss)
                }
            }
            """,
            )

        val findings = rule.lintWithAnalysisApi(code)

        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report outside composable scope`() {
        @Language("kotlin")
        val code =
            codeWithFakeCompose(
                """
            fun Example(onDismiss: () -> Unit) {
                val latestOnDismiss by rememberUpdatedState(onDismiss)
                val holder = remember {
                    Holder(latestOnDismiss)
                }
            }

            class Holder(val onDismiss: () -> Unit)
            """,
            )

        val findings = rule.lintWithAnalysisApi(code, allowCompilationErrors = true)

        assertTrue(findings.isEmpty())
    }
}

internal fun expectedStaleReadMessage(cacheFunction: String): String {
    val argument = if (cacheFunction == "rememberSaveable") "an input" else "a key"
    return "This code reads a `rememberUpdatedState` value directly inside `$cacheFunction`. " +
        "The cached result can keep the value read when it was created even after `rememberUpdatedState` receives " +
        "a new value. Add the source value as $argument to recalculate the result when it changes, " +
        "or defer the read in a lambda to get the latest value when it is used."
}
