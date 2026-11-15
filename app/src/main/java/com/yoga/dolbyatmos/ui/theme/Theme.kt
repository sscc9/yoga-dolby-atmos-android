package com.yoga.dolbyatmos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DolbyBgDeep = Color(0xFF070812)
val DolbyCardBg = Color(0xFF111428)
val DolbyCardActive = Color(0xFF1A2040)
val DolbyCyan = Color(0xFF00E5FF)
val DolbyViolet = Color(0xFF8C52FF)
val DolbyBlue = Color(0xFF2979FF)
val DolbyTextPrimary = Color(0xFFF3F6FF)
val DolbyTextSecondary = Color(0xFF9AA3C2)
val DolbyBorder = Color(0xFF232A4D)

private val DolbyColorScheme = darkColorScheme(
    primary = DolbyCyan,
    secondary = DolbyViolet,
    tertiary = DolbyBlue,
    background = DolbyBgDeep,
    surface = DolbyCardBg,
    onPrimary = Color(0xFF001F26),
    onSecondary = Color.White,
    onBackground = DolbyTextPrimary,
    onSurface = DolbyTextPrimary
)

@Composable
fun YogaDolbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DolbyColorScheme,
        content = content
    )
}
