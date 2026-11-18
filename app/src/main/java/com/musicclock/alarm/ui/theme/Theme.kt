package com.musicclock.alarm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5A49),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDD),
    onPrimaryContainer = Color(0xFF0C2D23),
    secondary = Color(0xFF3C5F72),
    secondaryContainer = Color(0xFFD9E8F1),
    tertiary = Color(0xFF8A5A2B),
    background = Color(0xFFF5F3EE),
    surface = Color(0xFFF5F3EE),
    surfaceVariant = Color(0xFFE8E4DA),
    outline = Color(0xFF77766F),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD4BE),
    onPrimary = Color(0xFF0C2D23),
    primaryContainer = Color(0xFF244D40),
    onPrimaryContainer = Color(0xFFCDEBDD),
    secondary = Color(0xFFAFCBDA),
    secondaryContainer = Color(0xFF294C5D),
    tertiary = Color(0xFFF2C08D),
    background = Color(0xFF121513),
    surface = Color(0xFF121513),
    surfaceVariant = Color(0xFF252A27),
    outline = Color(0xFF91938D),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 56.sp,
        lineHeight = 60.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun MusicClockTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

