package com.apexmark.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 主题模式：跟随系统 / 强制亮色 / 强制暗色。
 */
enum class ThemeMode {
    System, Light, Dark
}

/**
 * 全局主题偏好管理。
 * 使用 SharedPreferences 持久化，StateFlow 驱动 UI 响应。
 */
object ThemePreference {

    private const val PREFS_NAME = "apexmark_theme"
    private const val KEY_MODE = "theme_mode"

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODE, ThemeMode.System.name) ?: ThemeMode.System.name
        _themeMode.value = try {
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.System
        }
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
