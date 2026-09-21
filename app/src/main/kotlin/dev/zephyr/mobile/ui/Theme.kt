package dev.zephyr.mobile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The desktop build's tokens, unchanged. Light only, by design: the whole look
 * rests on a paper canvas with one blue slab on it, and a dark variant would be
 * a different design rather than a tint of this one.
 */
object Z {
    val blue = Color(0xFF3157F4)
    val blueDark = Color(0xFF2341BF)
    val bluePale = Color(0xFFE9EDFF)
    val blueLine = Color(0xFFC6D0FF)

    val paper = Color(0xFFFBFBF8)
    val card = Color(0xFFFFFFFF)
    val hover = Color(0xFFF2F2EE)

    val ink = Color(0xFF151922)
    val muted = Color(0xFF676E7B)
    val faint = Color(0xFF99A0AC)

    val line = Color(0xFFE6E6E0)
    val lineIn = Color(0xFFEFEFEA)
    val lineDark = Color(0xFFD2D4DA)

    val green = Color(0xFF16854D)
    val orange = Color(0xFFD97828)
    val red = Color(0xFFD73B49)

    val radius = 10.dp
    val radiusSm = 7.dp

    val gutter = 16.dp
}

/** Monospace, uppercase, wide tracking: the desktop build's section kickers. */
val KickerStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.6.sp,
)

val MonoSmall = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
)

private val zephyrTypography = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 14.sp, color = Z.ink),
        bodyMedium = base.bodyMedium.copy(fontSize = 13.sp, color = Z.ink),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, color = Z.muted),
    )
}

@Composable
fun ZephyrTheme(content: @Composable () -> Unit) {
    // The palette is fixed. A dark variant would be a different design rather
    // than a tint of this one, so the system setting is deliberately ignored.
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Z.blue,
            onPrimary = Color.White,
            primaryContainer = Z.bluePale,
            onPrimaryContainer = Z.blueDark,
            background = Z.paper,
            onBackground = Z.ink,
            surface = Z.card,
            onSurface = Z.ink,
            surfaceVariant = Z.hover,
            onSurfaceVariant = Z.muted,
            outline = Z.lineDark,
            outlineVariant = Z.line,
            error = Z.red,
        ),
        typography = zephyrTypography,
        content = content,
    )
}
