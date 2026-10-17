package com.example.novelreader.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 主题模式取值 */
const val THEME_MODE_SYSTEM = 0  // 跟随系统
const val THEME_MODE_LIGHT = 1   // 浅色
const val THEME_MODE_DARK = 2    // 深色

/** 阅读器偏好设置（字体大小、主题、翻页模式等） */
data class ReaderSettings(
    val fontSize: Float = 18f,
    val lineHeight: Float = 1.6f,
    val isNightMode: Boolean = false,
    val pageBackground: Int = 0, // 阅读背景色索引（见 readingBgs）
    val textBrightness: Float = 1.0f, // 文字亮度 0.4~1.3
    val themeMode: Int = THEME_MODE_DARK, // 0=跟随系统 1=浅色 2=深色，默认深色
    val accentIndex: Int = 0,         // 主题色索引
)

private val Context.dataStore by preferencesDataStore(name = "reader_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val PAGE_BACKGROUND = intPreferencesKey("page_background")
        val TEXT_BRIGHTNESS = floatPreferencesKey("text_brightness")
        val THEME_MODE = intPreferencesKey("theme_mode")
        // 旧版深色模式布尔键，用于迁移
        val DARK_MODE_LEGACY = booleanPreferencesKey("dark_mode")
        val ACCENT_INDEX = intPreferencesKey("accent_index")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSize = prefs[Keys.FONT_SIZE] ?: 18f,
            lineHeight = prefs[Keys.LINE_HEIGHT] ?: 1.6f,
            isNightMode = prefs[Keys.NIGHT_MODE] ?: false,
            pageBackground = prefs[Keys.PAGE_BACKGROUND] ?: 0,
            textBrightness = prefs[Keys.TEXT_BRIGHTNESS] ?: 1.0f,
            themeMode = prefs[Keys.THEME_MODE] ?: run {
                // 迁移旧 dark_mode：true→深色，false→浅色；否则默认深色
                when (prefs[Keys.DARK_MODE_LEGACY]) {
                    true -> THEME_MODE_DARK
                    false -> THEME_MODE_LIGHT
                    null -> THEME_MODE_DARK
                }
            },
            accentIndex = prefs[Keys.ACCENT_INDEX] ?: 0
        )
    }

    suspend fun setFontSize(size: Float) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = size }
    }

    suspend fun setLineHeight(height: Float) {
        context.dataStore.edit { it[Keys.LINE_HEIGHT] = height }
    }

    suspend fun setNightMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NIGHT_MODE] = enabled }
    }

    suspend fun setPageBackground(bg: Int) {
        context.dataStore.edit { it[Keys.PAGE_BACKGROUND] = bg }
    }

    suspend fun setTextBrightness(brightness: Float) {
        context.dataStore.edit { it[Keys.TEXT_BRIGHTNESS] = brightness }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setAccentIndex(index: Int) {
        context.dataStore.edit { it[Keys.ACCENT_INDEX] = index }
    }
}
