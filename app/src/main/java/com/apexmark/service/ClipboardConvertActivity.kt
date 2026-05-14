package com.apexmark.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.apexmark.R
import com.apexmark.engine.ConvertActions
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.ConvertUiFeedback
import com.apexmark.engine.MarkdownConverter
/**
 * 透明、无 UI 的活动。Android 10+ 要求剪贴板访问必须由前台拥有窗口焦点的 Activity 发起。
 * 通过 [ConvertActions.EXTRA]（或旧版 [EXTRA_DIRECTION]）选择六种显式转换。
 */
class ClipboardConvertActivity : Activity() {

    private var handled = false

    companion object {
        /** @deprecated 请使用 [ConvertActions.EXTRA] */
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
        val action = ConvertActions.resolve(
            launchIntent,
            launchIntent?.getStringExtra(EXTRA_DIRECTION)
        )
        val converter = FloatingPortalServiceLocator.instance?.converter
            ?: MarkdownConverter()
        val result: ConvertResult = try {
            converter.convertForAction(this, action)
        } catch (e: Exception) {
            ConvertResult.Error(getString(R.string.convert_error, e.message?.take(50) ?: ""))
        }

        val (success, msg) = ConvertUiFeedback.toastMessage(this, action, result)

        ConvertUiFeedback.showCenteredToast(this, msg)
        FloatingPortalServiceLocator.notifyConvertResult(success)
        FloatingPortalServiceLocator.requestNotificationUpdate()

        finish()
        overridePendingTransition(0, 0)
    }
}
