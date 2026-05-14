package com.apexmark.engine

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.Spanned
import androidx.core.content.FileProvider
import androidx.core.text.HtmlCompat
import com.apexmark.AppForegroundTracker
import com.apexmark.R
import com.apexmark.service.FloatingPortalServiceLocator
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import java.io.File
import java.io.FileInputStream

/** WPS 读取 FileProvider HTML 时 grant；包未安装则忽略 */
private val WPS_CLIPBOARD_GRANT_PACKAGES = listOf(
    "cn.wps.moffice_eng",
    "cn.wps.moffice",
    "com.kingsoft.moffice_eng",
    "com.kingsoft.moffice",
    "cn.wps.moffice_i18n",
    "com.kingsoft.moffice_pro"
)

/**
 * Apex-Link 核心转换引擎。
 * 剪贴板富文本：
 * - **浏览器向 HTML**：[writeHtmlEmailClipboard] — `ClipDescription` **仅 `text/html`** + 单 `Item(plain, html)`（贴近浏览器复制）。
 * - **WPS**：[writeWpsStyleClipboard] — **首项** `content://` 临时 HTML（FileProvider + grant），**第二项** `Item(plain, html)`；
 *   实测 WPS 在仅有 `htmlText` 时仍常只粘纯文本，故与 WPS 取向分策略。
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

        /**
         * 剪贴板 HTML 文档 `<body>` 默认字体（与 [StyleStyler] 正文字体栈一致）。
         * Office/WPS 系对「完整文档 + body 基字体」更友好。
         */
        const val CLIPBOARD_BODY_STYLE =
            "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC','Microsoft YaHei',sans-serif;"

        /** 无窗口时 getPrimaryClip 常为 null；通知 / 悬浮球沿用上次成功分类。 */
        @Volatile
        var lastPeekClipboardKind: ClipboardClipKind = ClipboardClipKind.EMPTY

        /**
         * 本进程刚写入剪贴板后，在部分系统上 [ClipboardManager.getPrimaryClip] 对无焦点进程仍短暂为 null；
         * 通知/前台 Service 用此兜底展示「写入后的类型」，直至能再次读到主槽或 TTL 到期。
         */
        private const val PENDING_PEEK_KIND_TTL_MS = 12_000L

        /** 写入剪贴板后，若 [ClipboardManager.getPrimaryClip] 仍短暂返回旧内容，在此时间窗内优先采信 [pendingPeekKindAfterWrite]。 */
        private const val POST_WRITE_PEEK_GRACE_MS = 320L

        @Volatile
        private var pendingPeekKindAfterWrite: ClipboardClipKind? = null

        @Volatile
        private var pendingPeekKindUntilElapsed: Long = 0L

        @Volatile
        private var pendingPeekSetAtElapsed: Long = 0L

        fun notePendingPeekKindAfterWrite(kind: ClipboardClipKind) {
            pendingPeekKindAfterWrite = kind
            pendingPeekSetAtElapsed = SystemClock.elapsedRealtime()
            pendingPeekKindUntilElapsed = pendingPeekSetAtElapsed + PENDING_PEEK_KIND_TTL_MS
            FloatingPortalServiceLocator.requestNotificationUpdate()
        }

        /**
         * 剪贴板主槽被任意来源更新（含他 App 复制）时调用。
         * 取消 [takePendingPeekKindIfFresh] 的「刚写入」兜底，否则在后台读不到 clip 时最长 [PENDING_PEEK_KIND_TTL_MS] 内通知会一直卡在写入类型。
         */
        fun discardPendingPeekAfterClipboardChanged() {
            pendingPeekKindAfterWrite = null
            pendingPeekKindUntilElapsed = 0L
            pendingPeekSetAtElapsed = 0L
        }

        private fun takePendingPeekKindIfFresh(): ClipboardClipKind? {
            val k = pendingPeekKindAfterWrite ?: return null
            if (SystemClock.elapsedRealtime() > pendingPeekKindUntilElapsed) {
                pendingPeekKindAfterWrite = null
                pendingPeekKindUntilElapsed = 0L
                return null
            }
            return k
        }

        private fun clearPendingPeekKindAfterSuccessfulRead() {
            pendingPeekKindAfterWrite = null
            pendingPeekKindUntilElapsed = 0L
            pendingPeekSetAtElapsed = 0L
        }
    }

    private val multiBlankLineRegex = Regex("\\n{3,}")

    private var pendingClipboardOutputHint: String? = null
    private val imgTagStripRegex = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val dataImageSrcRegex = Regex("""src\s*=\s*["']data:image""", RegexOption.IGNORE_CASE)

    private fun clearClipboardOutputHint() {
        pendingClipboardOutputHint = null
    }

    private fun takeClipboardOutputHint(): String? {
        val h = pendingClipboardOutputHint
        pendingClipboardOutputHint = null
        return h
    }

    /** 写剪贴板前去掉非 data: 的 img（外链图无法随 HTML 粘贴）。 */
    private fun stripNonDataImageTagsForClipboard(context: Context, htmlFragment: String): String {
        var removed = 0
        val out = imgTagStripRegex.replace(htmlFragment) { m ->
            if (dataImageSrcRegex.containsMatchIn(m.value)) m.value
            else {
                removed++
                ""
            }
        }
        if (removed > 0) {
            pendingClipboardOutputHint = context.getString(R.string.convert_remote_images_stripped, removed)
        }
        return out
    }

    /** 仅图片或 Office 绘图 MIME，且无可用 HTML 文本。 */
    private fun clipIndicatesImageClipboard(clip: ClipData): Boolean {
        val desc = clip.description ?: return false
        var sawImageMime = false
        for (i in 0 until desc.mimeTypeCount) {
            val m = (desc.getMimeType(i) ?: "").lowercase()
            if (m.contains("clipboard.drawing")) return true
            if (m.contains("openxmlformats") && m.contains("drawing")) return true
            if (m.startsWith("image/")) sawImageMime = true
        }
        if (!sawImageMime) return false
        if (clipMimeIndicatesHtml(clip) || clipHasNonBlankHtmlText(clip)) return false
        return true
    }

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

    private fun normalizeClipReadString(s: String): String =
        s.trim().removePrefix("\uFEFF").trim()

    /** WPS / Kingsoft 剪贴板常用 `content://…moffice…/copy`，HTML 在 URI 流里而非 [ClipData.Item.getHtmlText]。 */
    private fun isWpsOfficeClipboardUri(uri: Uri): Boolean {
        val h = (uri.host ?: "").lowercase()
        return (h.contains("wps") && h.contains("moffice")) ||
            (h.contains("kingsoft") && h.contains("moffice"))
    }

    /**
     * 比 [looksLikeHtml] 更宽：WPS copy provider 可能返回片段、或带 Office 命名空间的标签。
     */
    private fun looseMarkupClipboardBody(s: String): Boolean {
        val t = normalizeClipReadString(s)
        if (t.length < 8) return false
        val lower = t.lowercase()
        if (!lower.contains("<") || !lower.contains(">")) return false
        return lower.contains("<table") || lower.contains("<p") || lower.contains("<div") ||
            lower.contains("<html") || lower.contains("<body") || lower.contains("<span") ||
            lower.contains("<h1") || lower.contains("<h2") || lower.contains("<h3") ||
            lower.contains("<tr") || lower.contains("<td") || lower.contains("<th") ||
            lower.contains("<ul") || lower.contains("<ol") || lower.contains("<br")
    }

    private fun itemContentUri(item: ClipData.Item): Uri? =
        item.uri ?: item.intent?.data

    /**
     * WPS 等 App 常把富文本放在 [Spanned] 里且仅声明 `text/plain`；
     * [HtmlCompat.toHtml] 尽量还原为 HTML 供「→ HTML」「→ Markdown」使用。
     */
    private fun coerceCharSequenceToHtmlCandidate(cs: CharSequence?): String? {
        if (cs == null || cs.isEmpty()) return null
        if (cs !is Spanned) return null
        val asHtml = HtmlCompat.toHtml(cs, HtmlCompat.FROM_HTML_MODE_LEGACY).trim()
        if (asHtml.isEmpty()) return null
        if (looksLikeHtml(asHtml)) return asHtml
        if (asHtml.contains("<table", ignoreCase = true) ||
            asHtml.contains("<p", ignoreCase = true) ||
            asHtml.contains("<div", ignoreCase = true) ||
            asHtml.contains("<h1", ignoreCase = true) ||
            asHtml.contains("<h2", ignoreCase = true) ||
            asHtml.contains("<ul", ignoreCase = true) ||
            asHtml.contains("<ol", ignoreCase = true) ||
            asHtml.contains("<br", ignoreCase = true)
        ) {
            return asHtml
        }
        return null
    }

    /**
     * 读取「转成富文本」用的原始串：优先各 item 的 [ClipData.Item.getHtmlText]（浏览器），
     * 再尝试 URI 内容（若为 HTML），再逐项 [ClipData.Item.coerceToText] 中像 HTML 的串，
     * 最后退回主 item coerce。
     */
    private fun readClipRawForRichConvert(context: Context, clip: ClipData): String? {
        for (i in 0 until clip.itemCount) {
            val ht = clip.getItemAt(i).htmlText
            if (!ht.isNullOrBlank()) return normalizeClipReadString(ht)
        }
        for (i in 0 until clip.itemCount) {
            val u = itemContentUri(clip.getItemAt(i)) ?: continue
            val body = readUtf8FromContentUri(context, u) ?: continue
            val norm = normalizeClipReadString(body)
            if (norm.isEmpty()) continue
            if (looksLikeHtml(norm) || (isWpsOfficeClipboardUri(u) && looseMarkupClipboardBody(norm))) {
                return norm
            }
            if (isWpsOfficeClipboardUri(u) && clipMimeIndicatesHtml(clip)) {
                return norm
            }
        }
        for (i in 0 until clip.itemCount) {
            val cs = clip.getItemAt(i).coerceToText(context) ?: continue
            coerceCharSequenceToHtmlCandidate(cs)?.let { h ->
                val n = normalizeClipReadString(h)
                if (n.isNotEmpty() && (looksLikeHtml(n) || n.contains("<table", ignoreCase = true))) return n
            }
            val n = normalizeClipReadString(cs.toString())
            if (n.isNotEmpty() && looksLikeHtml(n)) return n
        }
        val fallback = clip.getItemAt(0).coerceToText(context)?.toString() ?: return null
        return normalizeClipReadString(fallback)
    }

    /** MD 类转换用：主 item 的纯文本（避免误把网页 htmlText 当 Markdown）。 */
    private fun readClipPlainCoerce(context: Context, clip: ClipData): String? {
        if (clip.itemCount <= 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    /** 识别用：任一 item 的 coerce 文本，避免仅 item[0] 为空时误判剪贴板为空。 */
    private fun readClipPlainAnyItem(context: Context, clip: ClipData): String {
        for (i in 0 until clip.itemCount) {
            val t = clip.getItemAt(i).coerceToText(context)?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) return t
        }
        return ""
    }

    /**
     * 多 Item 剪贴板（如 WPS/ApexMark：首项 URI、次项 `text`+`htmlText`）中优先取 **显式 CharSequence 文本**，
     * 避免 item[0] 的 `coerceToText` 把整页 HTML 当成「纯文本」从而误判 MD→WPS 走 HTML 管线。
     */
    private fun readMarkdownPlainFromClip(clip: ClipData): String? {
        for (i in 0 until clip.itemCount) {
            val t = clip.getItemAt(i).text ?: continue
            val norm = collapseBlankLines(normalizeClipReadString(t.toString()))
            if (norm.isNotBlank() && isLikelyMarkdownDocument(norm)) return norm
        }
        return null
    }

    private fun clipMimeIndicatesHtml(clip: ClipData): Boolean {
        val d = clip.description ?: return false
        for (i in 0 until d.mimeTypeCount) {
            val m = d.getMimeType(i) ?: continue
            if (m.equals(ClipDescription.MIMETYPE_TEXT_HTML, ignoreCase = true)) return true
        }
        return false
    }

    /**
     * 网页 / 片段 HTML → 先 html2md 再走与 Markdown 相同的渲染与内联样式（表格等尽量保留）；
     * **含 `<table>` 的网页** 直接内联样式、不走 html2md，避免表格结构被 Flexmark 破坏导致 WPS 拒粘。
     * 若转换为空则对内联样式做尽力注入。
     */
    private fun htmlSourceToStyledFragment(html: String): String {
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

    private fun extractBodyInnerHtmlIfDocument(html: String): String {
        val m = Regex("""(?is)<body[^>]*>(.*)</body>""").find(html) ?: return ""
        return m.groupValues[1].trim()
    }

    /**
     * 剪贴板富文本 / 纯文本 → 单 MIME HTML（Markdown 源则转调 [convertMdClipboardToHtmlEmail]）。
     */
    fun convertRichClipboardToHtmlEmail(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        val raw = readClipRawForRichConvert(context, clip)?.trim().orEmpty()
        val plain = normalizeClipReadString(readClipPlainAnyItem(context, clip))
        val source = raw.ifEmpty { plain }
        if (source.isBlank()) return ConvertResult.Empty
        val sizeBytes = source.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }

        val clipHasItemHtml = clipHasNonBlankHtmlText(clip)
        val rawIsHtml = raw.isNotEmpty() &&
            (acceptsAsHtmlSource(raw) || looksLikeHtml(raw) || looseMarkupClipboardBody(raw))
        val plainIsHtml = plain.isNotEmpty() &&
            (acceptsAsHtmlSource(plain) || looksLikeHtml(plain) || looseMarkupClipboardBody(plain))
        val useHtmlPipeline = clipHasItemHtml || rawIsHtml || plainIsHtml

        if (isLikelyMarkdownDocument(source) && !acceptsAsHtmlSource(source) && !useHtmlPipeline) {
            return convertPlainMarkdownStringToHtmlEmail(context, source)
        }
        val fragment = when {
            useHtmlPipeline -> {
                val inlineHtml = firstNonBlankHtmlTextInClip(clip)
                val htmlPick = when {
                    inlineHtml != null -> inlineHtml
                    rawIsHtml -> raw
                    else -> plain
                }
                htmlSourceToStyledFragment(htmlPick)
            }
            else -> styler.inlineAll(styler.styledPlainParagraph(source))
        }
        val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { plain.ifEmpty { source } }
        writeHtmlEmailClipboard(context, plainForClip, fragment)
        return ConvertResult.Success(charCount = source.length, hint = takeClipboardOutputHint())
    }

    /**
     * API 29+ 在后台 [ClipboardManager.getPrimaryClip] 常为 null，但 [ClipboardManager.primaryClipDescription]
     * 往往仍带 MIME；据此推断通知栏类型（点按转换时由透明 Activity 获得焦点再读全文）。
     */
    private fun inferClipboardKindFromPrimaryClipDescription(clipboard: ClipboardManager): ClipboardClipKind? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val desc = try {
            clipboard.primaryClipDescription
        } catch (_: Exception) {
            null
        } ?: return null
        if (desc.mimeTypeCount <= 0) return null
        val mimes = (0 until desc.mimeTypeCount).map { (desc.getMimeType(it) ?: "").lowercase() }
        if (mimes.any { it.startsWith("image/") }) return ClipboardClipKind.IMAGE
        if (mimes.any { it == "text/markdown" || it == "text/x-markdown" || it.endsWith("/markdown") }) {
            return ClipboardClipKind.MARKDOWN
        }
        if (mimes.any { it.contains("html") }) return ClipboardClipKind.HTML
        if (mimes.any {
            it.contains("wps") || it.contains("kingsoft") || it.contains("moffice") ||
                it.contains("officedocument") || it.contains("wordprocessingml")
        }) {
            return ClipboardClipKind.WPS
        }
        if (mimes.any { it.startsWith("text/") }) return ClipboardClipKind.PLAIN
        return null
    }

    /** 主界面 / 通知：剪贴板类型归类。 */
    fun peekClipboardKind(context: Context): ClipboardClipKind {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null) {
            takePendingPeekKindIfFresh()?.let { hinted ->
                lastPeekClipboardKind = hinted
                return hinted
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !AppForegroundTracker.isForeground
            ) {
                inferClipboardKindFromPrimaryClipDescription(clipboard)?.let { inferred ->
                    lastPeekClipboardKind = inferred
                    return inferred
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!AppForegroundTracker.isForeground) {
                    if (clipboard.hasPrimaryClip()) {
                        lastPeekClipboardKind = ClipboardClipKind.REMOTE_UPDATE
                        return ClipboardClipKind.REMOTE_UPDATE
                    }
                    lastPeekClipboardKind = ClipboardClipKind.EMPTY
                    return ClipboardClipKind.EMPTY
                }
                lastPeekClipboardKind = ClipboardClipKind.EMPTY
                return ClipboardClipKind.EMPTY
            }
            return lastPeekClipboardKind
        }
        if (clip.itemCount == 0) {
            clearPendingPeekKindAfterSuccessfulRead()
            lastPeekClipboardKind = ClipboardClipKind.EMPTY
            return ClipboardClipKind.EMPTY
        }
        val kind = when {
            clipLooksLikeApexMarkRichOutput(context, clip) -> ClipboardClipKind.WPS
            clipIndicatesImageClipboard(clip) -> ClipboardClipKind.IMAGE
            clipLooksLikeWpsExport(context, clip) -> ClipboardClipKind.WPS
            // 某 item 的显式 .text 已是 Markdown 时，优先于 MIME/HTML 包装（避免整页 HTML 误判盖住 MD）。
            readMarkdownPlainFromClip(clip) != null -> ClipboardClipKind.MARKDOWN
            clipMimeIndicatesHtml(clip) || clipHasNonBlankHtmlText(clip) -> ClipboardClipKind.HTML
            clipHasCoercibleStyledHtml(context, clip) -> ClipboardClipKind.HTML
            else -> {
                val raw = readClipRawForRichConvert(context, clip)?.trim().orEmpty()
                val plain = normalizeClipReadString(readClipPlainAnyItem(context, clip))
                when {
                    raw.isEmpty() && plain.isEmpty() -> ClipboardClipKind.EMPTY
                    acceptsAsHtmlSource(raw.ifEmpty { plain }) -> ClipboardClipKind.HTML
                    isLikelyMarkdownDocument(plain.ifEmpty { raw }) -> ClipboardClipKind.MARKDOWN
                    else -> ClipboardClipKind.PLAIN
                }
            }
        }
        val now = SystemClock.elapsedRealtime()
        val pending = pendingPeekKindAfterWrite
        val inPostWriteGrace = pending != null &&
            now <= pendingPeekKindUntilElapsed &&
            now <= pendingPeekSetAtElapsed + POST_WRITE_PEEK_GRACE_MS
        if (inPostWriteGrace) {
            val p = pending!!
            if (kind != p) {
                lastPeekClipboardKind = p
                return p
            }
        }
        lastPeekClipboardKind = kind
        clearPendingPeekKindAfterSuccessfulRead()
        return kind
    }

    /** 已知 [kind] 时的展示文案（避免重复调用 [peekClipboardKind]）。 */
    fun peekClipboardFormatLabelForKind(context: Context, kind: ClipboardClipKind): String {
        val res = context.resources
        return when (kind) {
            ClipboardClipKind.EMPTY -> res.getString(R.string.clipboard_empty)
            ClipboardClipKind.REMOTE_UPDATE -> res.getString(R.string.clipboard_kind_remote)
            ClipboardClipKind.MARKDOWN -> res.getString(R.string.clipboard_kind_markdown)
            ClipboardClipKind.WPS ->
                res.getString(R.string.clipboard_kind_wps)
            ClipboardClipKind.PLAIN -> res.getString(R.string.clipboard_kind_plain)
            ClipboardClipKind.HTML -> res.getString(R.string.clipboard_kind_html)
            ClipboardClipKind.IMAGE -> res.getString(R.string.clipboard_kind_image)
        }
    }

    /** [peekClipboardKind] 对应的展示文案。 */
    fun peekClipboardFormatLabel(context: Context): String =
        peekClipboardFormatLabelForKind(context, peekClipboardKind(context))

    /** 无 text/html MIME 时，coerce 出带块级样式的 Spanned → 按 HTML 源处理。 */
    private fun clipHasCoercibleStyledHtml(context: Context, clip: ClipData): Boolean {
        if (clipMimeIndicatesHtml(clip) || clipHasNonBlankHtmlText(clip)) return false
        for (i in 0 until clip.itemCount) {
            val it = clip.getItemAt(i)
            val tx = it.text
            if (!tx.isNullOrEmpty()) {
                if (tx is Spanned && spannedLooksStructurallyRich(tx)) return true
                coerceCharSequenceToHtmlCandidate(tx)?.let { h ->
                    if (htmlFromSpannedLikelyRichStructure(normalizeClipReadString(h))) return true
                }
            }
            val cs = it.coerceToText(context) ?: continue
            if (cs is Spanned && spannedLooksStructurallyRich(cs)) return true
            coerceCharSequenceToHtmlCandidate(cs)?.let { h ->
                if (htmlFromSpannedLikelyRichStructure(normalizeClipReadString(h))) return true
            }
        }
        return false
    }

    /** 避免「一行加粗 Markdown」类短 Spanned 被误判成网页 HTML。 */
    private fun htmlFromSpannedLikelyRichStructure(html: String): Boolean {
        if (html.isEmpty()) return false
        if (html.contains("<table", ignoreCase = true)) return true
        if (html.contains("<img", ignoreCase = true)) return true
        if (html.contains("<ul", ignoreCase = true) || html.contains("<ol", ignoreCase = true)) return true
        if (html.contains("<blockquote", ignoreCase = true)) return true
        if (Regex("""<h[1-6]\b""", RegexOption.IGNORE_CASE).containsMatchIn(html)) return true
        if (html.split(Regex("""<p\b""", RegexOption.IGNORE_CASE)).size > 2) return true
        return html.length >= 180 && looksLikeHtml(html)
    }

    /** WPS 表格等：span 多或类名暗示表格/列表，即使 toHtml 较短也视为富文本 HTML 源。 */
    private fun spannedLooksStructurallyRich(sp: Spanned): Boolean {
        val spans = sp.getSpans(0, sp.length, Any::class.java)
        if (spans.size >= 5) return true
        for (s in spans) {
            val n = s.javaClass.name
            if (n.contains("table", ignoreCase = true)) return true
            if (n.contains("Bullet", ignoreCase = true)) return true
            if (n.contains("Alignment", ignoreCase = true)) return true
        }
        return false
    }

    private fun clipLooksLikeApexMarkRichOutput(context: Context, clip: ClipData): Boolean {
        val label = clip.description?.label?.toString()?.trim().orEmpty()
        if (!label.equals("ApexMark", ignoreCase = true)) return false
        val selfFp = "${context.packageName}.fileprovider"
        for (i in 0 until clip.itemCount) {
            val u = clip.getItemAt(i).uri?.toString() ?: continue
            if (u.contains(selfFp, ignoreCase = true)) return true
        }
        // 仅有 ApexMark label、无本包 FileProvider URI 的剪贴板（如浏览器向单 MIME HTML）不按 WPS 取向归类。
        return false
    }

    private fun clipHasNonBlankHtmlText(clip: ClipData): Boolean {
        for (i in 0 until clip.itemCount) {
            if (!clip.getItemAt(i).htmlText.isNullOrBlank()) return true
        }
        return false
    }

    private fun firstNonBlankHtmlTextInClip(clip: ClipData): String? {
        for (i in 0 until clip.itemCount) {
            val ht = clip.getItemAt(i).htmlText
            if (!ht.isNullOrBlank()) return normalizeClipReadString(ht)
        }
        return null
    }

    private fun clipLooksLikeWpsExport(context: Context, clip: ClipData): Boolean {
        val selfFp = "${context.packageName}.fileprovider"
        val label = clip.description?.label?.toString().orEmpty()
        if (label.contains("WPS", ignoreCase = true)) return true
        if (label.contains("Kingsoft", ignoreCase = true)) return true
        for (i in 0 until clip.itemCount) {
            val s = clip.getItemAt(i).uri?.toString() ?: continue
            if (s.contains(selfFp, ignoreCase = true)) return false
            if (s.contains("wps.moffice", ignoreCase = true)) return true
            if (s.contains("kingsoft.moffice", ignoreCase = true)) return true
        }
        return false
    }

    /**
     * 仅 **Markdown** → WPS 取向剪贴板（`htmlText` + `content` URI，便于手机 WPS）。
     */
    fun convertMdClipboardToWps(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        readMarkdownPlainFromClip(clip)?.let { mdPlain ->
            val trimmed = collapseBlankLines(mdPlain.trim())
            if (trimmed.isNotBlank()) {
                val sizeBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
                if (sizeBytes > MAX_SIZE_BYTES) {
                    val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
                    return ConvertResult.TooLarge(sizeMb)
                }
                val fragment = toInlineStyledHtml(trimmed)
                val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }
                writeWpsStyleClipboard(context, plainForClip, fragment)
                return ConvertResult.Success(charCount = trimmed.length, hint = takeClipboardOutputHint())
            }
        }
        if (clipMimeIndicatesHtml(clip) || clipHasNonBlankHtmlText(clip)) {
            return convertHtmlClipboardToWps(context)
        }
        val text = readClipPlainCoerce(context, clip) ?: return ConvertResult.Empty
        val trimmed = collapseBlankLines(text.trim())
        if (trimmed.isBlank()) return ConvertResult.Empty
        val sizeBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }
        if (!isLikelyMarkdownDocument(trimmed)) return ConvertResult.NotMarkdown
        val fragment = toInlineStyledHtml(trimmed)
        val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }
        writeWpsStyleClipboard(context, plainForClip, fragment)
        return ConvertResult.Success(charCount = trimmed.length, hint = takeClipboardOutputHint())
    }

    /**
     * 将已取得的 Markdown 纯文本写成单 MIME HTML 剪贴板（不读剪贴板、不做 HTML 重定向），
     * 供 [convertRichClipboardToHtmlEmail] 走 Markdown 分支时避免与 [convertMdClipboardToHtmlEmail] 互相递归。
     */
    private fun convertPlainMarkdownStringToHtmlEmail(context: Context, markdownPlain: String): ConvertResult {
        val trimmed = collapseBlankLines(markdownPlain.trim())
        if (trimmed.isBlank()) return ConvertResult.Empty
        val sizeBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }
        if (!isLikelyMarkdownDocument(trimmed)) return ConvertResult.NotMarkdown
        val fragment = toInlineStyledHtml(trimmed)
        val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }
        writeHtmlEmailClipboard(context, plainForClip, fragment)
        return ConvertResult.Success(charCount = trimmed.length, hint = takeClipboardOutputHint())
    }

    /**
     * 仅 **Markdown** → 标准 `ClipData.newHtmlText`（浏览器等单 MIME 场景，不附带 WPS 用 URI 第二项）。
     */
    fun convertMdClipboardToHtmlEmail(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        readMarkdownPlainFromClip(clip)?.let { mdPlain ->
            return convertPlainMarkdownStringToHtmlEmail(context, mdPlain)
        }
        if (clipMimeIndicatesHtml(clip) || clipHasNonBlankHtmlText(clip)) {
            return convertRichClipboardToHtmlEmail(context)
        }
        val text = readClipPlainCoerce(context, clip) ?: return ConvertResult.Empty
        return convertPlainMarkdownStringToHtmlEmail(context, text)
    }

    /**
     * 仅 **HTML / 富文本**（浏览器 `htmlText`、WPS `content` URI 等）→ WPS 取向剪贴板。
     */
    fun convertHtmlClipboardToWps(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        val raw = readClipRawForRichConvert(context, clip) ?: return ConvertResult.Empty
        val trimmed = collapseBlankLines(raw.trim())
        if (trimmed.isBlank()) return ConvertResult.Empty
        if (!acceptsAsHtmlSource(trimmed)) return ConvertResult.NotHtml
        val sizeBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }
        val fragment = htmlSourceToStyledFragment(trimmed)
        val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }
        writeWpsStyleClipboard(context, plainForClip, fragment)
        return ConvertResult.Success(charCount = trimmed.length, hint = takeClipboardOutputHint())
    }

    /**
     * 剪贴板为 **纯文本**（已归类为 [ClipboardClipKind.PLAIN]）时：不跑 Markdown/HTML 管线，
     * 仅合并连续空行为段落间单行空行，并写回 `text/plain`。
     */
    fun convertPlainClipboardCollapseBlankLines(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty
        val text = readClipPlainCoerce(context, clip) ?: return ConvertResult.Empty
        val normalized = normalizeClipReadString(text)
        if (normalized.isBlank()) return ConvertResult.Empty
        val cleaned = collapseBlankLines(normalized.trim())
        if (cleaned.isBlank()) return ConvertResult.Empty
        val sizeBytes = cleaned.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("ApexMark", cleaned))
        notePendingPeekKindAfterWrite(ClipboardClipKind.PLAIN)
        return ConvertResult.PlainBlankLinesCollapsed(cleaned.length)
    }

    /**
     * **Markdown 或 HTML → WPS**（自动识别）。快捷指令、旧链路等仍可调此方法。
     */
    fun convertClipboard(context: Context): ConvertResult {
        if (peekClipboardKind(context) == ClipboardClipKind.IMAGE) {
            return ConvertResult.ClipboardImageUnsupported
        }
        if (peekClipboardKind(context) == ClipboardClipKind.PLAIN) {
            return convertPlainClipboardCollapseBlankLines(context)
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty

        val raw = readClipRawForRichConvert(context, clip) ?: return ConvertResult.Empty
        val trimmed = collapseBlankLines(raw.trim())
        if (trimmed.isBlank()) return ConvertResult.Empty

        val sizeBytes = trimmed.toByteArray(Charsets.UTF_8).size.toLong()
        if (sizeBytes > MAX_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", sizeBytes / 1_048_576.0)
            return ConvertResult.TooLarge(sizeMb)
        }

        val fragment = when {
            isLikelyMarkdownDocument(trimmed) -> toInlineStyledHtml(trimmed)
            acceptsAsHtmlSource(trimmed) -> htmlSourceToStyledFragment(trimmed)
            else -> return ConvertResult.NotMarkdown
        }

        val plainForClip = plainTextFromStyledFragment(fragment).ifEmpty { trimmed }

        writeWpsStyleClipboard(context, plainForClip, fragment)
        notePendingPeekKindAfterWrite(ClipboardClipKind.WPS)
        return ConvertResult.Success(charCount = trimmed.length, hint = takeClipboardOutputHint())
    }

    /** 通知栏 / 主界面等传入 [ConvertActions] 常量。未知 action 时按 MD→WPS 处理。 */
    fun convertForAction(context: Context, action: String): ConvertResult {
        val clipKind = try {
            peekClipboardKind(context)
        } catch (_: Throwable) {
            ClipboardClipKind.EMPTY
        }
        if (clipKind == ClipboardClipKind.IMAGE) {
            return ConvertResult.ClipboardImageUnsupported
        }
        if (clipKind == ClipboardClipKind.PLAIN) {
            return convertPlainClipboardCollapseBlankLines(context)
        }
        val result = when (action) {
            ConvertActions.MD_TO_WPS -> convertMdClipboardToWps(context)
            ConvertActions.MD_TO_HTML_EMAIL -> convertMdClipboardToHtmlEmail(context)
            ConvertActions.HTML_TO_WPS -> convertHtmlClipboardToWps(context)
            ConvertActions.WPS_OR_TEXT_TO_MD,
            ConvertActions.HTML_OR_TEXT_TO_MD,
            ConvertActions.WPS_TO_MD -> convertHtmlClipboardToMarkdown(context)
            ConvertActions.CLIPBOARD_TO_HTML_EMAIL -> convertRichClipboardToHtmlEmail(context)
            else -> convertMdClipboardToWps(context)
        }
        if (result is ConvertResult.Success) {
            notePendingPeekKindAfterWrite(clipboardKindAfterSuccessfulConvert(action))
        }
        return result
    }

    private fun clipboardKindAfterSuccessfulConvert(action: String): ClipboardClipKind = when (action) {
        ConvertActions.MD_TO_WPS, ConvertActions.HTML_TO_WPS -> ClipboardClipKind.WPS
        ConvertActions.MD_TO_HTML_EMAIL, ConvertActions.CLIPBOARD_TO_HTML_EMAIL -> ClipboardClipKind.HTML
        ConvertActions.WPS_OR_TEXT_TO_MD,
        ConvertActions.HTML_OR_TEXT_TO_MD,
        ConvertActions.WPS_TO_MD -> ClipboardClipKind.MARKDOWN
        else -> ClipboardClipKind.WPS
    }

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

    /** 剪贴板 plain 槽用；单测 JVM 无完整 Spanned 时退回粗略去标签。 */
    private fun plainTextFromStyledFragment(fragment: String): String {
        val full = wrapClipboardHtmlDocument(fragment)
        return try {
            HtmlCompat.fromHtml(full, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Throwable) {
            fragment.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }
    }

    /**
     * 反向：HTML / 富文本 → Markdown。
     * 读取剪贴板中的 HTML（优先）或纯文本，转回 Markdown 并写回剪贴板（纯文本）。
     */
    fun convertHtmlClipboardToMarkdown(context: Context): ConvertResult {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return ConvertResult.Empty
        if (clip.itemCount == 0) return ConvertResult.Empty

        val source = extractHtmlSourceFromClip(context, clip) ?: return ConvertResult.NotHtml

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
            Regex("<\\s*(p|div|span|h[1-6]|ul|ol|li|table|thead|tbody|tr|td|th|br|strong|em|b|i|a|img|code|pre|blockquote)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(t)
    }

    /** 含 WPS copy provider 返回的「宽判定」HTML 片段。 */
    private fun acceptsAsHtmlSource(text: String): Boolean =
        looksLikeHtml(text) || looseMarkupClipboardBody(text)

    /**
     * WPS：首项 FileProvider HTML URI + 第二项 `Item(plain, html)`，grant 给常见 WPS 包名。
     */
    fun writeWpsStyleClipboard(context: Context, plainText: String, htmlFragment: String) {
        setPrimaryRichHtmlClipboardWps(context, plainText, htmlFragment)
    }

    /**
     * 浏览器向：`ClipDescription` 仅 `text/html` + 单 Item（`plain` + `htmlText`）。
     */
    fun writeHtmlEmailClipboard(context: Context, plainText: String, htmlFragment: String) {
        setPrimaryRichHtmlClipboardEmail(context, plainText, htmlFragment)
    }

    /** 单 Item、`ClipDescription` 仅 `text/html`，与浏览器复制形态一致（Gmail 等）。 */
    private fun setPrimaryRichHtmlClipboardEmail(context: Context, plainText: String, htmlFragment: String) {
        clearClipboardOutputHint()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val stripped = stripNonDataImageTagsForClipboard(context, htmlFragment)
        val (plainStr, documentHtml) = buildRichClipPlainAndDocument(plainText, stripped)
        val desc = ClipDescription("ApexMark", arrayOf(ClipDescription.MIMETYPE_TEXT_HTML))
        val clip = ClipData(desc, ClipData.Item(plainStr, documentHtml))
        clipboard.setPrimaryClip(clip)
    }

    /**
     * WPS：首项 URI（完整 HTML 文件）、第二项富文本 Item。
     * 顺序与「URI 在第二项且无 htmlText」的旧版相反，避免 WPS 误选空项。
     */
    private fun setPrimaryRichHtmlClipboardWps(context: Context, plainText: String, htmlFragment: String) {
        clearClipboardOutputHint()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val stripped = stripNonDataImageTagsForClipboard(context, htmlFragment)
        val (plainStr, documentHtml) = buildRichClipPlainAndDocument(plainText, stripped)
        val uri = writeTempClipboardHtmlFile(context, documentHtml)
        grantWpsReadUri(context, uri)
        val desc = ClipDescription(
            "ApexMark",
            arrayOf(
                ClipDescription.MIMETYPE_TEXT_HTML,
                ClipDescription.MIMETYPE_TEXT_PLAIN
            )
        )
        val clip = ClipData(desc, ClipData.Item(uri))
        clip.addItem(ClipData.Item(plainStr, documentHtml))
        clipboard.setPrimaryClip(clip)
    }

    private fun buildRichClipPlainAndDocument(plainText: String, htmlFragment: String): Pair<String, String> {
        val documentHtml = sanitizeClipboardDocumentHtml(wrapClipboardHtmlDocument(htmlFragment))
        val plainStr = try {
            HtmlCompat.fromHtml(documentHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (_: Throwable) {
            plainTextFromStyledFragment(htmlFragment)
        }.ifEmpty { plainText }
        return plainStr to documentHtml
    }

    private fun writeTempClipboardHtmlFile(context: Context, documentHtml: String): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        pruneRichClipboardCache(exportsDir)
        val file = File(exportsDir, "rich_${System.currentTimeMillis()}.html")
        file.writeText(documentHtml, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun grantWpsReadUri(context: Context, uri: Uri) {
        for (pkg in WPS_CLIPBOARD_GRANT_PACKAGES) {
            try {
                context.grantUriPermission(
                    pkg,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {
                // 包未安装或系统拒绝
            }
        }
    }

    /** 仅保留最近若干份导出，避免 cache 膨胀 */
    private fun pruneRichClipboardCache(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= 8) return
        files.sortedBy { it.lastModified() }.dropLast(8).forEach { runCatching { it.delete() } }
    }

    /** 兼容旧调用：等同于 [writeWpsStyleClipboard]。 */
    fun writeToClipboard(context: Context, plainText: String, html: String) =
        writeWpsStyleClipboard(context, plainText, html)

    /**
     * 将 Flexmark 片段包成标准文档，便于微信 / WPS / 系统剪贴板等读取 text/html。
     * 含 `http-equiv` 与 `charset`、以及 `body` 默认字体，贴近 Office 系对完整 HTML 的常见预期。
     */
    internal fun wrapClipboardHtmlDocument(fragment: String): String {
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

    /** 去掉易干扰富文本粘贴的控制字符 / 行分隔符等。 */
    private fun sanitizeClipboardDocumentHtml(html: String): String {
        return buildString(html.length) {
            for (ch in html) {
                when {
                    shouldStripForRichClipboard(ch) -> append(' ')
                    ch == '\u0000' || ch == '\u000b' || ch == '\u000c' -> append(' ')
                    ch == '\u2028' || ch == '\u2029' || ch == '\u0085' -> append('\n')
                    else -> {
                        val c = ch.code
                        if (c < 32 && ch != '\n' && ch != '\r' && ch != '\t') append(' ')
                        else append(ch)
                    }
                }
            }
        }
    }

    /** 双向控制符等易触发 WPS「不支持竖排文字」等误报。 */
    private fun shouldStripForRichClipboard(ch: Char): Boolean {
        val c = ch.code
        return when (c) {
            0x200e, 0x200f -> true // LRM RLM
            in 0x202a..0x202e -> true // bidi embedding
            in 0x2066..0x2069 -> true // isolate / pop
            0xfeff -> true // BOM as char
            else -> false
        }
    }

    private fun readUtf8FromContentUri(context: Context, uri: Uri): String? {
        fun fromBytes(bytes: ByteArray): String? {
            if (bytes.isEmpty()) return null
            if (bytes.size > MAX_SIZE_BYTES) return null
            return String(bytes, Charsets.UTF_8)
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    return fromBytes(fis.readBytes())
                }
            }
        } catch (_: Throwable) {
            // WPS copy URI 等可能仅允许流式打开
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                fromBytes(input.readBytes())
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractHtmlSourceFromClip(context: Context, clip: ClipData): String? {
        for (i in 0 until clip.itemCount) {
            val it = clip.getItemAt(i)
            val inline = it.htmlText
            if (!inline.isNullOrBlank()) return normalizeClipReadString(inline)
            val u = itemContentUri(it)
            if (u != null) {
                val body = readUtf8FromContentUri(context, u)
                if (!body.isNullOrBlank()) {
                    val norm = normalizeClipReadString(body)
                    if (norm.isNotEmpty() &&
                        (looksLikeHtml(norm) || (isWpsOfficeClipboardUri(u) && looseMarkupClipboardBody(norm)) ||
                            (isWpsOfficeClipboardUri(u) && clipMimeIndicatesHtml(clip)))
                    ) {
                        return norm
                    }
                }
            }
            val tx = it.text
            if (!tx.isNullOrEmpty()) {
                coerceCharSequenceToHtmlCandidate(tx)?.let { h ->
                    val n = normalizeClipReadString(h)
                    if (n.isNotEmpty()) return n
                }
            }
        }
        for (i in 0 until clip.itemCount) {
            val cs = clip.getItemAt(i).coerceToText(context) ?: continue
            coerceCharSequenceToHtmlCandidate(cs)?.let { h ->
                val n = normalizeClipReadString(h)
                if (n.isNotEmpty()) return n
            }
            val n = normalizeClipReadString(cs.toString())
            if (n.isNotEmpty() && acceptsAsHtmlSource(n)) return n
        }
        val plain = readClipPlainAnyItem(context, clip).let { normalizeClipReadString(it) }
        if (plain.isEmpty()) return null
        if (acceptsAsHtmlSource(plain)) return plain
        return null
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

    private fun isLikelyMarkdownDocument(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || looksLikeHtml(t)) return false
        if (hasStructuralMarkdownSignals(t)) return true
        if (t.length < 48) return false
        return looksLikeMarkdown(t)
    }
}

sealed class ConvertResult {
    data class Success(val charCount: Int, val hint: String? = null) : ConvertResult()
    /** 纯文本剪贴板：仅合并多余空行后写回 plain。 */
    data class PlainBlankLinesCollapsed(val charCount: Int) : ConvertResult()
    data object Empty : ConvertResult()
    data object NotMarkdown : ConvertResult()
    data object NotHtml : ConvertResult()
    data object ClipboardImageUnsupported : ConvertResult()
    data class TooLarge(val sizeMb: String) : ConvertResult()
    data class Error(val message: String) : ConvertResult()
}
