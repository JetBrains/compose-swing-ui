package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.samples.widgets.ShowcaseShell
import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.samples.widgets.showcaseSections
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.onAncestors
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performMouseDrag
import org.jetbrains.compose.swing.test.interaction.performMouseEnter
import org.jetbrains.compose.swing.test.interaction.performMouseExit
import org.jetbrains.compose.swing.test.interaction.performMouseMove
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JCheckBox
import javax.swing.JColorChooser
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JSlider
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FoundationGraphicsSectionTest {
    @Test
    fun paddingCheckboxReservesSpaceAndRestoresTheSurfaceWhenDisabled() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val original = onNodeWithTag(DECORATION_TAG).captureToImage()
            val originalInsets = onNodeWithTag(DECORATION_TAG).fetch<JComponent>().insets
            val stageBounds = layoutBounds(DECORATION_STAGE_TAG)
            val padded = onNodeWithText("Padding: 8 px").fetch<JCheckBox>()
            assertFalse(padded.isSelected, "padding starts off")

            onNodeWithText("Padding: 8 px").performClick()
            assertTrue(padded.isSelected, "clicking turns padding on")
            val paddedSurface = onNodeWithTag(DECORATION_TAG).captureToImage()
            val paddedInsets = onNodeWithTag(DECORATION_TAG).fetch<JComponent>().insets
            assertEquals(originalInsets, paddedInsets, "padding leaves paint outsets unchanged")
            assertEquals(original.width - 16, paddedSurface.width, "padding reserves 8 px at both horizontal edges")
            assertEquals(original.height - 16, paddedSurface.height, "padding reserves 8 px at both vertical edges")
            assertEquals(
                stageBounds,
                layoutBounds(DECORATION_STAGE_TAG),
                "padding leaves the stage's layout bounds unchanged",
            )

            onNodeWithText("Padding: 8 px").performClick()
            assertFalse(padded.isSelected, "clicking again turns padding off")
            val restored = onNodeWithTag(DECORATION_TAG).captureToImage()
            assertImagesPixelPerfect(original, restored)
        }

    @Test
    fun changingShadowRadiusChangesMeasuredShadowSpread() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            onNodeWithTag(DECORATION_SHADOW_X_TAG).fetch<JSlider>().value = 0
            onNodeWithTag(DECORATION_SHADOW_Y_TAG).fetch<JSlider>().value = 0
            onNodeWithTag(DECORATION_SHADOW_RADIUS_TAG).fetch<JSlider>().value = 0
            awaitIdle()
            val sharp = onNodeWithTag(DECORATION_TAG).captureToImage()

            val shadowSlider = onNodeWithTag(DECORATION_SHADOW_RADIUS_TAG).fetch<JSlider>()
            shadowSlider.valueIsAdjusting = true
            shadowSlider.value = 18
            awaitIdle()
            val wide = onNodeWithTag(DECORATION_TAG).captureToImage()
            shadowSlider.valueIsAdjusting = false
            awaitIdle()

            assertTrue(
                wide.width >= sharp.width + 18 * 2 && wide.height >= sharp.height + 18 * 2,
                "radius 18 should provide captured shadow outsets around the baseline surface",
            )
            val sharpPainted =
                assertNotNull(
                    differingPixelBounds(blankLike(sharp), sharp),
                    "the sharp-radius capture paints something",
                )
            val widePainted =
                assertNotNull(
                    differingPixelBounds(blankLike(wide), wide),
                    "the wide-radius capture paints something",
                )
            assertTrue(
                widePainted.width >= sharpPainted.width + 18 && widePainted.height >= sharpPainted.height + 18,
                "radius 18 should spread the shadow past the sharp one: sharp=$sharpPainted wide=$widePainted",
            )
        }

    @Test
    fun surfaceBlurSliderIsEnabledByItsCheckboxAndChangesCapturedOutput() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            val blurSlider = onNodeWithTag(DECORATION_BLUR_RADIUS_TAG).fetch<JSlider>()
            assertFalse(blurSlider.isEnabled, "surface blur radius is inactive until surface blur is enabled")

            onNodeWithText("Blur").performClick()
            val enabledSlider = onNodeWithTag(DECORATION_BLUR_RADIUS_TAG).fetch<JSlider>()
            assertTrue(enabledSlider.isEnabled, "surface blur radius becomes interactive when surface blur is enabled")

            val stageBounds = layoutBounds(DECORATION_STAGE_TAG)
            val before = onNodeWithTag(DECORATION_TAG).captureToImage()
            enabledSlider.value = 12
            awaitIdle()
            val after = onNodeWithTag(DECORATION_TAG).captureToImage()
            assertTrue(
                after.width > before.width && after.height > before.height,
                "surface blur expands the captured output for its falloff",
            )
            assertTrue(
                (after.width - before.width) % 2 == 0 &&
                    (after.height - before.height) % 2 == 0,
                "surface blur expands the capture symmetrically",
            )
            val grown =
                after.getSubimage(
                    (after.width - before.width) / 2,
                    (after.height - before.height) / 2,
                    before.width,
                    before.height,
                )
            assertNotNull(
                differingPixelBounds(before, grown),
                "changing the enabled surface blur radius changes captured output",
            )
            assertEquals(
                stageBounds,
                layoutBounds(DECORATION_STAGE_TAG),
                "blur leaves the stage's layout bounds unchanged",
            )
        }

    @Test
    fun petalCountAndRotationIndependentlyRepaintTheCanvas() =
        runComposeSwingTest {
            openSection("Foundation graphics")

            val baseline = onNodeWithTag(CANVAS_TAG).captureToImage()
            onNodeWithTag(CANVAS_PETALS_TAG).fetch<JSlider>().value = 12
            awaitIdle()
            val petals = onNodeWithTag(CANVAS_TAG).captureToImage()
            onNodeWithTag(CANVAS_ROTATION_TAG).fetch<JSlider>().value = 17
            awaitIdle()
            val changed = onNodeWithTag(CANVAS_TAG).captureToImage()
            assertTrue(changed.width > 0 && changed.height > 0, "the captured surface has real size")
            assertNotEquals(
                changed.getRGB(4, 4),
                changed.getRGB(changed.width - 5, changed.height - 5),
                "the captured surface retains its blue gradient background",
            )
            assertNotNull(
                differingPixelBounds(baseline.inside(8), petals.inside(8)),
                "petal count independently changes the illustration",
            )
            assertNotNull(
                differingPixelBounds(petals.inside(8), changed.inside(8)),
                "rotation independently changes the petals",
            )
        }

    @Test
    fun disablingShadowDisablesItsControlsAndRetainsValues() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val tags = listOf(DECORATION_SHADOW_RADIUS_TAG, DECORATION_SHADOW_X_TAG, DECORATION_SHADOW_Y_TAG)
            val values = listOf(18, -8, 12)
            tags.zip(values).forEach { (tag, value) -> onNodeWithTag(tag).fetch<JSlider>().value = value }
            onNodeWithText("Shadow").performClick()
            tags.forEach { assertFalse(onNodeWithTag(it).fetch<JSlider>().isEnabled, "$it is off with the shadow") }
            onNodeWithText("Shadow").performClick()
            tags.zip(values).forEach { (tag, value) ->
                val slider = onNodeWithTag(tag).fetch<JSlider>()
                assertTrue(slider.isEnabled, "$tag is enabled again with the shadow")
                assertEquals(value, slider.value, "$tag keeps its value while the shadow is off")
            }
        }

    @Test
    fun opacityReachesTheBackgroundBorderAndArtwork() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithText("Shadow").performClick()
            val opaque = onNodeWithTag(DECORATION_TAG).captureToImage()
            onNodeWithTag(DECORATION_OPACITY_TAG).fetch<JSlider>().value = 40
            awaitIdle()
            val faded = onNodeWithTag(DECORATION_TAG).captureToImage()
            for ((x, y) in listOf(20 to 60, 1 to 60, 90 to 90)) {
                assertTrue(
                    faded.getRGB(x, y) ushr 24 < opaque.getRGB(x, y) ushr 24,
                    "opacity must reach surface, border and artwork ($x, $y)",
                )
            }
        }

    @Test
    fun blurReachesTheSurfaceEdgeAndBorder() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithText("Shadow").performClick()
            val sharp = onNodeWithTag(DECORATION_TAG).captureToImage()
            onNodeWithText("Blur").performClick()
            onNodeWithTag(DECORATION_BLUR_RADIUS_TAG).fetch<JSlider>().value = 16
            awaitIdle()
            val blurred = onNodeWithTag(DECORATION_TAG).captureToImage()
            assertNotEquals(sharp.getRGB(1, 60), blurred.getRGB(1, 60), "the border is inside the blur")
            assertNotEquals(sharp.getRGB(20, 60), blurred.getRGB(20, 60), "the gradient surface is inside the blur")
        }

    @Test
    fun paintOrderChangesTheSubstantialOverlappingRegion() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val overlayOnTop = onNodeWithTag(DRAW_MODIFIERS_TAG).captureToImage()
            onNodeWithText("Overlay on top").performClick()
            val contentOnTop = onNodeWithTag(DRAW_MODIFIERS_TAG).captureToImage()
            val overlap =
                orderTileBounds(overlayOnTop.width, overlay = false)
                    .createIntersection(orderTileBounds(overlayOnTop.width, overlay = true))
            assertEquals(
                overlap.bounds,
                differingPixelBounds(overlayOnTop, contentOnTop),
                "the paint order must change exactly the area where the tiles overlap",
            )
        }

    @Test
    fun shapeAndEffectExtremesKeepThePreviewBoundsStable() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val before = layoutBounds(DECORATION_STAGE_TAG)
            val decorated = layoutBounds(DECORATION_TAG)
            onNodeWithTag(DECORATION_SHAPE_TAG).fetch<JComboBox<*>>().selectedItem = "Circle"
            onNodeWithTag(DECORATION_SHADOW_RADIUS_TAG).fetch<JSlider>().value = 24
            onNodeWithTag(DECORATION_SHADOW_X_TAG).fetch<JSlider>().value = -16
            onNodeWithTag(DECORATION_SHADOW_Y_TAG).fetch<JSlider>().value = 16
            onNodeWithText("Blur").performClick()
            onNodeWithTag(DECORATION_BLUR_RADIUS_TAG).fetch<JSlider>().value = 16
            awaitIdle()
            assertEquals(
                before,
                layoutBounds(DECORATION_STAGE_TAG),
                "shape and effect extremes must not change the stage's layout bounds",
            )
            assertEquals(decorated, layoutBounds(DECORATION_TAG), "effects must not change the decorated layout bounds")
        }

    @Test
    fun controlsFitTheNarrowGalleryViewport() = graphicsControlsFit(Dimension(640, 680))

    @Test
    fun controlsFitTheDefaultGalleryViewport() = graphicsControlsFit(Dimension(960, 680))

    @Test
    fun decorationPreviewAndControlsShareTheNarrowViewport() =
        decorationPreviewAndControlsShareAViewport(Dimension(640, 680))

    @Test
    fun decorationPreviewAndControlsShareTheDefaultViewport() =
        decorationPreviewAndControlsShareAViewport(Dimension(960, 680))

    @Test
    fun placementClipCutsOverflowAgainstItsVisibleBoundary() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val center = placementCanvasCenter()
            val unclipped = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            onNodeWithText("Clip to dashed boundary").performClick()
            val clipped = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            val x = center.x - 40
            val outsideY = center.y - 48
            val background = clipped.getRGB(0, 0)
            assertNotEquals(background, unclipped.getRGB(x, outsideY), "the canvas deliberately overflows")
            assertEquals(background, clipped.getRGB(x, outsideY), "clip cuts outside the dashed window")
            assertEquals(
                unclipped.getRGB(x, center.y - 20),
                clipped.getRGB(x, center.y - 20),
                "the interior remains visible",
            )
        }

    @Test
    fun placementOpacityFadesTheSurfaceArtworkAndShadowTogether() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val center = placementCanvasCenter()
            val opaque = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            onNodeWithTag(PLACEMENT_OPACITY_TAG).fetch<JSlider>().value = 40
            awaitIdle()
            val faded = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            val background = opaque.getRGB(0, 0)
            for ((offsetX, offsetY) in listOf(-60 to -25, 0 to -18, 95 to 35)) {
                val x = center.x + offsetX
                val y = center.y + offsetY
                val before = colorDistance(opaque.getRGB(x, y), background)
                val after = colorDistance(faded.getRGB(x, y), background)
                assertTrue(before > 0 && after < before, "the complete child fades at offset $offsetX, $offsetY")
            }
        }

    @Test
    fun placementScaleAndPivotMoveTheWholeSurfaceWithoutChangingStageBounds() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val originalBounds = layoutBounds(PLACEMENT_STAGE_TAG)
            val center = placementCanvasCenter()
            val original = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            onNodeWithTag(PLACEMENT_SCALE_TAG).fetch<JSlider>().value = 60
            awaitIdle()
            val small = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            val x = center.x - 70
            val y = center.y - 25
            assertNotEquals(
                original.getRGB(0, 0),
                original.getRGB(x, y),
                "the artwork differs from the background before scaling",
            )
            assertEquals(
                small.getRGB(0, 0),
                small.getRGB(x, y),
                "scaling shrinks the background as well as the artwork",
            )
            onNodeWithTag(PLACEMENT_PIVOT_TAG).fetch<JSlider>().value = 0
            awaitIdle()
            val leftPivot = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            onNodeWithTag(PLACEMENT_PIVOT_TAG).fetch<JSlider>().value = 100
            awaitIdle()
            val rightPivot = onNodeWithTag(PLACEMENT_STAGE_TAG).captureToImage()
            val box = placementLayerBox()
            val untransformed = Point2D.Double(center.x.toDouble(), center.y - 12.0)
            val amber = original.getRGB(untransformed.x.toInt(), untransformed.y.toInt())
            val leftPoint = TransformOrigin(0f, 0.5f).createTransform(box, 0.6f, 0.6f).transform(untransformed, null)
            val rightPoint = TransformOrigin(1f, 0.5f).createTransform(box, 0.6f, 0.6f).transform(untransformed, null)
            assertTrue(
                colorDistance(leftPivot.getRGB(leftPoint.x.toInt(), leftPoint.y.toInt()), amber) < 40,
                "pivot 0 leaves the amber disc at ${leftPoint.x.toInt()}, ${leftPoint.y.toInt()}",
            )
            assertTrue(
                colorDistance(rightPivot.getRGB(rightPoint.x.toInt(), rightPoint.y.toInt()), amber) < 40,
                "pivot 100 moves the amber disc to ${rightPoint.x.toInt()}, ${rightPoint.y.toInt()}",
            )
            assertEquals(originalBounds, layoutBounds(PLACEMENT_STAGE_TAG))
        }

    @Test
    fun gradientPresetRecolorsTheSurfaceAndEveryBrushTile() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val surface = onNodeWithTag(DECORATION_TAG).captureToImage()
            val tiles = captureBrushTiles()
            val swatch = onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 0).captureToImage()

            onNodeWithTag(GRADIENT_PRESET_TAG).fetch<JComboBox<*>>().selectedItem = "Ocean"
            awaitIdle()

            assertNotNull(
                differingPixelBounds(surface, onNodeWithTag(DECORATION_TAG).captureToImage()),
                "the preset recolors the surface",
            )
            captureBrushTiles().forEach { (name, tile) ->
                assertNotNull(differingPixelBounds(tiles.getValue(name), tile), "the $name brush takes the preset")
            }
            val oceanSwatch = onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 0).captureToImage()
            assertNotNull(differingPixelBounds(swatch, oceanSwatch), "the preset recolors the first swatch")
            val horizontal = onNodeWithTag(BRUSH_TILE_TAG_PREFIX + "horizontal").captureToImage()
            assertTrue(
                colorDistance(
                    horizontal.getRGB(4, horizontal.height / 2),
                    oceanSwatch.getRGB(oceanSwatch.width / 2, oceanSwatch.height / 2),
                ) < 24,
                "the horizontal brush starts at the preset's first stop",
            )
        }

    @Test
    fun clickingABrushTileFillsTheSurfaceWithItsStyleUntilReset() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val horizontalTile = onNodeWithTag(BRUSH_TILE_TAG_PREFIX + "horizontal")
            assertEquals(
                Cursor.HAND_CURSOR,
                horizontalTile.fetch<JComponent>().cursor.type,
                "a brush tile shows a hand cursor",
            )
            // A tile's ring is paint outsets, which resizes the tile, so each tile is compared through its slot.
            val horizontalSlot = horizontalTile.onParent()
            val selectedTile = horizontalSlot.captureToImage()
            var surface = onNodeWithTag(DECORATION_TAG).captureToImage()
            val original = surface
            assertBrushDirection("horizontal", original)

            for (name in BRUSH_TILE_NAMES.drop(1)) {
                val tile = onNodeWithTag(BRUSH_TILE_TAG_PREFIX + name)
                val slot = tile.onParent()
                val unselected = slot.captureToImage()
                tile.performClick()
                assertNotNull(differingPixelBounds(unselected, slot.captureToImage()), "the $name tile gains the ring")
                val styled = onNodeWithTag(DECORATION_TAG).captureToImage()
                assertNotNull(differingPixelBounds(surface, styled), "the $name tile restyles the surface")
                assertBrushDirection(name, styled)
                surface = styled
            }
            assertNotNull(
                differingPixelBounds(selectedTile, horizontalSlot.captureToImage()),
                "a tile loses its ring once another is selected",
            )

            onNodeWithText("Reset decoration").performClick()
            assertImagesPixelPerfect(original, onNodeWithTag(DECORATION_TAG).captureToImage())
            assertImagesPixelPerfect(selectedTile, horizontalSlot.captureToImage())
        }

    /** Walks the Tab order in a showing window: Swing's focus traversal policies build no cycle for a hidden root. */
    @Test
    fun brushTilesTakeTheKeyboardFocusAndSelectOnSpaceOrEnter() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            setContent {
                Window(onCloseRequest = {}, state = WindowState(size = Dimension(1200, 900)), title = FOCUS_WINDOW) {
                    ShowcaseShell()
                }
            }
            val window = onWindowWithTitle(FOCUS_WINDOW)
            window.onNode(SwingMatcher.hasAccessibleName("Sections")).fetch<JList<*>>().selectedIndex =
                showcaseSections.indexOfFirst { it.title == "Foundation graphics" }
            awaitIdle()
            val original = window.onNodeWithTag(DECORATION_TAG).captureToImage()

            val tiles = BRUSH_TILE_NAMES.map { window.onNodeWithTag(BRUSH_TILE_TAG_PREFIX + it).fetch<JComponent>() }
            val root = tiles.first().focusCycleRootAncestor
            val reached =
                generateSequence<Component>(tiles.first()) { root.focusTraversalPolicy.getComponentAfter(root, it) }
                    .take(tiles.size + 1)
                    .toSet()
            assertTrue(reached.containsAll(tiles), "Tab reaches every tile")

            window.onNodeWithTag(BRUSH_TILE_TAG_PREFIX + "vertical").performKeyPress(KeyEvent.VK_SPACE)
            assertBrushDirection("vertical", window.onNodeWithTag(DECORATION_TAG).captureToImage())

            window.onNodeWithTag(BRUSH_TILE_TAG_PREFIX + "horizontal").performKeyPress(KeyEvent.VK_ENTER)
            assertImagesPixelPerfect(original, window.onNodeWithTag(DECORATION_TAG).captureToImage())
        }

    @Test
    fun starPointsSliderShowsOnlyForTheStarAndKeepsTheRingsWidth() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val shape = onNodeWithTag(DECORATION_SHAPE_TAG).fetch<JComboBox<*>>()
            val ringsWidth = onNodeWithTag(DECORATION_RINGS_TAG).fetch<JSlider>().width
            onNodeWithTag(DECORATION_STAR_POINTS_TAG).assertDoesNotExist()

            shape.selectedItem = "Star"
            awaitIdle()
            onNodeWithText("Star points: 5").assertExists()
            assertEquals(
                ringsWidth,
                onNodeWithTag(DECORATION_RINGS_TAG).fetch<JSlider>().width,
                "showing the star points keeps the rings slider's width",
            )

            shape.selectedItem = "Hexagon"
            awaitIdle()
            onNodeWithTag(DECORATION_STAR_POINTS_TAG).assertDoesNotExist()
            assertEquals(
                ringsWidth,
                onNodeWithTag(DECORATION_RINGS_TAG).fetch<JSlider>().width,
                "hiding the star points keeps the rings slider's width",
            )
        }

    @Test
    fun middleStopPositionMovesTheGradientInsideEveryTile() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val before = captureBrushTiles()
            onNodeWithTag(GRADIENT_MIDDLE_TAG).fetch<JSlider>().value = 85
            awaitIdle()
            onNodeWithText("Middle: 85%").assertExists()
            captureBrushTiles().forEach { (name, tile) ->
                assertNotNull(
                    differingPixelBounds(before.getValue(name), tile),
                    "the middle stop moves in the $name brush",
                )
            }
        }

    @Test
    fun middleStopPositionIsIndependentOfTheColorPreset() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val presets = onNodeWithTag(GRADIENT_PRESET_TAG).fetch<JComboBox<*>>()
            val middle = onNodeWithTag(GRADIENT_MIDDLE_TAG).fetch<JSlider>()

            middle.value = 70
            awaitIdle()
            assertEquals("Sunset", presets.selectedItem, "moving the middle stop keeps the preset selected")

            presets.selectedItem = "Ocean"
            awaitIdle()
            assertEquals(70, middle.value, "picking a preset keeps the middle stop where it is")
        }

    @Test
    fun swatchDialogRecolorsOnlyItsStopAndClosesOnRequest() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            openSection("Foundation graphics")
            val swatches = (0..2).map { onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + it).captureToImage() }
            val surface = onNodeWithTag(DECORATION_TAG).captureToImage()

            onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 1).performClick()
            val dialog = onWindowWithTitle("Middle color")
            dialog.onNodeWithTag(GRADIENT_CHOOSER_TAG).fetch<JColorChooser>().color = Color.GREEN
            awaitIdle()

            assertNotNull(
                differingPixelBounds(swatches[1], onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 1).captureToImage()),
                "the middle swatch takes the picked color",
            )
            assertImagesPixelPerfect(swatches[0], onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 0).captureToImage())
            assertImagesPixelPerfect(swatches[2], onNodeWithTag(GRADIENT_SWATCH_TAG_PREFIX + 2).captureToImage())
            assertNotNull(
                differingPixelBounds(surface, onNodeWithTag(DECORATION_TAG).captureToImage()),
                "the surface takes the picked color",
            )

            val window = dialog.fetch<JDialog>()
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
            awaitIdle()
            onWindowWithTitle("Middle color").assertDoesNotExist()
        }

    @Test
    fun colorChooserReportsItsSelectionAndFollowsTheDeclaredColor() =
        runComposeSwingTest {
            var declared by mutableStateOf(Color.BLUE)
            val reported = mutableListOf<Color>()
            setContent {
                GradientColorChooser(declared) {
                    reported += it
                    declared = it
                }
            }
            val chooser = onNodeWithTag(GRADIENT_CHOOSER_TAG).fetch<JColorChooser>()
            assertEquals(Color.BLUE, chooser.color, "the chooser starts at the declared color")

            chooser.color = Color.ORANGE
            awaitIdle()
            assertEquals(listOf(Color.ORANGE), reported, "a user's pick reaches the callback once")

            declared = Color.GREEN
            awaitIdle()
            assertEquals(
                Color.GREEN,
                onNodeWithTag(GRADIENT_CHOOSER_TAG).fetch<JColorChooser>().color,
                "the chooser follows a declared color change",
            )
        }

    @Test
    fun eachExtraRingLeavesAClearGapOutsideTheSurface() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithText("Shadow").performClick()
            onNodeWithTag(DECORATION_RINGS_TAG).fetch<JSlider>().value = 1
            awaitIdle()
            val bounds = onNodeWithTag(DECORATION_TAG).fetch<JComponent>().bounds
            val single = onNodeWithTag(DECORATION_TAG).captureToImage()
            onNodeWithTag(DECORATION_RINGS_TAG).fetch<JSlider>().value = 2
            awaitIdle()
            val double = onNodeWithTag(DECORATION_TAG).captureToImage()

            // The first ring and its gap take 9 pixels, so 6 pixels in lands in the gap.
            val y = single.height / 2
            assertEquals(255, single.getRGB(6, y) ushr 24, "one ring leaves the surface right inside the border")
            assertEquals(0, double.getRGB(6, y) ushr 24, "a second ring leaves a transparent gap")
            assertNotEquals(0, double.getRGB(1, y) ushr 24, "the outer ring is painted at the edge")
            assertEquals(
                bounds,
                onNodeWithTag(DECORATION_TAG).fetch<JComponent>().bounds,
                "the rings paint inside the slot the surface alone took",
            )
        }

    @Test
    fun spotlightDimsTheCanvasAwayFromThePointerAndClearsOnExit() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val canvas = onNodeWithTag(DRAW_MODIFIERS_TAG)
            assertEquals(Cursor.CROSSHAIR_CURSOR, canvas.fetch<JComponent>().cursor.type)
            val plain = canvas.captureToImage()

            canvas.performMouseMove(Point(40, 40))
            val lit = canvas.captureToImage()
            assertEquals(plain.getRGB(40, 40), lit.getRGB(40, 40), "the pointer's surroundings stay lit")
            val far = Point(lit.width - 10, lit.height - 10)
            assertTrue(
                colorDistance(lit.getRGB(far.x, far.y), 0) < colorDistance(plain.getRGB(far.x, far.y), 0),
                "the canvas away from the pointer is dimmed",
            )

            canvas.performMouseExit(Point(40, 40))
            assertImagesPixelPerfect(plain, canvas.captureToImage())
        }

    @Test
    fun spotlightKeepsThePointerAreaSharpAndBlursTheRest() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val canvas = onNodeWithTag(DRAW_MODIFIERS_TAG)
            val plain = canvas.captureToImage()

            canvas.performMouseMove(Point(80, 80))
            val lit = canvas.captureToImage()
            // Around the pointer, across the CONTENT tile's corner, nothing is blurred or tinted.
            assertImagesPixelPerfect(plain.getSubimage(60, 60, 40, 40), lit.getSubimage(60, 60, 40, 40))
            // Past the OVERLAY tile's right edge, far from the pointer, the background is flat until the blur
            // spreads the tile's color into it.
            val edge = orderTileBounds(plain.width, overlay = true).maxX.toInt()
            assertEquals(
                plain.getRGB(edge + 1, 100),
                plain.getRGB(edge + 10, 100),
                "the background past the tile's edge is flat before the pointer moves in",
            )
            assertNotEquals(lit.getRGB(edge + 1, 100), lit.getRGB(edge + 10, 100), "the tile's edge is blurred")
        }

    @Test
    fun dragOutOfTheCanvasLeavesNoSpotlight() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val canvas = onNodeWithTag(DRAW_MODIFIERS_TAG)
            val plain = canvas.captureToImage()

            canvas.performMouseDrag(Point(40, 40), Point(plain.width + 60, plain.height + 60))
            assertImagesPixelPerfect(plain, canvas.captureToImage())
        }

    @Test
    fun hoveringAShadowTileLiftsItsShadowWithAHandCursor() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            val tile = onNodeWithTag(SHADOW_STYLE_TAG_PREFIX + "Elevation")
            assertEquals(Cursor.HAND_CURSOR, tile.fetch<JComponent>().cursor.type)
            val resting = tile.captureToImage()

            tile.performMouseEnter()
            val lifted = tile.captureToImage()
            assertTrue(
                lifted.width > resting.width && lifted.height > resting.height,
                "a lifted tile casts a wider shadow",
            )

            tile.performMouseExit()
            assertImagesPixelPerfect(resting, tile.captureToImage())
        }

    @Test
    fun theTransformedSceneHoldsATextFieldAndAButtonBesideTheCanvas() =
        runComposeSwingTest {
            openSection("Foundation graphics")
            onNodeWithTag(PLACEMENT_FIELD_TAG).assertTextEquals("Select this text")

            onNodeWithTag(PLACEMENT_BUTTON_TAG).performClick()

            onNodeWithTag(PLACEMENT_BUTTON_TAG).assertTextEquals("Pressed 1")
            for (tag in listOf(PLACEMENT_CHILD_TAG, PLACEMENT_FIELD_TAG, PLACEMENT_BUTTON_TAG)) {
                onNodeWithTag(
                    tag,
                ).onAncestors().filter(SwingMatcher.hasTestTag(PLACEMENT_LAYER_TAG)).assertCountEquals(1)
            }
        }
}

