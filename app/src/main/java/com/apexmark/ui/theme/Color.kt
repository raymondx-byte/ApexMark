package com.apexmark.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ApexMark 色彩系统 — 源自图标深蓝渐变色谱。
 *
 * 图标主色提取：#002080 → #0040A0 → #0060C0 → #2080E0
 * 构建一套从深邃到明亮的蓝色阶梯，搭配冷灰中性色。
 */

// Brand blue — extracted from the icon gradient
val Apex50  = Color(0xFFE6F0FF)  // 最浅底色
val Apex100 = Color(0xFFCCE0FF)
val Apex200 = Color(0xFF99C2FF)
val Apex300 = Color(0xFF5C9AFF)
val Apex400 = Color(0xFF3380E0)  // 图标亮部
val Apex500 = Color(0xFF1566C8)  // 主色
val Apex600 = Color(0xFF0050B0)  // 图标核心蓝
val Apex700 = Color(0xFF003E90)  // 图标深部
val Apex800 = Color(0xFF002E70)
val Apex900 = Color(0xFF001A45)  // 最深色

// Accent teal
val Teal300 = Color(0xFF5CC6C8)
val Teal400 = Color(0xFF38A3A5)
val Teal500 = Color(0xFF2C8C8E)
val Teal600 = Color(0xFF1F7072)

// Warm amber — for CTA / highlight only
val Amber400 = Color(0xFFFFB74D)
val Amber500 = Color(0xFFFFA726)
val Amber600 = Color(0xFFFB8C00)

// Cool neutral gray
val Neutral0   = Color(0xFFFFFFFF)
val Neutral50  = Color(0xFFF8FAFB)
val Neutral100 = Color(0xFFF1F3F5)
val Neutral200 = Color(0xFFE4E8EB)
val Neutral300 = Color(0xFFCED4DA)
val Neutral400 = Color(0xFFADB5BD)
val Neutral500 = Color(0xFF868E96)
val Neutral600 = Color(0xFF6C757D)
val Neutral700 = Color(0xFF495057)
val Neutral800 = Color(0xFF343A40)
val Neutral900 = Color(0xFF212529)
val Neutral950 = Color(0xFF16191C)

// Dark-mode surfaces (deep blue-gray, not pure black, to keep brand warmth)
val DarkSurface0  = Color(0xFF0D1117)  // 最深底色
val DarkSurface1  = Color(0xFF161B22)  // 基础表面
val DarkSurface2  = Color(0xFF1C2129)  // 卡片
val DarkSurface3  = Color(0xFF242A33)  // 悬浮/弹出层
val DarkSurface4  = Color(0xFF2D333D)  // 边框/分割线
val DarkOnSurface = Color(0xFFE6EDF3)  // 暗色上的文字

// Semantic
val Success     = Color(0xFF2DA44E)
val SuccessLight = Color(0xFFDCF5E4)
val Error       = Color(0xFFE5534B)
val ErrorLight  = Color(0xFFFDECEB)
val Warning     = Color(0xFFD29922)
val WarningLight = Color(0xFFFFF6DD)
val Info        = Color(0xFF539BF5)
val InfoLight   = Color(0xFFDEECFF)
