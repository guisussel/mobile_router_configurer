package com.roteador.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.roteador.core.model.Quality

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E3FF),
    onPrimaryContainer = Color(0xFF001A42),
    secondary = Color(0xFF4F5F7D),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE5E8F0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD8E3FF),
    secondary = Color(0xFFB7C7E9),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
)

/** Colors that carry meaning (healthy / attention / problem), tuned per theme. */
@Immutable
data class StatusColors(val good: Color, val warning: Color, val critical: Color) {
    fun of(quality: Quality): Color = when (quality) {
        Quality.Good -> good
        Quality.Warning -> warning
        Quality.Critical -> critical
    }
}

private val LightStatus = StatusColors(good = Color(0xFF1B7F3B), warning = Color(0xFFB26A00), critical = Color(0xFFBA1A1A))
private val DarkStatus = StatusColors(good = Color(0xFF7DDA93), warning = Color(0xFFFFB95C), critical = Color(0xFFFFB4AB))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun RoteadorTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}

object RoteadorTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
