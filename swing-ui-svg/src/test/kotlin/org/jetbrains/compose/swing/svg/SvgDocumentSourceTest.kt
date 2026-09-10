// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.DomProcessor
import com.github.weisj.jsvg.parser.LoaderContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SvgDocumentSourceTest {
    @Test
    fun `a document is null until it has been loaded`() =
        runComposeSwingTest {
            val seen = mockk<(SVGDocument?) -> Unit>(relaxed = true)
            var loaded: SVGDocument? = null
            setContent {
                loaded = rememberSvgDocument(TestSvg.Static.url)
                seen(loaded)
            }

            waitUntil { loaded != null }
            val document = assertNotNull(loaded)
            verifyOrder {
                seen(null)
                seen(document)
            }
            assertEquals(Dimension(24, 16), document.intrinsicSize())
        }

    @Test
    fun `changing the source loads again`() =
        runComposeSwingTest {
            var url by mutableStateOf(TestSvg.Static.url)
            var loaded: SVGDocument? = null
            setContent { loaded = rememberSvgDocument(url) }
            waitUntil { loaded != null }
            val first = assertNotNull(loaded)

            url = TestSvg.Finite.url
            waitUntil { loaded != null && loaded !== first }

            assertTrue(assertNotNull(loaded).isAnimated, "the second source's document should be the animated one")
        }

    @Test
    fun `a url spelled another way that URL equality treats as the same loads again`() =
        runComposeSwingTest {
            val fixture = TestSvg.Static.url
            // A file URL naming the local host is equal to the same path with no host.
            val spelledWithHost = URI("file", "localhost", fixture.path, null).toURL()
            assertEquals(fixture, spelledWithHost, "the fixture needs two urls that URL.equals cannot tell apart")
            assertNotEquals(fixture.toExternalForm(), spelledWithHost.toExternalForm())
            var url by mutableStateOf(fixture, neverEqualPolicy())
            var loaded: SVGDocument? = null
            setContent { loaded = rememberSvgDocument(url) }
            waitUntil { loaded != null }
            val first = loaded

            url = spelledWithHost

            waitUntil { loaded !== first }
        }

    @Test
    fun `the previous document stays while a replacement url loads`() =
        runComposeSwingTest {
            val parse = HeldParse()
            try {
                var url by mutableStateOf(TestSvg.Static.url)
                var loaded: SVGDocument? = null
                setContent { loaded = rememberSvgDocument(url, parseContext = parse.dispatcher) }
                waitUntil { loaded != null }
                val first = assertNotNull(loaded)

                parse.hold()
                url = TestSvg.Finite.url
                waitUntil { parse.held == 1 }
                assertSame(first, loaded, "the previous document should stay until the replacement has loaded")

                parse.release()
                waitUntil { loaded !== first }
                assertTrue(assertNotNull(loaded).isAnimated)
            } finally {
                parse.close()
            }
        }

    @Test
    fun `the previous document stays while a replacement loader context loads`() =
        runComposeSwingTest {
            val parse = HeldParse()
            try {
                val processor = mockk<DomProcessor>(relaxed = true)
                var context by mutableStateOf<LoaderContext?>(LoaderContext.builder().preProcessor(processor).build())
                var loaded: SVGDocument? = null
                setContent {
                    loaded = rememberSvgDocument(TestSvg.Static.url, context, parse.dispatcher)
                }
                waitUntil { loaded != null }
                val first = assertNotNull(loaded)
                // The load should go through the supplied loader context.
                verify(exactly = 1) { processor.process(any()) }

                parse.hold()
                context = LoaderContext.builder().preProcessor(processor).build()
                waitUntil { parse.held == 1 }
                assertSame(first, loaded, "the previous document should stay until the replacement has loaded")

                parse.release()
                waitUntil { loaded !== first }
                val second = assertNotNull(loaded)
                // The replacement should go through its own loader context.
                verify(exactly = 2) { processor.process(any()) }

                context = null
                waitUntil { loaded !== second }
                // A null loader context should load with JSVG's defaults.
                verify(exactly = 2) { processor.process(any()) }
            } finally {
                parse.close()
            }
        }

    @Test
    fun `a source that is not an SVG is refused rather than resolved to nothing`() =
        runComposeSwingTest {
            val parse = HeldParse()
            try {
                val malformed = assertNotNull(SvgDocumentSourceTest::class.java.getResource("/svg/malformed.svg"))
                parse.hold()
                setContent { rememberSvgDocument(malformed, parseContext = parse.dispatcher) }
                waitUntil { parse.held == 1 }

                val thrown =
                    assertFailsWith<IllegalArgumentException> {
                        parse.release()
                        waitUntil { false }
                    }

                assertTrue(thrown.message.orEmpty().contains(malformed.toString()), "the message should name the url")
            } finally {
                parse.close()
            }
        }

    @Test
    fun `a failure of a load replaced while it parsed does not fail the composition`() =
        runComposeSwingTest {
            val parse = HeldParse()
            try {
                var context by mutableStateOf<LoaderContext?>(null)
                var loaded: SVGDocument? = null
                setContent { loaded = rememberSvgDocument(TestSvg.Static.url, context, parse.dispatcher) }
                waitUntil { loaded != null }
                val previous = loaded

                val entered = CompletableFuture<Unit>()
                val release = CompletableFuture<Unit>()
                val failing =
                    DomProcessor {
                        entered.complete(Unit)
                        release.get()
                        error("the document cannot be processed")
                    }
                context = LoaderContext.builder().preProcessor(failing).build()
                waitUntil { entered.isDone }
                context = null
                awaitIdle()

                release.complete(Unit)
                // The parse thread runs in order, so once this task has run the failed parse has finished.
                parse.awaitDrained()
                awaitIdle()

                assertNotSame(previous, loaded, "the replacement should have loaded")
                assertEquals(Dimension(24, 16), assertNotNull(loaded).intrinsicSize())
            } finally {
                parse.close()
            }
        }

    @Test
    fun `changing the parse context loads again`() =
        runComposeSwingTest {
            val first = HeldParse()
            val second = HeldParse()
            try {
                var context by mutableStateOf<CoroutineContext>(first.dispatcher)
                var loaded: SVGDocument? = null
                setContent { loaded = rememberSvgDocument(TestSvg.Static.url, parseContext = context) }
                waitUntil { loaded != null }
                val previous = loaded
                verify(exactly = 0) { second.executor.execute(any()) }

                context = second.dispatcher

                waitUntil { loaded !== previous }
                verify(exactly = 1) { second.executor.execute(any()) }
            } finally {
                first.close()
                second.close()
            }
        }

    @Test
    fun `a recomposition of the caller after the load completes does not load again`() =
        runComposeSwingTest {
            val parse = HeldParse()
            try {
                var unrelated by mutableStateOf(0)
                var loaded: SVGDocument? = null
                setContent {
                    // Read so this composable recomposes, without changing anything the load is keyed on.
                    unrelated.toString()
                    loaded = rememberSvgDocument(TestSvg.Static.url, parseContext = parse.dispatcher)
                }
                waitUntil { loaded != null }

                unrelated = 1
                awaitIdle()

                verify(exactly = 1) { parse.executor.execute(any()) }
            } finally {
                parse.close()
            }
        }
}

/**
 * Provides a parse context on one background thread whose work can be held. Every load reaches [executor];
 * after [hold], a load stays in [held] until [release] passes it to the thread.
 */
private class HeldParse {
    private val lock = Any()
    private val pending = mutableListOf<Runnable>()
    private var holding = false
    private val thread =
        Executors.newSingleThreadExecutor { task ->
            Thread(task, "held-svg-parse").apply { isDaemon = true }
        }

    val executor: Executor =
        mockk {
            every { execute(any()) } answers {
                val task = firstArg<Runnable>()
                synchronized(lock) { if (holding) pending += task else thread.execute(task) }
            }
        }

    val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    val held: Int get() = synchronized(lock) { pending.size }

    fun hold() {
        synchronized(lock) { holding = true }
    }

    fun release() {
        synchronized(lock) {
            holding = false
            pending.forEach(thread::execute)
            pending.clear()
        }
    }

    fun awaitDrained() {
        thread.submit {}.get()
    }

    fun close() {
        thread.shutdownNow()
    }
}
