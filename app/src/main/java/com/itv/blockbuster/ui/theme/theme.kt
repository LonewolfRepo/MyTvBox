package com.itv.blockbuster.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BbDarkColorScheme = darkColorScheme(
    primary = BbAccent,
    onPrimary = BbTextPrimary,
    secondary = BbAccentLight,
    background = BbBackground,
    surface = BbSurface,
    onBackground = BbTextPrimary,
    onSurface = BbTextPrimary,
    error = BbDestructive
)

@Composable
fun BlockbusterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BbDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}