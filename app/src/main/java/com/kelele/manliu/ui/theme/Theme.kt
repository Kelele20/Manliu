package com.kelele.manliu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = Ink,
    onSurface = Ink,
    surfaceVariant = AccentSoft,
)

@Composable
fun ManliuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
