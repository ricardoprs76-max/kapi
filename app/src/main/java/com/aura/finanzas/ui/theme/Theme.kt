package com.aura.finanzas.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta definida por su tono (0–360); el resto de colores se derivan para claro y oscuro. */
data class Palette(val name: String, val hue: Float, val lightL: Float = 0.44f)

val Palettes = listOf(
  Palette("Púrpura", 262f),
  Palette("Verde", 152f, 0.32f),
  Palette("Azul", 218f),
  Palette("Naranja", 24f, 0.42f),
  Palette("Rosa", 332f),
  Palette("Amarillo", 44f, 0.34f),
  Palette("Rojo", 2f),
)

/** Colores fijos de significado financiero (semáforo), iguales en todas las paletas. */
data class FinColors(
  val income: Color,
  val expense: Color,
  val ok: Color,
  val warn: Color,
  val danger: Color,
  val isDark: Boolean,
  val seed: Color,
  /** Modo AMOLED: fondo negro puro, decoraciones del fondo y vidrio más tenues. */
  val amoled: Boolean = false,
)

val LocalFin = staticCompositionLocalOf {
  FinColors(Color.Green, Color.Red, Color.Green, Color.Yellow, Color.Red, true, Color.Magenta)
}

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s, l)

/** Color sólido de una paleta, para chips de cuentas y categorías. */
fun paletteColor(idx: Int, dark: Boolean): Color {
  val p = Palettes[((idx % Palettes.size) + Palettes.size) % Palettes.size]
  return if (dark) hsl(p.hue, 0.82f, 0.70f) else hsl(p.hue, 0.72f, p.lightL + 0.06f)
}

// Degradados vivos de dos tonos, en el orden de los colores que se eligen para la cuenta:
// púrpura, verde, azul, naranja, rosa, amarillo y rojo.
private val accountGradients = listOf(
  listOf(Color(0xFF5A1FD8), Color(0xFFA23DFF), Color(0xFFD08BFF)),
  listOf(Color(0xFF1746E0), Color(0xFF12A5A0), Color(0xFF2FD67B)),
  listOf(Color(0xFF0F5BD0), Color(0xFF16A6E6), Color(0xFF5CEFEA)),
  listOf(Color(0xFFFF3D00), Color(0xFFFF6A1F), Color(0xFFFFA24D)),
  listOf(Color(0xFFFF4A1C), Color(0xFFFF5A8C), Color(0xFFE77BF5)),
  listOf(Color(0xFFFF7A00), Color(0xFFFFAE1F), Color(0xFFFFD257)),
  listOf(Color(0xFF4A0F92), Color(0xFFB5246F), Color(0xFFFF5A5F)),
)

/** Colores del degradado de la tarjeta de una cuenta, según el color que se le eligió. */
fun accountColors(idx: Int): List<Color> = accountGradients[((idx % accountGradients.size) + accountGradients.size) % accountGradients.size]

/** Colores vivos y bien distintos entre sí para la flor de distribución, en el orden en que se reparten. */
val petalColors = listOf(
  Color(0xFFFF5A1F), Color(0xFFFF2D55), Color(0xFFBF3FD9), Color(0xFF5B4FE9),
  Color(0xFF1E88F5), Color(0xFF12B8A6), Color(0xFF6DBE2E), Color(0xFFFFB300),
)

fun accountBrush(idx: Int): Brush = Brush.linearGradient(accountColors(idx))

/**
 * Modo AMOLED: el oscuro con fondo negro puro. En pantallas OLED/AMOLED los píxeles negros van apagados, así que
 * ahorra batería; las tarjetas y hojas usan grises casi negros para seguir distinguiéndose del fondo.
 */
private fun amoledScheme(p: Palette): ColorScheme {
  val h = p.hue
  return auraScheme(p, true).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = hsl(h, 0.10f, 0.035f),
    surfaceContainer = hsl(h, 0.12f, 0.06f),
    surfaceContainerHigh = hsl(h, 0.12f, 0.085f),
    surfaceContainerHighest = hsl(h, 0.12f, 0.12f),
    surfaceVariant = hsl(h, 0.14f, 0.13f),
    outlineVariant = hsl(h, 0.12f, 0.20f),
  )
}

