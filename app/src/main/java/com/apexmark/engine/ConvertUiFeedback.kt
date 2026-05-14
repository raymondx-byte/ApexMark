package com.apexmark.engine

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import com.apexmark.R

/** 各 [ConvertActions] 与 [ConvertResult] 组合的 Toast 文案（主界面 / 通知 / 悬浮球共用）。 */
object ConvertUiFeedback {

    /** 转换结果提示：屏幕居中，与通知/主界面按钮「→ WPS / → HTML / → MD」语义一致。 */
    fun showCenteredToast(context: Context, message: String) {
        val toast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    fun toastMessage(context: Context, action: String, result: ConvertResult): Pair<Boolean, String> {
        val r = context.resources
        when (result) {
            is ConvertResult.Success -> {
                val base = when {
                    ConvertActions.producesMarkdown(action) ->
                        r.getString(R.string.converted_to_markdown_with_count, result.charCount)
                    ConvertActions.isHtmlEmailOutput(action) ->
                        r.getString(R.string.converted_html_email_with_count, result.charCount)
                    ConvertActions.writesWpsStyleClipboard(action) ->
                        r.getString(R.string.converted_wps_with_count, result.charCount)
                    else ->
                        r.getString(R.string.converted_wps_with_count, result.charCount)
                }
                val msg = if (!result.hint.isNullOrBlank()) "$base\n${result.hint}" else base
                return true to msg
            }
            is ConvertResult.PlainBlankLinesCollapsed ->
                return true to r.getString(R.string.converted_plain_blank_lines_collapsed, result.charCount)
            is ConvertResult.ClipboardImageUnsupported ->
                return false to r.getString(R.string.clipboard_image_unsupported)
            is ConvertResult.Empty -> return false to r.getString(R.string.clipboard_empty)
            is ConvertResult.NotMarkdown -> {
                val text = if (ConvertActions.expectsMarkdownSource(action)) {
                    r.getString(R.string.not_markdown)
                } else {
                    r.getString(R.string.not_html)
                }
                return false to text
            }
            is ConvertResult.NotHtml -> {
                val text = if (ConvertActions.expectsMarkdownSource(action)) {
                    r.getString(R.string.not_markdown)
                } else {
                    r.getString(R.string.not_html)
                }
                return false to text
            }
            is ConvertResult.TooLarge ->
                return false to r.getString(R.string.content_too_large, result.sizeMb)
            is ConvertResult.Error ->
                return false to r.getString(R.string.convert_error, result.message.take(50))
        }
    }
}
