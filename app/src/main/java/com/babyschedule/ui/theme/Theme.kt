package com.babyschedule.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF8E4A6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E7),
    onPrimaryContainer = Color(0xFF3A0725),
    secondary = Color(0xFF5E5C9C),
    secondaryContainer = Color(0xFFE2DFFF),
    onSecondaryContainer = Color(0xFF1A1856),
    tertiary = Color(0xFF00696B),
    tertiaryContainer = Color(0xFFB4EFEF),
    background = Color(0xFFFFF8F8),
    surface = Color(0xFFFFF8F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB0D0),
    onPrimary = Color(0xFF55193B),
    primaryContainer = Color(0xFF713352),
    onPrimaryContainer = Color(0xFFFFD8E7),
    secondary = Color(0xFFC4C1FF),
    secondaryContainer = Color(0xFF464482),
    onSecondaryContainer = Color(0xFFE2DFFF),
    tertiary = Color(0xFF82D4D5),
    tertiaryContainer = Color(0xFF004F51),
)

/** Fixed colours for chart series so they stay consistent across screens. */
object SeriesColors {
    val Left = Color(0xFFEC6F9E)
    val Right = Color(0xFF8C6BDB)
    val Bottle = Color(0xFFF5A33B)
    val Wet = Color(0xFF3FA3E8)
    val Dirty = Color(0xFFA5795C)
    val Mixed = Color(0xFF7CB342)
    val Sleep = Color(0xFF5C6BC0)
    val Weight = Color(0xFF26A69A)
}

@Composable
fun BabyScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off so the app always uses its own primary/secondary colours (the phone's
    // wallpaper colours can make primary and secondary buttons look the same).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