private fun colorDistance(
    first: Int,
    second: Int,
): Int = listOf(0, 8, 16).sumOf { shift -> kotlin.math.abs((first shr shift and 255) - (second shr shift and 255)) }

/** The layout bounds of the [PLACEMENT_LAYER_TAG] layer, in the coordinates a capture of the stage uses. */
internal fun ComposeSwingTest.placementLayerBox(): Rectangle2D {
    val stage = onNodeWithTag(PLACEMENT_STAGE_TAG).fetch<JComponent>()
    val layer = onNodeWithTag(PLACEMENT_LAYER_TAG).fetch<JComponent>()
    val local = (layer as Decoratable).decoration.localLayoutBounds(layer)
    val origin = SwingUtilities.convertPoint(layer, local.x, local.y, stage)
    return Rectangle2D.Double(origin.x.toDouble(), origin.y.toDouble(), local.width.toDouble(), local.height.toDouble())
}

private fun graphicsControlsFit(size: Dimension) =
    runComposeSwingTest(rootSize = size) {
        root.layout = BorderLayout()
        openSection("Foundation graphics")
        onNodeWithTag(DECORATION_SHAPE_TAG).fetch<JComboBox<*>>().selectedItem = "Star"
        awaitIdle()
        val tags =
            listOf(
                CANVAS_PETALS_TAG,
                CANVAS_SWEEP_TAG,
                CANVAS_ROTATION_TAG,
                DRAW_SWEEP_TAG,
                DECORATION_SHAPE_TAG,
                GRADIENT_PRESET_TAG,
                GRADIENT_SWATCH_TAG_PREFIX + 2,
                GRADIENT_MIDDLE_TAG,
                DECORATION_OPACITY_TAG,
                DECORATION_BORDER_TAG,
                DECORATION_RINGS_TAG,
                DECORATION_STAR_POINTS_TAG,
                DECORATION_SHADOW_RADIUS_TAG,
                DECORATION_SHADOW_X_TAG,
                DECORATION_SHADOW_Y_TAG,
                DECORATION_BLUR_RADIUS_TAG,
                SHADOW_STYLE_TAG_PREFIX + "Soft",
                PLACEMENT_OPACITY_TAG,
                PLACEMENT_SCALE_TAG,
                PLACEMENT_ROTATION_TAG,
                PLACEMENT_PIVOT_TAG,
            )
        for (tag in tags) {
            val component = onNodeWithTag(tag).fetch<JComponent>()
            component.scrollRectToVisible(Rectangle(0, 0, component.width, component.height))
            awaitIdle()
            val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, component) as JViewport
            val bounds = SwingUtilities.convertRectangle(component.parent, component.bounds, viewport)
            assertTrue(
                bounds.x >= 0 && bounds.maxX <= viewport.width,
                "$tag is horizontally reachable at $size: $bounds",
            )
            assertTrue(bounds.y >= 0 && bounds.maxY <= viewport.height, "$tag is vertically reachable after scrolling")
        }
    }

