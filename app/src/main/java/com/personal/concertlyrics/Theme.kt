package com.personal.concertlyrics

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Same look as the Expense Tracker app: flat white page, thin hairlines, one blue accent.
// Colour is used for meaning, not decoration — no filled grey boxes.
private val Blue = Color(0xFF1F6FEB)
private val BlueDark = Color(0xFF5B95FF)

private val Light = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FF),
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAF2FF),
    onSecondaryContainer = Color(0xFF0B3D91),
    tertiary = Blue,
    background = Color.White,
    onBackground = Color(0xFF1B1D24),
    surface = Color.White,
    onSurface = Color(0xFF1B1D24),
    surfaceVariant = Color(0xFFF4F5F7),
    onSurfaceVariant = Color(0xFF7C7E8C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHigh = Color(0xFFEEEFF2),
    surfaceContainerHighest = Color(0xFFE6E7EB),
    outline = Color(0xFFD6D8DE),
    outlineVariant = Color(0xFFEBECEF),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFFEDEE),
    onErrorContainer = Color(0xFF6B0F13),
)

private val Dark = darkColorScheme(
    primary = BlueDark,
    onPrimary = Color(0xFF071C40),
    primaryContainer = Color(0xFF14243F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = BlueDark,
    onSecondary = Color(0xFF071C40),
    secondaryContainer = Color(0xFF14243F),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = BlueDark,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFEDEEF0),
    surfaceVariant = Color(0xFF1E1F22),
    onSurfaceVariant = Color(0xFF9A9CA6),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1E1F22),
    surfaceContainerHigh = Color(0xFF26272B),
    surfaceContainerHighest = Color(0xFF2E3034),
    outline = Color(0xFF3A3C42),
    outlineVariant = Color(0xFF26272B),
    error = Color(0xFFFF6B6F),
    onError = Color(0xFF4A0A0D),
    errorContainer = Color(0xFF3A1214),
    onErrorContainer = Color(0xFFFFDADB),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Immutable
data class ExtraColors(val good: Color, val warn: Color)

private val LightExtra = ExtraColors(good = Color(0xFF00A86B), warn = Color(0xFFC77700))
private val DarkExtra = ExtraColors(good = Color(0xFF2FD38E), warn = Color(0xFFFAB219))

val LocalExtraColors = staticCompositionLocalOf { LightExtra }

object Extra {
    val colors: ExtraColors @Composable get() = LocalExtraColors.current
}

@Composable
fun ConcertTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) Dark else Light,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

// ---------------------------------------------------------------- shared building blocks

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant)

/** A flat section: a hairline on top, a title, then content. No filled box. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hairline(Modifier.padding(bottom = 6.dp))
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
        }
        content()
    }
}

/** A thin-bordered tile for notices. */
@Composable
fun OutlinedTile(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, borderColor, MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/** A small outlined label, e.g. "6/6 shows". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
    )
}
