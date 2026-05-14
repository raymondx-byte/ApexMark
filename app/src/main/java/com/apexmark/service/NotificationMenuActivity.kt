package com.apexmark.service

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.apexmark.AppForegroundTracker
import com.apexmark.R
import com.apexmark.engine.ClipboardClipKind
import com.apexmark.engine.ConvertActions
import com.apexmark.engine.ConvertUiFeedback
import com.apexmark.engine.MarkdownConverter
import com.apexmark.engine.StyleStyler
import com.apexmark.ui.ConvertMenuUi

/**
 * 从通知栏点击「转换文本」提示进入：取得焦点后判型，再展示与悬浮球相同风格的双键二级菜单。
 */
class NotificationMenuActivity : Activity() {

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
            if (kind == ClipboardClipKind.IMAGE) {
                ConvertUiFeedback.showCenteredToast(this, getString(R.string.clipboard_image_unsupported))
                finish()
                overridePendingTransition(0, 0)
                return
            }
            showConvertMenu(kind)
        }
    }

    private fun menuDp(px: Int): Int = (px * resources.displayMetrics.density + 0.5f).toInt()

    private fun labelForAction(action: String, plainTidyLabels: Boolean): String {
        if (plainTidyLabels) return getString(R.string.notif_plain_tidy_blanks)
        return when (action) {
            ConvertActions.MD_TO_WPS -> getString(R.string.notif_md_wps)
            ConvertActions.MD_TO_HTML_EMAIL -> getString(R.string.notif_md_html)
            ConvertActions.HTML_TO_WPS -> getString(R.string.notif_html_wps)
            ConvertActions.HTML_OR_TEXT_TO_MD -> getString(R.string.notif_html_text_md)
            ConvertActions.WPS_OR_TEXT_TO_MD -> getString(R.string.notif_wps_text_md)
            ConvertActions.WPS_TO_MD -> getString(R.string.notif_wps_md)
            ConvertActions.CLIPBOARD_TO_HTML_EMAIL -> getString(R.string.notif_clipboard_to_html_email)
            else -> getString(R.string.notif_md_wps)
        }
    }

    private fun launchConvertActivity(action: String) {
        startActivity(
            Intent(this, ClipboardConvertActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_HISTORY or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                )
                putExtra(ConvertActions.EXTRA, action)
            }
        )
    }

    private fun runConvertAction(action: String) {
        val svc = FloatingPortalServiceLocator.instance
        if (AppForegroundTracker.isForeground && svc != null) {
            svc.performConvertDirectly(action)
        } else {
            launchConvertActivity(action)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showConvertMenu(kind: ClipboardClipKind) {
        val converter = FloatingPortalServiceLocator.instance?.converter
            ?: MarkdownConverter(StyleStyler())
        val (primary, secondary) = ConvertActions.primarySecondaryForKind(kind)
        val plainTidy = kind == ClipboardClipKind.PLAIN

        val btnWidth = menuDp(268)
        val pillRadius = menuDp(999).toFloat()
        val strokeW = menuDp(2)
        val primaryBg = ConvertMenuUi.primaryPill(pillRadius)
        val secondaryBg = ConvertMenuUi.secondaryPill(pillRadius, strokeW)

        val typeLine = TextView(this).apply {
            text = converter.peekClipboardFormatLabelForKind(this@NotificationMenuActivity, kind)
            setTextColor(ConvertMenuUi.typeLineColor)
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(menuDp(12), 0, menuDp(12), menuDp(14))
        }

        val tvPrimary = TextView(this).apply {
            text = labelForAction(primary, plainTidy)
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(menuDp(20), menuDp(14), menuDp(20), menuDp(14))
            background = primaryBg
            setOnClickListener {
                runConvertAction(primary)
                finish()
                overridePendingTransition(0, 0)
            }
        }
        val tvSecondary = TextView(this).apply {
            text = labelForAction(secondary, plainTidy)
            setTextColor(Color.parseColor("#0050B0"))
            textSize = 13f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(menuDp(20), menuDp(14), menuDp(20), menuDp(14))
            background = secondaryBg
            setOnClickListener {
                runConvertAction(secondary)
                finish()
                overridePendingTransition(0, 0)
            }
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = menuDp(18)
            setPadding(pad, pad, pad, pad)
            background = ConvertMenuUi.panelCard(menuDp(28).toFloat(), menuDp(1))
            elevation = menuDp(6).toFloat()
            addView(typeLine, LinearLayout.LayoutParams(btnWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
            val lp1 = LinearLayout.LayoutParams(btnWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp1.topMargin = menuDp(4)
            lp1.bottomMargin = menuDp(12)
            addView(tvPrimary, lp1)
            addView(tvSecondary, LinearLayout.LayoutParams(btnWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            setBackgroundColor(ConvertMenuUi.SCRIM_ARGB)
            setOnClickListener {
                finish()
                overridePendingTransition(0, 0)
            }
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        panel.isClickable = true
        panel.setOnClickListener { /* 吞掉点击，避免关闭 */ }
        root.addView(panel, lp)
        setContentView(root)
    }
}
