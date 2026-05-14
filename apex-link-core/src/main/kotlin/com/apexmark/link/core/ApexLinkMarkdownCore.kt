package com.apexmark.link.core

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * JVM 可复用的 Markdown ↔ 内联 HTML 管线（Flexmark + [StyleStyler]）。
 * Android 剪贴板适配仍由应用内 [com.apexmark.engine.MarkdownConverter] 负责。
 *
 * 许可与仓库根目录 AGPL-3.0 一致；以库形态分发时 copyleft 边界更清晰。
 */
class ApexLinkMarkdownCore(
    private val styler: StyleStyler = StyleStyler()
) {

    private val options = MutableDataSet().apply {
        set(Parser.EXTENSIONS, listOf(
            TablesExtension.create(),
            StrikethroughExtension.create()
        ))
        set(HtmlRenderer.SOFT_BREAK, "<br/>\n")
        set(TablesExtension.COLUMN_SPANS, false)
        set(TablesExtension.MIN_HEADER_ROWS, 1)
        set(TablesExtension.MAX_HEADER_ROWS, 1)
        set(TablesExtension.APPEND_MISSING_COLUMNS, true)
        set(TablesExtension.DISCARD_EXTRA_COLUMNS, true)
        set(TablesExtension.WITH_CAPTION, false)
        set(TablesExtension.HEADER_SEPARATOR_COLUMN_MATCH, true)
    }

    private val parser: Parser = Parser.builder(options).build()
    private val renderer: HtmlRenderer = HtmlRenderer.builder(options).build()
    private val html2md: FlexmarkHtmlConverter = FlexmarkHtmlConverter.builder().build()

    private val multiBlankLineRegex = Regex("\\n{3,}")

    companion object {
        const val MAX_SIZE_BYTES = 1_048_576L

        const val CLIPBOARD_BODY_STYLE =
            "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC','Microsoft YaHei',sans-serif;"
    }

    fun collapseBlankLines(text: String): String =
        text.replace(multiBlankLineRegex, "\n\n")

    fun toRawHtml(markdown: String): String {
        val cleaned = collapseBlankLines(markdown)
        val doc = parser.parse(cleaned)
        return renderer.render(doc)
    }

    fun toInlineStyledHtml(markdown: String): String {
        val rawHtml = toRawHtml(markdown)
        return styler.inlineAll(rawHtml)
    }

    /**
     * 网页 / 片段 HTML → 先 html2md 再走 Markdown 渲染与内联样式。
     */
    fun htmlSourceToStyledFragment(html: String): String {
        val trimmed = html.trim()
        if (trimmed.contains("<table", ignoreCase = true)) {
            val slice = extractBodyInnerHtmlIfDocument(trimmed).ifBlank { trimmed }
            return styler.inlineAll(slice)
        }
        val md = try {
            html2md.convert(trimmed).trim()
        } catch (_: Exception) {
            ""
        }
        return if (md.isNotBlank()) {
            toInlineStyledHtml(md)
        } else {
            styler.inlineAll(trimmed)
        }
    }

    fun htmlToMarkdown(html: String): String = html2md.convert(html).trimEnd()

    fun inlineStyledPlainSource(source: String): String =
        styler.inlineAll(styler.styledPlainParagraph(source))

    fun convertText(text: String): Pair<String, String> {
        val trimmed = collapseBlankLines(text.trim())
        val fragment = when {
            isLikelyMarkdownDocument(trimmed) -> toInlineStyledHtml(trimmed)
            acceptsAsHtmlSource(trimmed) -> htmlSourceToStyledFragment(trimmed)
            else -> ""
        }
        if (fragment.isBlank()) return Pair(trimmed, "")
        val plainOut = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }
        return Pair(plainOut, fragment)
    }

    fun wrapClipboardHtmlDocument(fragment: String): String {
        val t = fragment.trim()
        if (t.startsWith("<!DOCTYPE", ignoreCase = true) ||
            t.startsWith("<html", ignoreCase = true)
        ) {
            return fragment
        }
        return "<!DOCTYPE html><html><head>" +
            "<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\">" +
            "<meta charset=\"utf-8\">" +
            "</head><body style=\"" + CLIPBOARD_BODY_STYLE + "\">" +
            t +
            "</body></html>"
    }

    fun looksLikeHtml(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("<") && t.contains(">") &&
            Regex("<\\s*(p|div|span|h[1-6]|ul|ol|li|table|thead|tbody|tr|td|th|br|strong|em|b|i|a|img|code|pre|blockquote)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)
    }

    fun looseMarkupClipboardBody(s: String): Boolean {
        val t = s.trim().removePrefix("\uFEFF").trim()
        if (t.length < 8) return false
        val lower = t.lowercase()
        if (!lower.contains("<") || !lower.contains(">")) return false
        return lower.contains("<table") || lower.contains("<p") || lower.contains("<div") ||
            lower.contains("<html") || lower.contains("<body") || lower.contains("<span") ||
            lower.contains("<h1") || lower.contains("<h2") || lower.contains("<h3") ||
            lower.contains("<tr") || lower.contains("<td") || lower.contains("<th") ||
            lower.contains("<ul") || lower.contains("<ol") || lower.contains("<br")
    }

    fun acceptsAsHtmlSource(text: String): Boolean =
        looksLikeHtml(text) || looseMarkupClipboardBody(text)

    fun isLikelyMarkdownDocument(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || looksLikeHtml(t)) return false
        if (hasStructuralMarkdownSignals(t)) return true
        if (t.length < 48) return false
        return looksLikeMarkdown(t)
    }

    private fun extractBodyInnerHtmlIfDocument(html: String): String {
        val m = Regex("""(?is)<body[^>]*>(.*)</body>""").find(html) ?: return ""
        return m.groupValues[1].trim()
    }

    private fun plainTextFromStyledFragment(fragment: String): String {
        val full = wrapClipboardHtmlDocument(fragment)
        return full.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun hasStructuralMarkdownSignals(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (Regex("^#{1,6}\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (t.contains("```")) return true
        if (Regex("^[-*+]\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^\\d+\\.\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^>\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^\\[.+]\\(.+\\)", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("!\\[[^]]*]\\([^)]+\\)").containsMatchIn(t)) return true
        if (Regex("\\|[^\\n]+\\|\\s*\\n\\s*\\|[\\s\\-:|]+\\|", RegexOption.MULTILINE).containsMatchIn(t)) return true
        return false
    }

    private fun looksLikeMarkdown(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (looksLikeHtml(t)) return false
        if (Regex("^#{1,6}\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (t.contains("```")) return true
        if (Regex("^[-*+]\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^\\d+\\.\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^>\\s", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("^\\[.+]\\(.+\\)", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if (Regex("!\\[[^]]*]\\([^)]+\\)").containsMatchIn(t)) return true
        if (Regex("\\|[^\\n]+\\|\\s*\\n\\s*\\|[\\s\\-:|]+\\|", RegexOption.MULTILINE).containsMatchIn(t)) return true
        if ((t.contains("**") || t.contains("~~")) && t.contains('\n')) return true
        return false
    }
}
