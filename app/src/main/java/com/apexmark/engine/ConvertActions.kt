package com.apexmark.engine

import android.content.Intent

/**
 * 剪贴板转换的显式动作（通知栏 / 主界面 / 悬浮球桥接 Activity 共用）。
 * 「→ MD」类多条按钮在引擎中均走 [MarkdownConverter.convertHtmlClipboardToMarkdown]，仅文案区分使用场景。
 */
object ConvertActions {
    const val EXTRA = "com.apexmark.convert_action"

    const val MD_TO_WPS = "md_to_wps"
    const val WPS_OR_TEXT_TO_MD = "wps_or_text_to_md"
    const val MD_TO_HTML_EMAIL = "md_to_html_email"
    const val HTML_OR_TEXT_TO_MD = "html_or_text_to_md"
    const val HTML_TO_WPS = "html_to_wps"
    const val WPS_TO_MD = "wps_to_md"
    /** 剪贴板 HTML / 纯文本 → 单 MIME `text/html`（非 Markdown 源时走此动作）。 */
    const val CLIPBOARD_TO_HTML_EMAIL = "clipboard_to_html_email"

    private val all = setOf(
        MD_TO_WPS, WPS_OR_TEXT_TO_MD, MD_TO_HTML_EMAIL,
        HTML_OR_TEXT_TO_MD, HTML_TO_WPS, WPS_TO_MD,
        CLIPBOARD_TO_HTML_EMAIL
    )

    /** 写入 WPS 取向剪贴板（含第二 URI 项）的动作，成功后可附带回读诊断。 */
    fun writesWpsStyleClipboard(action: String): Boolean =
        action == MD_TO_WPS || action == HTML_TO_WPS

    /** 期望剪贴板主槽为 Markdown 文本。 */
    fun expectsMarkdownSource(action: String): Boolean =
        action == MD_TO_WPS || action == MD_TO_HTML_EMAIL

    /** 成功时 Toast 与「MD→HTML」相同（均为单 MIME HTML 剪贴板）。 */
    fun isHtmlEmailOutput(action: String): Boolean =
        action == MD_TO_HTML_EMAIL || action == CLIPBOARD_TO_HTML_EMAIL

    /** 反向得到 Markdown。 */
    fun producesMarkdown(action: String): Boolean =
        action == WPS_OR_TEXT_TO_MD || action == HTML_OR_TEXT_TO_MD || action == WPS_TO_MD

    /**
     * @param legacyDirection [ClipboardConvertActivity.EXTRA_DIRECTION] 取值，须与 Activity 中
     * `DIR_HTML_TO_MD` 等常量一致。
     */
    fun resolve(intent: Intent?, legacyDirection: String?): String {
        intent?.getStringExtra(EXTRA)?.let { if (it in all) return it }
        return when (legacyDirection) {
            "html_to_md" -> HTML_OR_TEXT_TO_MD
            else -> MD_TO_WPS
        }
    }

    /** 与通知栏两行按钮顺序一致：first = 左 / 主项，second = 右 / 次项。 */
    fun primarySecondaryForKind(kind: ClipboardClipKind): Pair<String, String> = when (kind) {
        ClipboardClipKind.MARKDOWN -> MD_TO_WPS to MD_TO_HTML_EMAIL
        ClipboardClipKind.HTML -> HTML_TO_WPS to HTML_OR_TEXT_TO_MD
        ClipboardClipKind.WPS -> CLIPBOARD_TO_HTML_EMAIL to WPS_OR_TEXT_TO_MD
        ClipboardClipKind.PLAIN -> HTML_OR_TEXT_TO_MD to CLIPBOARD_TO_HTML_EMAIL
        ClipboardClipKind.REMOTE_UPDATE -> HTML_OR_TEXT_TO_MD to CLIPBOARD_TO_HTML_EMAIL
        ClipboardClipKind.EMPTY -> MD_TO_WPS to MD_TO_HTML_EMAIL
        ClipboardClipKind.IMAGE -> MD_TO_WPS to MD_TO_HTML_EMAIL
    }
}
