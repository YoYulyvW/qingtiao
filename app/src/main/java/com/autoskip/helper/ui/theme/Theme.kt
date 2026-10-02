package com.autoskip.helper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** HarmonyOS 浅色配色方案 */
private val LightColors = lightColorScheme(
    primary = HarmonyColor.BrandOrange,
    onPrimary = HarmonyColor.White,
    primaryContainer = HarmonyColor.IconOrangeBg,
    onPrimaryContainer = HarmonyColor.BrandOrange,
    secondary = HarmonyColor.BrandBlue,
    onSecondary = HarmonyColor.White,
    tertiary = HarmonyColor.Success,
    background = HarmonyColor.GrayBG,
    onBackground = HarmonyColor.TextPrimary,
    surface = HarmonyColor.White,
    onSurface = HarmonyColor.TextPrimary,
    surfaceVariant = HarmonyColor.Gray1,
    onSurfaceVariant = HarmonyColor.Gray6,
    outline = HarmonyColor.Gray2,
    outlineVariant = HarmonyColor.Gray2,
    error = HarmonyColor.Error,
    onError = HarmonyColor.White,
    errorContainer = HarmonyColor.StatusErrBg,
    onErrorContainer = HarmonyColor.Error
)

/** HarmonyOS 深色配色方案 */
private val DarkColors = darkColorScheme(
    primary = HarmonyColor.BrandOrangeDark,
    onPrimary = Color(0xFF001A33),
    primaryContainer = Color(0xFF003366),
    onPrimaryContainer = HarmonyColor.BrandOrangeDark,
    secondary = HarmonyColor.BrandBlue,
    onSecondary = HarmonyColor.White,
    tertiary = HarmonyColor.SuccessDark,
    background = HarmonyColor.DarkBG,
    onBackground = HarmonyColor.DarkTextPrimary,
    surface = HarmonyColor.DarkCard,
    onSurface = HarmonyColor.DarkTextPrimary,
    surfaceVariant = HarmonyColor.DarkTertiary,
    onSurfaceVariant = HarmonyColor.DarkTextSecondary,
    outline = HarmonyColor.DarkDivider,
    outlineVariant = HarmonyColor.DarkDivider,
    error = HarmonyColor.ErrorDark,
    onError = Color(0xFF330000),
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = HarmonyColor.ErrorDark
)

/** HarmonyOS 字号阶梯（HarmonyOS Sans 风格） */
private val HarmonyTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
)

@Composable
fun AutoSkipTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = HarmonyTypography,
        content = content
    )
}
