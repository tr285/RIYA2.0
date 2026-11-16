package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class RiyaExtendedColors(
    val isDark: Boolean,
    val bgBase: Color,
    val bgSurface: Color,
    val bgSurfaceElevated: Color,
    val bgCard: Color,
    val bgInput: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val userBubbleBg: Color,
    val userBubbleFg: Color,
    val assistantBubbleBg: Color,
    val assistantBubbleFg: Color,
    val codeBg: Color,
    val codeFg: Color,
    val statusSuccessBg: Color,
    val statusSuccessFg: Color,
    val statusWarningBg: Color,
    val statusWarningFg: Color,
    val statusErrorBg: Color,
    val statusErrorFg: Color,
    val statusInfoBg: Color,
    val statusInfoFg: Color,
    val waveformActive: Color,
    val waveformIdle: Color,
    val waveformMicLive: Color,
    val waveformMicPaused: Color,
    val waveformMicOff: Color
)

val RiyaDarkExtended = RiyaExtendedColors(
    isDark = true,
    bgBase = Color(0xFF0D1117),
    bgSurface = Color(0xFF161B22),
    bgSurfaceElevated = Color(0xFF21262D),
    bgCard = Color(0xFF1C2128),
    bgInput = Color(0xFF0D1117),
    border = Color(0xFF30363D),
    textPrimary = Color(0xFFF0F6FC),
    textSecondary = Color(0xFF8B949E),
    textMuted = Color(0xFF6E7681),
    accent = Color(0xFF2F81F7),
    userBubbleBg = Color(0xFF1F6FEB),
    userBubbleFg = Color(0xFFFFFFFF),
    assistantBubbleBg = Color(0xFF161B22),
    assistantBubbleFg = Color(0xFFF0F6FC),
    codeBg = Color(0xFF090D13),
    codeFg = Color(0xFFE6EDF3),
    statusSuccessBg = Color(0xFF172B1D),
    statusSuccessFg = Color(0xFF3FB950),
    statusWarningBg = Color(0xFF342813),
    statusWarningFg = Color(0xFFD29922),
    statusErrorBg = Color(0xFF351A1A),
    statusErrorFg = Color(0xFFF85149),
    statusInfoBg = Color(0xFF14263B),
    statusInfoFg = Color(0xFF58A6FF),
    waveformActive = Color(0xFF38BDF8),
    waveformIdle = Color(0xFF475569),
    waveformMicLive = Color(0xFF22C55E),
    waveformMicPaused = Color(0xFFEAB308),
    waveformMicOff = Color(0xFF64748B)
)

val RiyaLightExtended = RiyaExtendedColors(
    isDark = false,
    bgBase = Color(0xFFF6F8FA),
    bgSurface = Color(0xFFFFFFFF),
    bgSurfaceElevated = Color(0xFFF0F2F5),
    bgCard = Color(0xFFFFFFFF),
    bgInput = Color(0xFFFFFFFF),
    border = Color(0xFFD0D7DE),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF656D76),
    textMuted = Color(0xFF8C959F),
    accent = Color(0xFF0969DA),
    userBubbleBg = Color(0xFF0969DA),
    userBubbleFg = Color(0xFFFFFFFF),
    assistantBubbleBg = Color(0xFFFFFFFF),
    assistantBubbleFg = Color(0xFF1F2328),
    codeBg = Color(0xFFF6F8FA),
    codeFg = Color(0xFF24292F),
    statusSuccessBg = Color(0xFFDAFBE1),
    statusSuccessFg = Color(0xFF1A7F37),
    statusWarningBg = Color(0xFFFFF8C5),
    statusWarningFg = Color(0xFF9A6700),
    statusErrorBg = Color(0xFFFFEBE9),
    statusErrorFg = Color(0xFFCF222E),
    statusInfoBg = Color(0xFFDDF4FF),
    statusInfoFg = Color(0xFF0969DA),
    waveformActive = Color(0xFF0284C7),
    waveformIdle = Color(0xFF94A3B8),
    waveformMicLive = Color(0xFF16A34A),
    waveformMicPaused = Color(0xFFCA8A04),
    waveformMicOff = Color(0xFF94A3B8)
)

val LocalRiyaColors = staticCompositionLocalOf { RiyaDarkExtended }

private val DarkMaterialScheme = darkColorScheme(
    primary = Color(0xFF2F81F7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF14263B),
    onPrimaryContainer = Color(0xFF58A6FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
    onError = Color(0xFFFFFFFF)
)

private val LightMaterialScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0969DA),
    secondary = Color(0xFF0284C7),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun RiyaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val extended = if (darkTheme) RiyaDarkExtended else RiyaLightExtended
    val colorScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme

    CompositionLocalProvider(LocalRiyaColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RiyaTypography,
            content = content
        )
    }
}
