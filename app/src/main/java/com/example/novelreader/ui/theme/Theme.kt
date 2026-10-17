package com.example.novelreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 阅读背景方案（名称、背景色、文字色） */
data class ReadingBg(val name: String, val bg: Color, val text: Color)

val readingBgs = listOf(
    // 浅色：白纸黑字
    ReadingBg("米白", Color(0xFFF5F1E8), Color(0xFF2B2B2B)),
    ReadingBg("纯白", Color(0xFFFCFCFC), Color(0xFF2B2B2B)),
    ReadingBg("淡青", Color(0xFFE6EEF0), Color(0xFF2B2B2B)),
    ReadingBg("淡黄", Color(0xFFF7EFDA), Color(0xFF2B2B2B)),
    ReadingBg("淡绿", Color(0xFFDCE9DC), Color(0xFF2B2B2B)),
    ReadingBg("淡粉", Color(0xFFFFE4E1), Color(0xFF2B2B2B)),
    // 深色：黑纸白字
    ReadingBg("黑纸", Color(0xFF000000), Color(0xFFE8E8E8)),
    ReadingBg("深灰", Color(0xFF171717), Color(0xFFE8E8E8)),
    ReadingBg("墨蓝", Color(0xFF0D1B2A), Color(0xFFE8E8E8)),
    ReadingBg("墨绿", Color(0xFF0E2414), Color(0xFFE8E8E8)),
    ReadingBg("棕褐", Color(0xFF231510), Color(0xFFE8E8E8)),
    ReadingBg("藏青", Color(0xFF0B0F24), Color(0xFFE8E8E8))
)

/** 主题色方案（primary 强调色 + tertiary 点缀色） */
data class AccentColor(
    val name: String,
    val lightPrimary: Color,
    val lightTertiary: Color,
    val darkPrimary: Color,
    val darkTertiary: Color
)

val accentColors = listOf(
    AccentColor("黛青", Color(0xFF3E5C76), Color(0xFFB05B3B), Color(0xFF9FB3C8), Color(0xFFD08B6B)),
    AccentColor("朱砂", Color(0xFFB05B3B), Color(0xFF3E5C76), Color(0xFFD08B6B), Color(0xFF9FB3C8)),
    AccentColor("墨绿", Color(0xFF4F7A5C), Color(0xFFC08A3E), Color(0xFF8FBC9F), Color(0xFFD9B45B)),
    AccentColor("群青", Color(0xFF4A5D8C), Color(0xFF8C5E58), Color(0xFFA8B6D0), Color(0xFFC9A099)),
    AccentColor("赭红", Color(0xFF8C5E58), Color(0xFF4A5D8C), Color(0xFFC9A099), Color(0xFFA8B6D0)),
    AccentColor("青绿", Color(0xFF2E7D6B), Color(0xFFC08A3E), Color(0xFF7BC4AE), Color(0xFFD9B45B))
)

private fun lightScheme(accent: AccentColor) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = lerp(accent.lightPrimary, Color.White, 0.82f),
    onPrimaryContainer = lerp(accent.lightPrimary, Color.Black, 0.6f),
    secondary = Color(0xFF748B9C),
    tertiary = accent.lightTertiary,
    background = Color(0xFFF6F1E7),   // 宣纸
    onBackground = Color(0xFF2B2B2B),
    surface = Color(0xFFFDFBF5),
    onSurface = Color(0xFF2B2B2B),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF6B6257),
    outline = Color(0xFFB9AE9E)
)

private fun darkScheme(accent: AccentColor) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF1B2A38),
    primaryContainer = lerp(accent.darkPrimary, Color.Black, 0.62f),
    onPrimaryContainer = lerp(accent.darkPrimary, Color.White, 0.85f),
    secondary = Color(0xFF8FA0AE),
    tertiary = accent.darkTertiary,
    background = Color(0xFF1A1C1F),
    onBackground = Color(0xFFE6E1D8),
    surface = Color(0xFF23262A),
    onSurface = Color(0xFFE6E1D8),
    surfaceVariant = Color(0xFF3A3E42),
    onSurfaceVariant = Color(0xFFB8AFA3),
    outline = Color(0xFF5C5F63)
)

@Composable
fun NovelReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val accent = accentColors.getOrElse(accentIndex) { accentColors[0] }
    val colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