private fun decorationPreviewAndControlsShareAViewport(size: Dimension) =
    runComposeSwingTest(rootSize = size) {
        root.layout = BorderLayout()
        openSection("Foundation graphics")
        val stage = onNodeWithTag(DECORATION_STAGE_TAG).fetch<JComponent>()
        val lastControl = onNodeWithTag(DECORATION_SHADOW_Y_TAG).fetch<JComponent>()
        val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, stage) as JViewport
        val view = viewport.view as JComponent
        view.scrollRectToVisible(
            SwingUtilities
                .convertRectangle(stage.parent, stage.bounds, view)
                .union(SwingUtilities.convertRectangle(lastControl.parent, lastControl.bounds, view)),
        )
        awaitIdle()
        for (tag in listOf(DECORATION_STAGE_TAG, BRUSH_TILES_TAG, DECORATION_SHADOW_Y_TAG)) {
            val component = onNodeWithTag(tag).fetch<JComponent>()
            val bounds = SwingUtilities.convertRectangle(component.parent, component.bounds, viewport)
            assertTrue(
                bounds.y >= 0 && bounds.maxY <= viewport.height,
                "$tag is visible together with the preview at $size: $bounds in ${viewport.size}",
            )
        }
    }

// Samples the surface away from its gloss: across the lower half, down the right half, and outward from the
// center. Each gradient style changes color along its own direction only; the repeating styles have no single
// direction and pass unchecked.
private fun assertBrushDirection(
    name: String,
    surface: BufferedImage,
) {
    fun at(
        x: Double,
        y: Double,
    ) = surface.getRGB((surface.width * x).toInt(), (surface.height * y).toInt())
    val across = colorDistance(at(0.25, 0.75), at(0.75, 0.75))
    val down = colorDistance(at(0.75, 0.25), at(0.75, 0.75))
    val outward = colorDistance(at(0.5, 0.5), at(0.75, 0.5))
    val matches =
        when (name) {
            "horizontal" -> across > 200 && down < 100
            "vertical" -> across < 100 && down > 200
            "linear" -> across > 100 && down > 100
            "radial" -> across < 100 && down < 100 && outward > 200
            else -> true
        }
    assertTrue(matches, "the surface shows the $name brush: across=$across down=$down outward=$outward")
}

