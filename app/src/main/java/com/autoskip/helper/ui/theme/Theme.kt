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

/** HyperOS 浅色配色方案 */
private val LightColors = lightColorScheme(
    primary = HyperColor.BrandOrange,
    onPrimary = HyperColor.White,
    primaryContainer = HyperColor.IconOrangeBg,
    onPrimaryContainer = HyperColor.BrandOrange,
    secondary = HyperColor.BrandBlue,
    onSecondary = HyperColor.White,
    tertiary = HyperColor.Success,
    background = HyperColor.GrayBG,
    onBackground = HyperColor.TextPrimary,
    surface = HyperColor.White,
    onSurface = HyperColor.TextPrimary,
    surfaceVariant = HyperColor.Gray1,
    onSurfaceVariant = HyperColor.Gray6,
    outline = HyperColor.Gray2,
    outlineVariant = HyperColor.Gray2,
    error = HyperColor.Error,
    onError = HyperColor.White,
    errorContainer = HyperColor.StatusErrBg,
    onErrorContainer = HyperColor.Error
)

/** HyperOS 深色配色方案 */
private val DarkColors = darkColorScheme(
    primary = HyperColor.BrandOrangeDark,
    onPrimary = Color(0xFF3D1A00),
    primaryContainer = Color(0xFF4A2600),
    onPrimaryContainer = HyperColor.BrandOrangeDark,
    secondary = HyperColor.BrandBlue,
    onSecondary = HyperColor.White,
    tertiary = HyperColor.SuccessDark,
    background = HyperColor.DarkBG,
    onBackground = HyperColor.DarkTextPrimary,
    surface = HyperColor.DarkCard,
    onSurface = HyperColor.DarkTextPrimary,
    surfaceVariant = HyperColor.DarkTertiary,
    onSurfaceVariant = HyperColor.DarkTextSecondary,
    outline = HyperColor.DarkDivider,
    outlineVariant = HyperColor.DarkDivider,
    error = HyperColor.ErrorDark,
    onError = Color(0xFF3D0000),
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = HyperColor.ErrorDark
)

/** HyperOS 字号阶梯（MiSans 风格） */
private val HyperTypography = Typography(
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 14.sp)
)

@Composable
fun AutoSkipTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = HyperTypography,
        content = content
    )
}
