package com.apexmark.service

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.apexmark.engine.ClipboardClipKind
import com.apexmark.engine.MarkdownConverter
import com.apexmark.engine.StyleStyler

/**
 * 透明 Activity：在取得窗口焦点后读取剪贴板并分类（Android 10+ 要求）。
 * 仅用于悬浮球：判型后再弹出与通知栏一致的转换菜单。
 */
class ClipboardPeekActivity : Activity() {

    private var handled = false

    companion object {
        const val EXTRA_PEEK_TARGET = "com.apexmark.PEEK_TARGET"
        const val PEEK_BUBBLE = "bubble"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handled = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            MarkdownConverter.discardPendingPeekAfterClipboardChanged()
            val converter = FloatingPortalServiceLocator.instance?.converter
                ?: MarkdownConverter(StyleStyler())
            val kind = try {
                converter.peekClipboardKind(this)
            } catch (_: Throwable) {
                ClipboardClipKind.EMPTY
            }
            if (intent.getStringExtra(EXTRA_PEEK_TARGET) == PEEK_BUBBLE) {
                FloatingPortalServiceLocator.instance?.showBubbleConvertMenu(kind)
            }
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing) finish()
                overridePendingTransition(0, 0)
            }
        }
    }
}
