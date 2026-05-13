package com.apexmark.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.apexmark.R
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.MarkdownConverter
import com.apexmark.engine.StyleStyler

/**
 * 透明、无 UI 的活动。Android 10+ 要求剪贴板访问必须由前台拥有窗口焦点的 Activity 发起。
 * 通过 `EXTRA_DIRECTION` 决定转换方向：MD→Rich 或 Rich→MD。
 */
class ClipboardConvertActivity : Activity() {

    private var handled = false

    companion object {
        const val EXTRA_DIRECTION = "direction"
        const val DIR_MD_TO_HTML = "md_to_html"
        const val DIR_HTML_TO_MD = "html_to_md"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            doConvert(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handled = false
    }

    private fun doConvert(launchIntent: Intent?) {
        val direction = launchIntent?.getStringExtra(EXTRA_DIRECTION) ?: DIR_MD_TO_HTML
        // 优先复用 Service 已持有的 converter，避免重复构造 Flexmark；fallback 时本地构造一次。
        val converter = FloatingPortalServiceLocator.instance?.converter
            ?: MarkdownConverter(StyleStyler())
        val result: ConvertResult = try {
            when (direction) {
                DIR_HTML_TO_MD -> converter.convertHtmlClipboardToMarkdown(this)
                else -> converter.convertClipboard(this)
            }
        } catch (e: Exception) {
            ConvertResult.Error(getString(R.string.convert_error, e.message?.take(50) ?: ""))
        }

        val success: Boolean
        val msg: String
        when (result) {
            is ConvertResult.Success -> {
                success = true
                msg = if (direction == DIR_HTML_TO_MD)
                    getString(R.string.converted_to_markdown_with_count, result.charCount)
                else
                    getString(R.string.converted_success_with_count, result.charCount)
            }
            is ConvertResult.Empty -> {
                success = false
                msg = getString(R.string.clipboard_empty)
            }
            is ConvertResult.NotMarkdown -> {
                success = false
                msg = getString(R.string.not_markdown)
            }
            is ConvertResult.NotHtml -> {
                success = false
                msg = getString(R.string.not_html)
            }
            is ConvertResult.TooLarge -> {
                success = false
                msg = getString(R.string.content_too_large, result.sizeMb)
            }
            is ConvertResult.Error -> {
                success = false
                msg = result.message
            }
        }

        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        FloatingPortalServiceLocator.instance?.onConvertResult(success)

        finish()
        overridePendingTransition(0, 0)
    }
}
