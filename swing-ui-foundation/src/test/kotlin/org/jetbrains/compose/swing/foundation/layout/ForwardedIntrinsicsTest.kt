package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JComponent
import javax.swing.JTextArea
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * An intrinsic read that passes through code written against the public API - a [LayoutModifierNode] or a
 * [MeasurePolicy] reading its child each intrinsic read with the same arguments - is computed as the built-in tree
 * without
 * that code performs it. Each case lays a tree out twice, with the forwarding code and without it, and expects the same
 * bounds, a container as tall as its content, a leaf at the height it needs at the width it is placed at, and the
 * content of a container leaf within it at its own ratio.
 */
class ForwardedIntrinsicsTest {
    @Test
    fun fillWidthOffersSurviveForwardingInsideAndOutsideTheModifier() {
        for (fraction in listOf(1f, 0.5f)) {
            for (outside in listOf(false, true)) {
                assertOffers(
                    OfferScenario("fill $fraction, outside $outside") { outer, leaf, forwarding ->
                        Box(modifier = outer) {
                            val fill = SwingModifier.fillMaxWidth(fraction)
                            leaf(if (outside) forwarding then fill else fill then forwarding)
                        }
                    },
                )
            }
        }
    }

    @Test
    fun weightedWidthOffersSurviveForwarding() =
        assertOffers(
            OfferScenario("row weighted share") { outer, leaf, forwarding ->
                Row(modifier = outer) { leaf(SwingModifier.weight(1f) then forwarding) }
            },
            OfferScenario("column weighted share") { outer, leaf, forwarding ->
                Column(modifier = outer) { leaf(SwingModifier.weight(1f).fillMaxWidth() then forwarding) }
            },
        )

    @Test
    fun baselineAlignedWidthOffersSurviveForwarding() =
        assertOffers(
            OfferScenario("baseline aligned share") { outer, leaf, forwarding ->
                Row(modifier = outer) {
                    leaf(SwingModifier.weight(1f).alignByBaseline() then forwarding)
                    Label("Side", modifier = SwingModifier.alignByBaseline())
                }
            },
        )

    @Test
    fun propagatedMinimumWidthOffersSurviveForwarding() =
        assertOffers(
            OfferScenario("propagated minimum") { outer, leaf, forwarding ->
                Box(modifier = outer, propagateMinConstraints = true) { leaf(forwarding) }
            },
        )

    @Test
    fun aConstrainedPanelsDefaultPolicyOffersTheLeafItsWidthThroughForwardingModifiers() =
        assertOffers(
            OfferScenario("public panel content") { outer, leaf, forwarding ->
                val panel = remember { ConstrainedPanel() }
                SwingNode(factory = { panel }, modifier = outer)
                val context = rememberCompositionContext()
                DisposableEffect(panel) {
                    val content = panel.setContent(context) { leaf(forwarding) }
                    onDispose { content.dispose() }
                }
            },
        )

    @Test
    fun nestedWidthOffersSurviveForwarding() {
        val nested: Map<String, @Composable (SwingModifier, @Composable () -> Unit) -> Unit> =
            mapOf(
                "propagating box" to { modifier, content ->
                    Box(modifier = modifier, propagateMinConstraints = true) { content() }
                },
                "propagating box with preferred width" to { modifier, content ->
                    Box(modifier = modifier, propagateMinConstraints = true) {
                        Box(modifier = SwingModifier.width(WIDTH))
                        content()
                    }
                },
                "column" to { modifier, content -> Column(modifier = modifier) { content() } },
            )
        for ((nestedName, container) in nested) {
            assertOffers(
                OfferScenario("$nestedName filling box") { outer, leaf, forwarding ->
                    Box(modifier = outer) { container(SwingModifier.fillMaxWidth()) { leaf(forwarding) } }
                },
                OfferScenario("$nestedName filling column") { outer, leaf, forwarding ->
                    Column(modifier = outer) { container(SwingModifier.fillMaxWidth()) { leaf(forwarding) } }
                },
                OfferScenario("$nestedName row share") { outer, leaf, forwarding ->
                    Row(modifier = outer) { container(SwingModifier.weight(1f)) { leaf(forwarding) } }
                },
            )
        }
    }

