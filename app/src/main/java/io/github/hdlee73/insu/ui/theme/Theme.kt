package io.github.hdlee73.insu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* hdlee73/FSS_insights 톤: 연한 하늘색 바탕, 흰 카드, 둥근 모서리, 파랑 강조, 주황 보조. */

private val LightColors = lightColorScheme(
    primary = Color(0xFF2A7DE1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFC),
    onPrimaryContainer = Color(0xFF1A5FB4),
    secondary = Color(0xFF47638A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF1F5),
    onSecondaryContainer = Color(0xFF232A36),
    tertiary = Color(0xFFF08A4B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFCEDE5),
    onTertiaryContainer = Color(0xFFA8461A),
    background = Color(0xFFF2F7FC),
    onBackground = Color(0xFF1D2B3F),
    surface = Color(0xFFF2F7FC),
    onSurface = Color(0xFF1D2B3F),
    surfaceVariant = Color(0xFFE6EEF8),
    onSurfaceVariant = Color(0xFF64778F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF2FB),
    surfaceContainerHighest = Color(0xFFE3ECF6),
    outline = Color(0xFFA3AAB5),
    outlineVariant = Color(0xFFE3ECF6),
    error = Color(0xFFC9372C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB5E6),
    onPrimary = Color(0xFF0E2048),
    primaryContainer = Color(0xFF1F2A40),
    onPrimaryContainer = Color(0xFFDCE6FA),
    secondary = Color(0xFFA9B2C0),
    secondaryContainer = Color(0xFF262B33),
    onSecondaryContainer = Color(0xFFE6E9EE),
    tertiary = Color(0xFFFF9A72),
    onTertiary = Color(0xFF3A1200),
    tertiaryContainer = Color(0xFF3A2218),
    onTertiaryContainer = Color(0xFFFFB59A),
    background = Color(0xFF0C0E11),
    onBackground = Color(0xFFF1F3F6),
    surface = Color(0xFF0C0E11),
    onSurface = Color(0xFFF1F3F6),
    surfaceVariant = Color(0xFF1A1E24),
    onSurfaceVariant = Color(0xFF9BA4B0),
    surfaceContainerLowest = Color(0xFF0F1216),
    surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF12151A),
    surfaceContainerHigh = Color(0xFF1A1E24),
    surfaceContainerHighest = Color(0xFF22272F),
    outline = Color(0xFF6B7380),
    outlineVariant = Color(0xFF252A32),
    error = Color(0xFFFF7A6E),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 머리말 그라데이션·경고색처럼 Material 색표에 없는 값. */
@Immutable
data class ExtraColors(
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val warning: Color,
    val warningContainer: Color,
    val success: Color,
    val successContainer: Color,
)

private val LightExtra = ExtraColors(
    heroStart = Color(0xFF4FB4FB), heroEnd = Color(0xFF2563EB), onHero = Color.White,
    warning = Color(0xFFE2622B), warningContainer = Color(0xFFFFF4EC),
    success = Color(0xFF0F8F6A), successContainer = Color(0xFFDDF5EA),
)
private val DarkExtra = ExtraColors(
    heroStart = Color(0xFF24457A), heroEnd = Color(0xFF1A2F63), onHero = Color.White,
    warning = Color(0xFFFF9A72), warningContainer = Color(0xFF3A2218),
    success = Color(0xFF5FD38D), successContainer = Color(0xFF14382A),
)

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

@Composable
fun InsuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