private const val FOCUS_WINDOW = "foundation-graphics-focus"

private val BRUSH_TILE_NAMES = listOf("horizontal", "vertical", "linear", "radial", "repeat", "reflect")

private fun ComposeSwingTest.captureBrushTiles(): Map<String, BufferedImage> =
    BRUSH_TILE_NAMES.associateWith { onNodeWithTag(BRUSH_TILE_TAG_PREFIX + it).captureToImage() }

/** A transparent image of this [image]'s size. */
private fun blankLike(image: BufferedImage): BufferedImage =
    BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)

/** This image less [inset] pixels on every side. */
private fun BufferedImage.inside(inset: Int): BufferedImage =
    getSubimage(inset, inset, width - 2 * inset, height - 2 * inset)

/** The center of the placement canvas's layout bounds, in the coordinates a capture of the stage uses. */
internal fun ComposeSwingTest.placementCanvasCenter(): Point {
    val canvas = onNodeWithTag(PLACEMENT_CHILD_TAG).fetch<JComponent>()
    val local = (canvas as Decoratable).decoration.localLayoutBounds(canvas)
    return SwingUtilities.convertPoint(
        canvas,
        local.centerX.toInt(),
        local.centerY.toInt(),
        onNodeWithTag(PLACEMENT_STAGE_TAG).fetch<JComponent>(),
    )
}

/**
 * The layout bounds of the component tagged [tag], without the paint outsets its decorations take: its bounds less
 * those outsets, from its parent's layout origin, which the parent's own paint outsets move.
 */
private fun ComposeSwingTest.layoutBounds(tag: String): Rectangle {
    val component = onNodeWithTag(tag).fetch<JComponent>()
    val parent = component.parent
    val local = (component as Decoratable).decoration.localLayoutBounds(component)
    val box = SwingUtilities.convertRectangle(component, local, parent)
    val origin = (parent as? Decoratable)?.decoration?.localLayoutBounds(parent)?.location
    if (origin != null) box.translate(-origin.x, -origin.y)
    return box
}