    @Test
    fun requiredWidthOffersSurviveForwarding() {
        val containers: Map<String, @Composable (SwingModifier, @Composable ConstrainedScope.() -> Unit) -> Unit> =
            mapOf(
                "a box" to { modifier, content -> Box(modifier = modifier) { content() } },
                "a column" to { modifier, content -> Column(modifier = modifier) { content() } },
                "a row" to { modifier, content -> Row(modifier = modifier) { content() } },
            )
        for ((containerName, container) in containers) {
            assertOffers(
                OfferScenario("required fixed width in $containerName") { outer, leaf, forwarding ->
                    container(outer) { leaf(SwingModifier.requiredWidth(width = 200) then forwarding) }
                },
            )
            val minimums: Map<String, ConstrainedScope.() -> SwingModifier> =
                mapOf(
                    "requiredWidthIn" to { SwingModifier.requiredWidthIn(min = 200) },
                    "requiredSizeIn" to { SwingModifier.requiredSizeIn(minWidth = 200) },
                )
            for ((name, minimum) in minimums) {
                assertOffers(
                    OfferScenario("$name minimum in $containerName", stockOnly = true) { outer, leaf, forwarding ->
                        container(outer) { leaf(minimum() then forwarding) }
                    },
                )
            }
        }
    }

    @Test
    fun boundedWidthOffersSurviveForwarding() {
        val bounds: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "sizeIn minimum" to { SwingModifier.sizeIn(minWidth = 200) },
                "requiredWidthIn explicit minimum" to { SwingModifier.requiredWidthIn(min = 0, max = WIDTH) },
                "requiredSizeIn explicit minimum" to { SwingModifier.requiredSizeIn(minWidth = 0, maxWidth = WIDTH) },
                "requiredWidthIn maximum" to { SwingModifier.requiredWidthIn(max = WIDTH) },
            )
        for ((name, bound) in bounds) {
            assertOffers(
                OfferScenario(name) { outer, leaf, forwarding ->
                    Box(modifier = outer, propagateMinConstraints = true) { leaf(bound() then forwarding) }
                },
            )
        }
    }

    private fun assertOffers(vararg scenarios: OfferScenario) {
        for (size in IntrinsicSize.entries) {
            for (scenario in scenarios) {
                val leaves = if (scenario.stockOnly) StockLeaves else Leaves
                val forwardings =
                    if (scenario.stockOnly) listOf(Forwarding.Unchanged, Forwarding.Stacked) else Forwarding.entries
                for ((leafName, leaf) in leaves) {
                    for (forwarding in forwardings) {
                        assertForwardingKeepsTheLayout(
                            "$size, ${scenario.name}, $leafName, $forwarding",
                            size,
                            forwarding.trailing,
                        ) { forwarded ->
                            scenario.content(
                                this,
                                SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                                leaf,
                                with(forwarding) { SwingModifier.modifier(forwarded) },
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aModifierDroppingTheMinimumHasTheSamePreferencesAsTheRequiredWidthThatDropsIt() {
        for (size in IntrinsicSize.entries) {
            for ((leafName, leaf) in Leaves) {
                assertForwardingKeepsTheLayout("$size, $leafName", size, trailing = 0) { forwarded ->
                    Box(
                        modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                        propagateMinConstraints = true,
                    ) {
                        leaf(
                            if (forwarded) {
                                MinimumDroppingElement
                            } else {
                                SwingModifier.requiredWidthIn(
                                    min = 0,
                                    max = WIDTH,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun anOfferFromANegativeWidthIsRejected() {
        val negativeWidthOfferPolicy =
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints,
                ): MeasureResult = layout(constraints.minWidth, constraints.minHeight) {}

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int,
                ): Int = measurables.single().withOfferedMinWidth(-1).minIntrinsicHeight(width)
            }
        runComposeSwingTest {
            setContent {
                Layout(negativeWidthOfferPolicy, SwingModifier.testTag(CONTAINER_TAG)) { Label("Leaf") }
            }
            awaitIdle()
            val container = onNodeWithTag(CONTAINER_TAG).fetch<JComponent>() as Constrainable
            assertFailsWith<IllegalArgumentException> { container.minIntrinsicHeight(WIDTH) }
        }
    }

    @Test
    fun aLayoutHasTheSamePreferencesAsTheBoxThatMeasuresItsChildAlike() {
        val offers: Map<String, ConstrainedScope.() -> SwingModifier> =
            mapOf(
                "no offer" to { SwingModifier },
                "fillMaxWidth()" to { SwingModifier.fillMaxWidth() },
            )
        for (size in IntrinsicSize.entries) {
            for (policy in CustomPolicy.entries) {
                for ((offerName, offer) in offers) {
                    for ((leafName, leaf) in Leaves) {
                        val case = "$size, $leafName, $offerName, $policy"
                        assertForwardingKeepsTheLayout(case, size, trailing = 0) { forwarded ->
                            val modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)
                            if (forwarded) {
                                Layout(measurePolicy = policy.policy, modifier = modifier) { leaf(offer()) }
                            } else {
                                Box(modifier = modifier, propagateMinConstraints = policy.propagatesMinimum) {
                                    leaf(offer())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aDirectIntrinsicReadCarriesNoOfferSoALayoutUsesNoMinimum() {
        for (size in IntrinsicSize.entries) {
            for ((leafName, leaf) in NarrowLeaf) {
                val case = "$size, $leafName"
                val modifier: ConstrainedScope.() -> SwingModifier = {
                    SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size)
                }
                val relaxed =
                    laidOut(size, CONTAINER_TAG, LEAF_TAG) {
                        Box(modifier = modifier(), propagateMinConstraints = false) { leaf(SwingModifier) }
                    }
                val forwarded =
                    laidOut(size, CONTAINER_TAG, LEAF_TAG) {
                        Layout(ForwardingPolicy(keepsMinimum = true, passesOffer = false), modifier()) {
                            leaf(SwingModifier)
                        }
                    }
                assertEquals(
                    relaxed.container,
                    forwarded.container,
                    "$case: a direct intrinsic read carries no offer, " +
                        "so the policy returns results as though the offer had no minimum",
                )
                assertTrue(
                    forwarded.leafNeeds < forwarded.container.height,
                    "$case: the leaf the policy measures under the minimum needs less than the height it returned",
                )
            }
        }
    }

    private companion object {
        const val CONTAINER_TAG = "container"
        const val LEAF_TAG = "leaf"

        /** The width-to-height ratio of a container leaf's content. */
        const val CONTENT_RATIO = 2f

        /**
         * A stock leaf that prefers less than [WIDTH] and whose height follows the width it holds, so a minimum width
         * offered to it changes its height.
         */
        val NarrowLeaf: Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf(
                "a narrow wrapping widget" to { modifier ->
                    SwingNode(
                        factory = { NarrowWrappingComponent() },
                        modifier = SwingModifier.testTag(LEAF_TAG) then modifier,
                    )
                },
            )

        /** Stock leaves whose height follows their width, tagged [LEAF_TAG] and declared with the modifier given. */
        val StockLeaves: Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf<String, @Composable (SwingModifier) -> Unit>(
                "a wrapping text area" to { modifier ->
                    SwingNode(
                        factory = {
                            JTextArea(WRAPPING_TEXT).apply {
                                columns = 10
                                lineWrap = true
                                wrapStyleWord = true
                            }
                        },
                        modifier = SwingModifier.testTag(LEAF_TAG) then modifier,
                    )
                },
                "an HTML label" to { modifier ->
                    Label("<html>$WRAPPING_TEXT</html>", modifier = SwingModifier.testTag(LEAF_TAG) then modifier)
                },
            ) + NarrowLeaf

        /** The [StockLeaves], and a container whose one child's height follows its width. */
        val Leaves: Map<String, @Composable (SwingModifier) -> Unit> =
            StockLeaves +
                mapOf(
                    "a nested column" to { modifier ->
                        Column(modifier = SwingModifier.testTag(LEAF_TAG) then modifier) {
                            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(CONTENT_RATIO))
                        }
                    },
                )

        /**
         * Lays [scene], whose container reads its [size] intrinsic height, out without and with forwarding code, and
         * asserts both place the container and the leaf alike, that the container is as tall as the leaf plus the
         * [trailing] extent the forwarding adds below it, that the leaf takes its [size] height at its width, and that
         * a container leaf's content keeps its ratio inside the leaf.
         */
        fun assertForwardingKeepsTheLayout(
            case: String,
            size: IntrinsicSize,
            trailing: Int,
            scene: @Composable ColumnScope.(forwarded: Boolean) -> Unit,
        ) {
            val builtIn = laidOut(size, CONTAINER_TAG, LEAF_TAG) { scene(false) }
            val forwarded = laidOut(size, CONTAINER_TAG, LEAF_TAG) { scene(true) }

            assertEquals(builtIn, forwarded, "$case: the tree lays out as it does without the forwarding code")
            assertEquals(
                forwarded.leaf.y + forwarded.leaf.height + trailing,
                forwarded.container.height,
                "$case: the container is as tall as its content, with no gap below it",
            )
            assertEquals(
                forwarded.leafNeeds,
                forwarded.leaf.height,
                "$case: the leaf takes the height it needs at the width it is placed at, so nothing is cut off",
            )
            val content = forwarded.content ?: return
            assertEquals(
                (content.width / CONTENT_RATIO).roundToInt(),
                content.height,
                "$case: the leaf's content keeps its ratio",
            )
            assertTrue(
                content.y >= 0 && content.y + content.height <= forwarded.leaf.height,
                "$case: the leaf's content lies within the leaf",
            )
        }
    }
}

private class OfferScenario(
    val name: String,
    val stockOnly: Boolean = false,
    val content: @Composable ConstrainedScope.(
        outer: SwingModifier,
        leaf: @Composable (SwingModifier) -> Unit,
        forwarding: SwingModifier,
    ) -> Unit,
)

/**
 * A modifier written against the public API that reads its child for each intrinsic read, and its built-in twin, which
 * reserve [trailing] below the child.
 */
private enum class Forwarding(
    val trailing: Int,
) {
    /** A [ForwardingNode], where the built-in tree declares nothing. */
    Unchanged(0),

    /** Two [ForwardingNode]s, where the built-in tree declares nothing. */
    Stacked(0),

    /** A [ForwardingNode], where the built-in tree declares [padding] of [PADDING]. */
    PaddingLike(PADDING),
    ;

    /** The forwarding modifier where [forwarded], and the built-in one otherwise. */
    context(scope: ConstrainedScope)
    fun SwingModifier.modifier(forwarded: Boolean): SwingModifier =
        when (this@Forwarding) {
            Forwarding.Unchanged -> if (forwarded) (this then ForwardingElement) else this
            Forwarding.Stacked -> if (forwarded) (this then ForwardingElement then ForwardingElement) else this
            Forwarding.PaddingLike -> if (forwarded) (this then PaddingLikeElement) else padding(PADDING)
        }
}

private const val PADDING = 4

private data object ForwardingElement : LayoutModifierNodeElement<ForwardingNode>() {
    override fun create(): ForwardingNode = ForwardingNode()

    override fun update(node: ForwardingNode): Unit = Unit
}

private data object PaddingLikeElement : LayoutModifierNodeElement<ForwardingNode>() {
    override fun create(): ForwardingNode = ForwardingNode(inset = PADDING)

    override fun update(node: ForwardingNode): Unit = Unit
}

/**
 * Reserves [PADDING] along every edge of its child, as [padding] does, and returns each intrinsic read by forwarding
 * its child at the extent less the padding and adding the padding back.
 */
private class ForwardingNode(
    private val inset: Int = 0,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable =
            measurable.measure(
                constraints.offset(horizontal = -2 * inset, vertical = -2 * inset),
            )
        val width = (placeable.width + 2 * inset).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeable.height + 2 * inset).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) { placeable.place(inset, inset) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.minIntrinsicWidth(height.lessPadding()) + 2 * inset

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int = measurable.maxIntrinsicWidth(height.lessPadding()) + 2 * inset

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.minIntrinsicHeight(width.lessPadding()) + 2 * inset

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.maxIntrinsicHeight(width.lessPadding()) + 2 * inset

    /** This extent less the padding on both of its edges, never below zero; an unbounded extent stays unbounded. */
    private fun Int.lessPadding(): Int = if (this == Constraints.Infinity) this else (this - 2 * inset).coerceAtLeast(0)
}

/**
 * A [MeasurePolicy] written against the public API, and whether the [Box] that measures its child alike propagates its
 * minimum.
 */
private enum class CustomPolicy(
    val policy: MeasurePolicy,
    val propagatesMinimum: Boolean,
) {
    /**
     * Measures its child under the constraints it is given less their minimum, and reads it each preference read with
     * the specified arguments.
     */
    Forwarding(ForwardingPolicy(keepsMinimum = false, passesOffer = false), propagatesMinimum = false),

    /**
     * Measures its child under the constraints it is given, and reads it each preference read with the specified
     * arguments, its height under the
     * offer it is read under.
     */
    ForwardingTheOffer(ForwardingPolicy(keepsMinimum = true, passesOffer = true), propagatesMinimum = true),

    /** Measures its child under the constraints it is given, and returns a result its intrinsic reads by default. */
    Measuring(MeasuringPolicy(dropsMinimum = false), propagatesMinimum = true),

    /** Measures its child under the constraints it is given less their minimum, and returns results by default. */
    MeasuringWithoutMinimum(MeasuringPolicy(dropsMinimum = true), propagatesMinimum = false),
}

/** Measures its one child under the constraints it is given, or under them less their minimum where [dropsMinimum]. */
private class MeasuringPolicy(
    private val dropsMinimum: Boolean,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val offered = if (dropsMinimum) constraints.copy(minWidth = 0, minHeight = 0) else constraints
        val placeable = measurables.single().measure(offered)
        val width = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = placeable.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) { placeable.place(0, 0) }
    }
}

/**
 * Measures its one child under the constraints it is given, less their minimum unless it [keepsMinimum], and reads it
 * each intrinsic read, its height under the offer it is read under where it [passesOffer].
 */
private class ForwardingPolicy(
    keepsMinimum: Boolean,
    private val passesOffer: Boolean,
) : MeasurePolicy by MeasuringPolicy(dropsMinimum = !keepsMinimum) {
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.single().minIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.single().maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.single().withHeightPreferenceOffer().minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.single().withHeightPreferenceOffer().maxIntrinsicHeight(width)

    context(scope: IntrinsicMeasureScope)
    private fun IntrinsicMeasurable.withHeightPreferenceOffer(): IntrinsicMeasurable =
        if (passesOffer) withOfferedMinWidth(scope.offeredMinWidth) else this
}

private data object MinimumDroppingElement : LayoutModifierNodeElement<MinimumDroppingNode>() {
    override fun create(): MinimumDroppingNode = MinimumDroppingNode()

    override fun update(node: MinimumDroppingNode): Unit = Unit
}

/**
 * Measures its child under the constraints it is given less their minimum width, and reads its child for each height
 * intrinsic read under no width offer.
 */
private class MinimumDroppingNode : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        return layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.withOfferedMinWidth(0).minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int = measurable.withOfferedMinWidth(0).maxIntrinsicHeight(width)
}

private const val WIDTH = 320
