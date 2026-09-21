// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.tooling

import io.mockk.mockk
import org.jetbrains.compose.swing.carriedChainAppearancesOf
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.modifier.interaction.defaultButton
import org.jetbrains.compose.swing.components.modifier.interaction.documentFilter
import org.jetbrains.compose.swing.components.modifier.listener.changeListener
import org.jetbrains.compose.swing.components.modifier.listener.documentListener
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.rememberDocumentState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.propertyChangeListener
import org.jetbrains.compose.swing.node.SwingComponentNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.tooling.findDeclaringGroup
import org.jetbrains.compose.swing.tooling.isDebugInspectorInfoEnabled
import java.awt.Component
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentListener
import javax.swing.text.DocumentFilter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

/**
 * Behavioral tests for the chain catalog widgets carry: each widget carries the chain it was given once,
 * and the elements of the catalog's own modifiers report the values they declare.
 *
 * The chain is reached through the declaring group, so these turn inspection on the way a tool does and
 * leave it off again - it is process-wide state that would otherwise reach every later test.
 */
class CatalogChainInspectionTest {
    @AfterTest
    fun turnInspectionOff() {
        SwingUtilities.invokeAndWait { isDebugInspectorInfoEnabled = false }
    }

    @Test
    fun everyWidgetCarriesTheChainItWasGivenOnce() = runComposeSwingTest {
        // A widget assembles its own declarations - a mirror, an editor, a row height - onto the chain it
        // was handed, through builders private to its own file. Each widget here is called through the
        // overload that reaches one of those, which is where they are held to carrying the caller's chain
        // once, the way every builder a test can call is held to it directly.
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel {
                ComboBox(
                    items = listOf("a"),
                    selectedItem = "a",
                    onSelectionChange = {},
                    modifier = SwingModifier.testTag("combo"),
                )
                Slider(
                    value = 0,
                    onValueChange = {},
                    modifier = SwingModifier.testTag("slider").changeListener(ChangeListener { }),
                )
                Spinner(
                    model = SpinnerNumberModel(),
                    modifier = SwingModifier.testTag("spinner").changeListener(ChangeListener { }),
                    editor = { Label("value") },
                )
                Table(
                    rows = listOf("a"),
                    columns = { column("c", cellContent = { Label("cell") }) { it } },
                    modifier = SwingModifier.testTag("table"),
                    rowHeight = 20,
                )
                Tree(root = "root", children = { emptyList() }, modifier = SwingModifier.testTag("tree"))
                FormattedTextField(
                    value = "v",
                    onValueChange = {},
                    modifier = SwingModifier.testTag("formatted"),
                )
                FormattedTextField(
                    value = "v",
                    onValueChange = {},
                    modifier = SwingModifier.testTag("formattedRaw").propertyChangeListener("value") { },
                )
                PasswordField(
                    value = charArrayOf('a'),
                    onValueChange = {},
                    modifier =
                        SwingModifier
                            .testTag("password")
                            .documentListener(mockk<DocumentListener>(relaxed = true)),
                )
                TextArea(state = rememberDocumentState("v"), modifier = SwingModifier.testTag("area"), tabSize = 4)
            }
        }

        val tags =
            listOf("combo", "slider", "spinner", "table", "tree", "formatted", "formattedRaw", "password", "area")
        assertEquals(
            tags.associateWith { 1 },
            tags.associateWith { onNodeWithTag(it).fetch().carriedChainAppearancesOf(it) },
            "each widget should carry the chain it was given once",
        )
    }

    @Test
    fun aCatalogModifierDeclaresTheValuesItInstalls() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        val filter = DocumentFilter()
        setContent {
            Panel {
                TextField(
                    value = "text",
                    onValueChange = {},
                    modifier = SwingModifier.testTag("field").documentFilter(filter),
                )
                Button(
                    text = "OK",
                    onClick = {},
                    modifier = SwingModifier.testTag("ok").defaultButton(),
                )
            }
        }

        assertEquals(
            mapOf("documentFilter" to filter),
            declaredBy("field", "documentFilter"),
            "a document filter declares the filter it installs",
        )
        assertEquals(
            mapOf("defaultButton" to true),
            declaredBy("ok", "defaultButton"),
            "a default button declares whether it claims the association",
        )
    }

    @Test
    fun aWidgetsOwnSeamsStandInTheChainItBuilds() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel {
                Table(
                    rows = listOf("a"),
                    columns = { column("c", cellContent = { Label("cell") }) { it } },
                    modifier = SwingModifier.testTag("table"),
                    rowHeight = 20,
                )
                Spinner(
                    model = SpinnerNumberModel(),
                    modifier = SwingModifier.testTag("spinner").changeListener(ChangeListener { }),
                    editor = { Label("value") },
                )
            }
        }

        assertEquals(
            listOf(emptyMap(), emptyMap(), emptyMap()),
            listOf(
                declaredBy("table", "columnCells"),
                declaredBy("table", "userSelectionListener"),
                declaredBy("spinner", "spinnerEditor"),
            ),
            "a widget's own seams are named in the chain it builds and carry nothing a tool can show",
        )
    }

    @Test
    fun aDeclarationCarriedByACatalogTypeStillTellsOneArgumentFromAnother() = runComposeSwingTest {
        isDebugInspectorInfoEnabled = true
        setContent {
            Panel {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Label("p", modifier = SwingModifier.testTag("tab-one").tab("One"))
                    Label("q", modifier = SwingModifier.testTag("tab-two").tab("One"))
                    Label("r", modifier = SwingModifier.testTag("tab-other").tab("Two"))
                }
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-one"), fixedCellHeight = 10)
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-two"), fixedCellHeight = 10)
                ListBox(items = listOf("a"), modifier = SwingModifier.testTag("cells-other"), fixedCellHeight = 20)
            }
        }

        listOf(
            "tab" to "tab",
            "cells" to "listCellSizing",
        ).forEach { (tag, element) ->
            val first = declaredBy("$tag-one", element)
            assertEquals(first, declaredBy("$tag-two", element), "$element retains equal arguments")
            assertNotEquals(first, declaredBy("$tag-other", element), "$element distinguishes changed arguments")
        }
    }
}

/** What the element named [element] declares in the chain the component tagged [tag] carries. */
private fun ComposeSwingTest.declaredBy(
    tag: String,
    element: String,
): Map<String, Any?> = onNodeWithTag(tag).fetch().valuesOf(element)

/** What the element named [name] declares in this component's chain. */
private fun Component.valuesOf(name: String): Map<String, Any?> =
    assertNotNull(declaredChain().firstOrNull { it.first == name }, "no $name element in the chain").second

/** The chain this component carries, as the name and values each element reports. */
private fun Component.declaredChain(): List<Pair<String, Map<String, Any?>>> {
    val node = assertNotNull(findDeclaringGroup()?.node as? SwingComponentNode<*>, "no composition declared $this")
    return node.modifier.foldIn(mutableListOf<Pair<String, Map<String, Any?>>>()) { acc, element ->
        acc.apply { if (element is SwingModifier.InspectableElement) add(element.name to element.declaredValues) }
    }
}
