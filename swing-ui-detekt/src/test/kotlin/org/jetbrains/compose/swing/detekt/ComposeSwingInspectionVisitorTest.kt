// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.detekt

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import dev.detekt.test.utils.KotlinAnalysisApiEngine
import dev.detekt.test.utils.compileContentForTest
import dev.detekt.test.utils.createEnvironment
import org.jetbrains.compose.swing.rules.ComposeSwingRuleConfig
import org.jetbrains.compose.swing.rules.ComposeSwingRuleSets
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeSwingInspectionVisitorTest {
    @Test
    fun `reports each state creation from its call on every visit`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.mutableStateOf
                    import androidx.compose.runtime.snapshots.StateFactoryMarker

                    @StateFactoryMarker
                    fun otherState() = mutableStateOf(0)

                    @Composable
                    fun Outer() {
                        @Composable
                        fun Inner() {
                            mutableStateOf(1)
                            otherState()
                        }
                        Inner()
                    }
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                )
            val calls =
                file.collectDescendantsOfType<KtCallExpression> {
                    it.text == "mutableStateOf(1)" || it.text == "otherState()"
                }
            checkVisits(
                "RememberMissing",
                calls,
                listOf("mutableStateOf(1)", "otherState()"),
            ) { reports ->
                listOf("mutableStateOf", "otherState").zip(reports).forEach { (factory, report) ->
                    assertTrue(report.message.startsWith("This call to `$factory` creates state"))
                    assertTrue(report.message.endsWith("Call `$factory` inside `remember`."))
                }
            }
        }
    }

    @Test
    fun `reports composable naming with the same message on every visit`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun lower() {}

                    @Composable
                    fun Factory() = 42
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                )
            checkVisits(
                "ComposableNaming",
                file.collectDescendantsOfType<KtNamedFunction>(),
                listOf("lower", "Factory"),
            ) { reports ->
                assertTrue(reports[0].message.startsWith("This composable returns `Unit`"))
                assertTrue(reports[1].message.startsWith("This composable returns a value"))
                assertTrue(reports.all { "https://" !in it.message })
            }
        }
    }

    @Test
    fun `reports composition locals without a file visit`() {
        val file =
            compileContentForTest(
                """
                val LocalOne = compositionLocalOf { 1 }
                val LocalTwo = staticCompositionLocalOf { 2 }
                """.trimIndent(),
            )
        checkVisits(
            "CompositionLocalAllowlist",
            file.collectDescendantsOfType<KtProperty>(),
            listOf("LocalOne", "LocalTwo"),
        )
    }

    @ParameterizedTest
    @MethodSource("functionRules")
    fun `reports upstream findings without a file visit`(
        ruleId: String,
        source: String,
        anchors: List<String>,
    ) {
        val file = compileContentForTest(source)
        val functions = file.collectDescendantsOfType<KtNamedFunction> { it.name in setOf("One", "Two") }
        checkVisits(ruleId, functions, anchors)
    }

    @Test
    fun `reports aliased previews without a file visit`() {
        val file =
            compileContentForTest(
                """
                import org.jetbrains.compose.swing.tooling.Preview as SwingExample

                @SwingExample annotation class Themes
                @Themes @Composable fun One() {}
                @SwingExample @Composable fun Two() {}
                """.trimIndent(),
            )
        checkVisits("PreviewPublic", file.collectDescendantsOfType<KtNamedFunction>(), listOf("One", "Two"))
    }

    @Test
    fun `validates Swing preview functions from their own visits`() {
        val source =
            """
            import org.jetbrains.compose.swing.tooling.Preview as SwingExample

            @SwingExample annotation class PreviewVariants
            @PreviewVariants private fun One(label: String) {}
            @SwingExample private fun Two(label: String = "") {}
            """.trimIndent()
        val swingPreview = "package org.jetbrains.compose.swing.tooling\nannotation class Preview"
        KotlinAnalysisApiEngine().use { engine ->
            val file = engine.compile(source, dependencyCodes = listOf(swingPreview))
            val functions = file.collectDescendantsOfType<KtNamedFunction>()

            checkVisits("PreviewNeedsComposableAnnotation", functions, listOf("One", "Two"))
            checkVisits("PreviewSignature", functions, listOf("One", "Two"))
        }
    }

    @Test
    fun `reports an unkeyed effect when another effect keys the same parameter`() {
        val file =
            compileContentForTest(
                """
                import androidx.compose.runtime.LaunchedEffect

                @Composable
                fun One(action: () -> Unit) {
                    LaunchedEffect(action) { action() }
                    LaunchedEffect(Unit) { action() }
                }
                """.trimIndent(),
            )
        checkVisits(
            "LambdaParameterInRestartableEffect",
            file.collectDescendantsOfType<KtNamedFunction> { it.name == "One" },
            listOf("action"),
        ) { reports ->
            assertEquals(
                "This effect captures a callback outside its keys. " +
                    "The running effect can keep calling an old callback after the parameter changes. " +
                    "Add the callback as a key if changing it should restart the effect. To keep the effect running, " +
                    "wrap the callback in `rememberUpdatedState` and read its value inside the effect.",
                reports.single().message,
            )
        }
    }

    @Test
    fun `resolves a restartable effect among wildcard imports and a local overload`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    import androidx.compose.runtime.*
                    import kotlin.collections.*

                    @Composable
                    fun Example(action: () -> Unit) {
                        fun LaunchedEffect() = Unit
                        LaunchedEffect(Unit) { action() }
                    }
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                )
            checkVisits(
                "LambdaParameterInRestartableEffect",
                file.collectDescendantsOfType<KtNamedFunction> { it.name == "Example" },
                listOf("action"),
            )
        }
    }

    @Test
    fun `reports an unshadowed effect when another effect shadows the same parameter`() {
        val file =
            compileContentForTest(
                """
                import androidx.compose.runtime.LaunchedEffect

                @Composable
                fun One(action: () -> Unit) {
                    LaunchedEffect(Unit) { action() }
                    run {
                        val action = {}
                        LaunchedEffect(Unit) { action() }
                    }
                }
                """.trimIndent(),
            )
        checkVisits(
            "LambdaParameterInRestartableEffect",
            file.collectDescendantsOfType<KtNamedFunction> { it.name == "One" },
            listOf("action"),
        )
    }

    @Test
    fun `reports nested composable declarations from their own visits`() {
        val file =
            compileContentForTest(
                """
                import androidx.compose.runtime.LaunchedEffect

                @Composable
                fun Outer(outerAction: () -> Unit) {
                    LaunchedEffect(Unit) { outerAction() }
                    @Composable
                    fun Inner(innerAction: () -> Unit) {
                        LaunchedEffect(Unit) { innerAction() }
                    }
                }
                """.trimIndent(),
            )
        checkVisits(
            "LambdaParameterInRestartableEffect",
            file.collectDescendantsOfType<KtNamedFunction> { it.name in setOf("Outer", "Inner") },
            listOf("innerAction", "outerAction"),
        )
    }

    @Test
    fun `nested function parameters do not report the outer parameter`() {
        val file =
            compileContentForTest(
                """
                import androidx.compose.runtime.LaunchedEffect

                @Composable
                fun Outer(action: () -> Unit) {
                    @Composable
                    fun Inner(action: () -> Unit) {
                        LaunchedEffect(Unit) { action() }
                    }
                }
                """.trimIndent(),
            )
        val functions = file.collectDescendantsOfType<KtNamedFunction>().associateBy { it.name }
        val reports = mutableListOf<PsiElement>()
        val visitor =
            ComposeSwingRuleSets.ruleSets.values
                .flatten()
                .single { it.id == "LambdaParameterInRestartableEffect" }
                .createVisitor(reporter = { anchor, _ -> reports += anchor }, config = Defaults)

        functions.getValue("Outer").accept(visitor)
        assertTrue(reports.isEmpty())
        functions.getValue("Inner").accept(visitor)
        val innerParameter =
            functions
                .getValue("Inner")
                .valueParameters
                .single()
                .nameIdentifier
        assertEquals(listOf(innerParameter), reports)
    }

    @Test
    fun `reports conditional Swing content on independent declaration visits`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    package org.jetbrains.compose.swing.foundation.layout

                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Column(content: @Composable () -> Unit) = content()

                    @Composable
                    fun Screen(first: Boolean, second: Boolean) {
                        @Composable
                        fun Inner() {
                            Column { if (second) Column {} }
                        }
                        Column { if (first) Column {} }
                        Inner()
                    }
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                )
            val functions =
                file
                    .collectDescendantsOfType<KtNamedFunction> { it.name == "Screen" || it.name == "Inner" }
                    .sortedBy { it.textOffset }
            checkVisits(
                "ConditionHoist",
                functions,
                listOf("if (first) Column {}", "if (second) Column {}"),
            )
        }
    }

    @Test
    fun `reports a stale state read during a visit to its remember call`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    import androidx.compose.runtime.Composable
                    import androidx.compose.runtime.remember
                    import androidx.compose.runtime.rememberUpdatedState

                    @Composable
                    fun Screen(input: String) {
                        val state = rememberUpdatedState(input)
                        remember { state.value }
                        remember { state.value.length }
                    }
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                    allowCompilationErrors = true,
                )
            checkVisits(
                "StaleRememberUpdatedStateInRemember",
                file.collectDescendantsOfType<KtCallExpression> { it.calleeExpression?.text == "remember" },
                listOf("state", "state"),
            )
        }
    }

    @Test
    fun `reports an unbacked var from its property visit`() {
        KotlinAnalysisApiEngine().use { engine ->
            val file =
                engine.compile(
                    """
                    import androidx.compose.runtime.Composable

                    @Composable
                    fun Screen() {
                        var count = 0
                        var label = "initial"
                    }
                    """.trimIndent(),
                    jvmClasspathRoots = createEnvironment().jvmClasspathRoots,
                )
            checkVisits(
                "VarsWithoutStateBacking",
                file.collectDescendantsOfType<KtProperty>(),
                listOf("var count = 0", "var label = \"initial\""),
            )
        }
    }

    private fun checkVisits(
        ruleId: String,
        elements: List<KtElement>,
        expectedAnchors: List<String>,
        checkMessages: (List<Report>) -> Unit = {},
    ) {
        val currentElement = ThreadLocal<PsiElement>()
        val reports = ThreadLocal.withInitial { mutableListOf<Report>() }
        val visitor =
            ComposeSwingRuleSets.ruleSets.values
                .flatten()
                .single { it.id == ruleId }
                .createVisitor(
                    reporter = { anchor, message ->
                        val element = currentElement.get()
                        assertEquals(element.containingFile, anchor.containingFile, ruleId)
                        assertTrue(
                            element.textRange.contains(anchor.textRange),
                            "$ruleId reported outside ${element.text}",
                        )
                        reports.get() += Report(anchor.text, message)
                    },
                    config = Defaults,
                )

        fun visit(element: KtElement): List<Report> {
            currentElement.set(element)
            return try {
                element.accept(visitor)
                reports.get().toList()
            } finally {
                reports.remove()
                currentElement.remove()
            }
        }

        val concurrent =
            Executors.newFixedThreadPool(elements.size).use { pool ->
                val start = CyclicBarrier(elements.size)
                val pending =
                    elements.map { element ->
                        pool.submit<Report> {
                            ReadAction.compute<Report, RuntimeException> {
                                start.await(10, TimeUnit.SECONDS)
                                visit(element).single()
                            }
                        }
                    }
                pending.map { it.get(30, TimeUnit.SECONDS) }
            }
        val forward = elements.map { visit(it).single() }
        assertEquals(expectedAnchors, forward.map(Report::anchor), ruleId)
        checkMessages(forward)
        assertEquals(forward, concurrent, ruleId)
        assertEquals(forward.asReversed(), elements.asReversed().map { visit(it).single() }, ruleId)
        assertEquals(emptyList(), visit(elements.first().containingKtFile), ruleId)
    }

    private data class Report(
        val anchor: String,
        val message: String,
    )

    private object Defaults : ComposeSwingRuleConfig {
        override fun <T> valueOrDefault(
            key: String,
            default: T,
        ): T = default

        override fun valueOrDefault(
            key: String,
            default: List<String>,
        ): List<String> = default
    }

    companion object {
        @JvmStatic
        fun functionRules(): List<Arguments> =
            listOf(
                Arguments.of(
                    "ModifierMissing",
                    """
                    import org.jetbrains.compose.swing.components.Label
                    import org.jetbrains.compose.swing.components.layout.Panel

                    @Composable fun One() { Panel {} }
                    @Composable fun Two() { Label("text") }
                    """.trimIndent(),
                    listOf("One", "Two"),
                ),
                Arguments.of(
                    "ComposableParamOrder",
                    """
                    fun interface Callback { fun run() }
                    @Composable fun One(default: Int = 0, required: Int, action: Callback) {}
                    @Composable fun Two(default: Int = 0, required: Int, action: Callback) {}
                    """.trimIndent(),
                    listOf("One", "Two"),
                ),
                Arguments.of(
                    "LambdaParameterInRestartableEffect",
                    """
                    import androidx.compose.runtime.LaunchedEffect as Launch

                    fun interface Callback { fun run() }
                    @Composable fun One(action: Callback) { Launch(Unit) { action() } }
                    @Composable fun Two(action: Callback) { androidx.compose.runtime.LaunchedEffect(Unit) { action() } }
                    """.trimIndent(),
                    listOf("action", "action"),
                ),
                Arguments.of(
                    "ContentEmitterReturningValues",
                    """
                    import org.jetbrains.compose.swing.components.Label

                    @Composable fun Leaf() { Label("text") }
                    @Composable fun Relay() { Leaf() }
                    @Composable fun One(): Int { Relay(); return 1 }
                    @Composable fun Two(): Int { Relay(); return 2 }
                    """.trimIndent(),
                    listOf("One", "Two"),
                ),
                Arguments.of(
                    "MultipleEmitters",
                    """
                    import org.jetbrains.compose.swing.components.Label

                    @Composable fun Leaf() { Label("text") }
                    @Composable fun Relay() { Leaf() }
                    @Composable fun One() { Relay(); Relay() }
                    @Composable fun Two() { Relay(); Label("text") }
                    """.trimIndent(),
                    listOf("One", "Two"),
                ),
            )
    }
}
