package com.apexmark.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apexmark.R
import com.apexmark.engine.ConvertResult
import com.apexmark.engine.ConvertUiFeedback
import com.apexmark.engine.MarkdownConverter
import com.apexmark.service.FloatingPortalService
import com.apexmark.service.FloatingPortalServiceLocator

class QuickActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONVERT_CLIPBOARD = "com.apexmark.ACTION_CONVERT_CLIPBOARD"
        const val ACTION_TOGGLE_BUBBLE = "com.apexmark.ACTION_TOGGLE_BUBBLE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CONVERT_CLIPBOARD -> {
                val converter = FloatingPortalServiceLocator.instance?.converter
                    ?: MarkdownConverter()
                val msg = when (val result = converter.convertClipboard(context)) {
                    is ConvertResult.Success -> context.getString(R.string.converted_wps_with_count, result.charCount)
                    is ConvertResult.PlainBlankLinesCollapsed ->
                        context.getString(R.string.converted_plain_blank_lines_collapsed, result.charCount)
                    is ConvertResult.ClipboardImageUnsupported -> context.getString(R.string.clipboard_image_unsupported)
                    is ConvertResult.Empty -> context.getString(R.string.clipboard_empty)
                    is ConvertResult.NotMarkdown -> context.getString(R.string.not_markdown)
                    is ConvertResult.NotHtml -> context.getString(R.string.not_html)
                    is ConvertResult.TooLarge -> context.getString(R.string.content_too_large, result.sizeMb)
                    is ConvertResult.Error -> result.message
                }
                ConvertUiFeedback.showCenteredToast(context, msg)
                FloatingPortalServiceLocator.requestNotificationUpdate()
            }
            ACTION_TOGGLE_BUBBLE -> {
                FloatingPortalService.start(context)
            }
            else -> {}
        }
    }
}
