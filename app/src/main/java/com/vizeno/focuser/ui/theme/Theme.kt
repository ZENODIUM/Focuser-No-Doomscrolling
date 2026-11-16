package com.vizeno.focuser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = InkLight,
    onPrimary = Ink,
    background = PaperDark,
    surface = PaperDark,
    onBackground = InkLight,
    onSurface = InkLight,
)

private val LightColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = InkLight,
    background = Paper,
    surface = Paper,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun FocuserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