fun auraScheme(p: Palette, dark: Boolean): ColorScheme {
  val h = p.hue
  return if (dark) darkColorScheme(
    primary = hsl(h, 0.88f, 0.76f),
    onPrimary = hsl(h, 0.70f, 0.13f),
    primaryContainer = hsl(h, 0.52f, 0.27f),
    onPrimaryContainer = hsl(h, 0.95f, 0.91f),
    secondary = hsl(h + 28f, 0.42f, 0.74f),
    onSecondary = hsl(h + 28f, 0.45f, 0.14f),
    secondaryContainer = hsl(h + 28f, 0.28f, 0.24f),
    onSecondaryContainer = hsl(h + 28f, 0.60f, 0.90f),
    tertiary = hsl(h + 58f, 0.72f, 0.74f),
    onTertiary = hsl(h + 58f, 0.60f, 0.13f),
    tertiaryContainer = hsl(h + 58f, 0.42f, 0.26f),
    onTertiaryContainer = hsl(h + 58f, 0.85f, 0.90f),
    background = hsl(h, 0.30f, 0.055f),
    onBackground = hsl(h, 0.20f, 0.94f),
    surface = hsl(h, 0.30f, 0.055f),
    onSurface = hsl(h, 0.20f, 0.94f),
    surfaceVariant = hsl(h, 0.18f, 0.19f),
    onSurfaceVariant = hsl(h, 0.14f, 0.76f),
    surfaceContainerLowest = hsl(h, 0.30f, 0.04f),
    surfaceContainerLow = hsl(h, 0.24f, 0.085f),
    surfaceContainer = hsl(h, 0.22f, 0.11f),
    surfaceContainerHigh = hsl(h, 0.20f, 0.14f),
    surfaceContainerHighest = hsl(h, 0.18f, 0.18f),
    outline = hsl(h, 0.10f, 0.50f),
    outlineVariant = hsl(h, 0.14f, 0.25f),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF50100C),
  ) else lightColorScheme(
    primary = hsl(h, 0.74f, p.lightL),
    onPrimary = Color.White,
    primaryContainer = hsl(h, 0.92f, 0.90f),
    onPrimaryContainer = hsl(h, 0.80f, 0.17f),
    secondary = hsl(h + 28f, 0.34f, 0.38f),
    onSecondary = Color.White,
    secondaryContainer = hsl(h + 28f, 0.60f, 0.90f),
    onSecondaryContainer = hsl(h + 28f, 0.50f, 0.16f),
    tertiary = hsl(h + 58f, 0.62f, 0.36f),
    onTertiary = Color.White,
    tertiaryContainer = hsl(h + 58f, 0.85f, 0.89f),
    onTertiaryContainer = hsl(h + 58f, 0.70f, 0.15f),
    background = hsl(h, 0.60f, 0.972f),
    onBackground = hsl(h, 0.35f, 0.10f),
    surface = hsl(h, 0.60f, 0.972f),
    onSurface = hsl(h, 0.35f, 0.10f),
    surfaceVariant = hsl(h, 0.36f, 0.90f),
    onSurfaceVariant = hsl(h, 0.16f, 0.32f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = hsl(h, 0.55f, 0.96f),
    surfaceContainer = hsl(h, 0.50f, 0.945f),
    surfaceContainerHigh = hsl(h, 0.45f, 0.925f),
    surfaceContainerHighest = hsl(h, 0.40f, 0.90f),
    outline = hsl(h, 0.10f, 0.50f),
    outlineVariant = hsl(h, 0.24f, 0.82f),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
  )
}

private val base = Typography()
private val AuraType = Typography(
  displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp),
  displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
  displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
  headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
  headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
  titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
  titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val AuraShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(36.dp),
)

/** Resortes suaves y pausados: movimiento con un rebote leve, efectos (color/alfa) sin rebote. */
object Motion {
  fun <T> spatial() = spring<T>(dampingRatio = 0.80f, stiffness = 170f)
  fun <T> spatialBouncy() = spring<T>(dampingRatio = 0.60f, stiffness = 150f)
  fun <T> spatialFast() = spring<T>(dampingRatio = 0.85f, stiffness = 520f)
  fun <T> effects() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 240f)
}

@Composable
fun AuraTheme(paletteIdx: Int, dark: Boolean, amoled: Boolean = false, content: @Composable () -> Unit) {
  val palette = Palettes[paletteIdx.coerceIn(0, Palettes.lastIndex)]
  val scheme = if (dark && amoled) amoledScheme(palette) else auraScheme(palette, dark)
  val fin = if (dark) FinColors(
    income = Color(0xFF4ADE80), expense = Color(0xFFFF7B72), ok = Color(0xFF4ADE80),
    warn = Color(0xFFFFD24A), danger = Color(0xFFFF6B6B), isDark = true, seed = scheme.primary, amoled = amoled,
  ) else FinColors(
    income = Color(0xFF15803D), expense = Color(0xFFC62828), ok = Color(0xFF16A34A),
    warn = Color(0xFFD99A00), danger = Color(0xFFDC2626), isDark = false, seed = scheme.primary,
  )
  CompositionLocalProvider(LocalFin provides fin) {
    MaterialTheme(colorScheme = scheme, typography = AuraType, shapes = AuraShapes, content = content)
  }
}
