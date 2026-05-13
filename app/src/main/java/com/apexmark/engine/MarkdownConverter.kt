package com.apexmark.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

/**
 * Apex-Link 核心转换引擎。
 * MD → HTML(内联样式) → 剪贴板双格式写入。
 *
 * 兜底保护：
 * - 超过 MAX_SIZE_BYTES (1MB) 的文本拒绝同步处理，返回 TooLarge。
 * - 所有 public 方法线程安全，可在后台线程调用。
 */
class MarkdownConverter(
    private val styler: StyleStyler
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

    companion object {
        const val MAX_SIZE_BYTES = 1_048_576L // 1MB
    }

    private val multiBlankLineRegex = Regex("\\n{3,}")

    fun toRawHtml(markdown: String): String {
        val cleaned = collapseBlankLines(markdown)
        val doc = parser.parse(cleaned)
        return renderer.render(doc)
    }

    fun toInlineStyledHtml(markdown: String): String {
        val rawHtml = toRawHtml(markdown)
        return styler.inlineAll(rawHtml)
    }

    fun collapseBlankLines(text: String): String =
        text.replace(multiBlankLineRegex, "\n\n")

    /**
     * 一键转换：读剪贴板 → 校验 → 转换 → 双格式写回。
     * 可在后台线程调用。
     */
    fun convertClipboard(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty

        val item = clip.getItemAt(0)
        val plainText = item.coerceToText(context)?.toString()
        if (plainText.isNullOrBlank()) return ConvertResult.Empty

        val sizeBytes = plainText.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }

        if (!looksLikeMarkdown(plainText)) {
            return ConvertResult.NotMarkdown
        }

        val inlinedHtml = toInlineStyledHtml(plainText)
        writeToClipboard(context, plainText, inlinedHtml)
        return ConvertResult.Success(charCount = plainText.length)
    }

    fun convertText(text: String): Pair<String, String> {
        val html = toInlineStyledHtml(text)
        return Pair(text, html)
    }

    /**
     * 反向：HTML / 富文本 → Markdown。
     * 读取剪贴板中的 HTML（优先）或纯文本，转回 Markdown 并写回剪贴板（纯文本）。
     */
    fun convertHtmlClipboardToMarkdown(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        val item = clip.getItemAt(0)

        val html: String? = item.htmlText
        val plain: String? = item.coerceToText(context)?.toString()

        val source = when {
            !html.isNullOrBlank() -> html
            !plain.isNullOrBlank() && looksLikeHtml(plain) -> plain
            else -> null
        } ?: return ConvertResult.NotHtml

        val sizeBytes = source.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }

        val md = try { html2md.convert(source).trimEnd() }
        catch (e: Exception) { return ConvertResult.Error(e.message ?: "html2md error") }

        if (md.isBlank()) return ConvertResult.NotHtml

        val cleaned = collapseBlankLines(md)
        clipboard.setPrimaryClip(ClipData.newPlainText("ApexMark", cleaned))
        return ConvertResult.Success(charCount = cleaned.length)
    }

    private fun looksLikeHtml(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("<") && t.contains(">") &&
            Regex("<\\s*(p|div|span|h[1-6]|ul|ol|li|table|tr|td|br|strong|em|b|i|a|img|code|pre|blockquote)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)
    }

    fun writeToClipboard(context: Context, @Suppress("UNUSED_PARAMETER") plainText: String, html: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val renderedPlain = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString().trim()
        val clip = ClipData.newHtmlText("ApexMark", renderedPlain, html)
        clipboard.setPrimaryClip(clip)
    }

    private fun looksLikeMarkdown(text: String): Boolean {
        val markers = listOf(
            "# ", "## ", "### ",
            "**", "~~", "```",
            "- ", "* ", "1. ",
            "> ", "| ", "---",
            "[", "!["
        )
        return markers.any { text.contains(it) }
    }
}

sealed class ConvertResult {
    data class Success(val charCount: Int) : ConvertResult()
    data object Empty : ConvertResult()
    data object NotMarkdown : ConvertResult()
    data object NotHtml : ConvertResult()
    data class TooLarge(val sizeMb: String) : ConvertResult()
    data class Error(val message: String) : ConvertResult()
}
