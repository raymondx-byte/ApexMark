package com.apexmark.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 通知二级菜单与悬浮球 Popup 共用的半透明毛玻璃风格，避免两处渐变硬编码分叉。
 */
object ConvertMenuUi {

    /** 全屏遮罩（深蓝灰，略透明） */
    val SCRIM_ARGB: Int = Color.argb(140, 15, 23, 42)

    /** 卡片底色（近白半透明） */
    private val PANEL_FILL_ARGB: Int = Color.argb(232, 255, 255, 255)

    /** 卡片描边（品牌蓝半透明） */
    private val PANEL_STROKE_ARGB: Int = Color.argb(153, 51, 128, 224)

    /** 类型说明行（深色，保证在半透明白底上可读） */
    val typeLineColor: Int = Color.parseColor("#1E3A5F")

    fun primaryPill(cornerRadiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            colors = intArrayOf(
                Color.parseColor("#D93380E0"),
                Color.parseColor("#D91D4ED8")
            )
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
        }

    fun secondaryPill(cornerRadiusPx: Float, strokeWidthPx: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(Color.argb(100, 59, 130, 246))
            setStroke(strokeWidthPx, Color.argb(210, 21, 102, 194))
        }

    fun panelCard(cornerRadiusPx: Float, strokeWidthPx: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(PANEL_FILL_ARGB)
            setStroke(strokeWidthPx, PANEL_STROKE_ARGB)
        }
}
