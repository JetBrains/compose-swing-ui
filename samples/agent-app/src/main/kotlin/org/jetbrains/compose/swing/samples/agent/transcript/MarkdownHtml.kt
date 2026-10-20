package org.jetbrains.compose.swing.samples.agent.transcript

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.ImageGeneratingProvider
import org.intellij.markdown.html.LinkGeneratingProvider
import org.intellij.markdown.html.TrimmingInlineHolderProvider
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

private val flavour = GFMFlavourDescriptor()

private val parser = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable)

/** GitHub-flavored [markdown] as an HTML page for Swing's HTML kit, styled by [css]. */
internal fun markdownToHtml(
    markdown: String,
    css: String,
): String {
    val source: CharSequence = markdown
    val tree = parser.buildMarkdownTreeFromString(source)
    val linkMap = LinkMap.buildLinkMap(tree, source)
    val providers = flavour.createHtmlGeneratingProviders(linkMap, baseURI = null)
    val listItem =
        MarkdownElementTypes.LIST_ITEM to TaskItemProvider(providers.getValue(MarkdownElementTypes.LIST_ITEM))
    val safeProviders =
        providers + listItem +
            mapOf(
                MarkdownElementTypes.HTML_BLOCK to EscapedHtmlProvider,
                MarkdownTokenTypes.HTML_BLOCK_CONTENT to EscapedHtmlProvider,
                MarkdownTokenTypes.HTML_TAG to EscapedHtmlProvider,
                MarkdownElementTypes.IMAGE to
                    object : ImageGeneratingProvider(linkMap, null) {
                        override fun renderLink(
                            visitor: HtmlGenerator.HtmlGeneratingVisitor,
                            text: String,
                            node: ASTNode,
                            info: RenderInfo,
                        ) {
                            LinkGeneratingProvider.labelProvider.processNode(visitor, text, info.label)
                        }
                    },
            )
    val body = HtmlGenerator(markdown, tree, safeProviders).generateHtml()
    return "<html><head><style>$css</style></head>$body</html>"
}

/**
 * Writes a task item as `li.task` or `li.done` that starts with a box character. The library writes an `<input>`,
 * which Swing's HTML kit would embed as a live check box, built again on every streamed update.
 */
private class TaskItemProvider(
    private val plainItem: GeneratingProvider,
) : GeneratingProvider {
    private val inlineText = TrimmingInlineHolderProvider()

    override fun processNode(
        visitor: HtmlGenerator.HtmlGeneratingVisitor,
        text: String,
        node: ASTNode,
    ) {
        val box = node.findChildOfType(GFMTokenTypes.CHECK_BOX) ?: return plainItem.processNode(visitor, text, node)
        val done = box.getTextInNode(text).any { it == 'x' || it == 'X' }
        visitor.consumeTagOpen(node, "li", if (done) "class=\"done\"" else "class=\"task\"")
        visitor.consumeHtml(if (done) "&#9745; " else "&#9744; ")
        for (child in node.children) {
            when {
                child.type == MarkdownElementTypes.PARAGRAPH -> inlineText.processNode(visitor, text, child)
                child !is LeafASTNode -> child.accept(visitor)
            }
        }
        visitor.consumeTagClose("li")
    }
}

private object EscapedHtmlProvider : GeneratingProvider {
    override fun processNode(
        visitor: HtmlGenerator.HtmlGeneratingVisitor,
        text: String,
        node: ASTNode,
    ) {
        visitor.consumeHtml(HtmlGenerator.leafText(text, node, replaceEscapesAndEntities = false))
    }
}
