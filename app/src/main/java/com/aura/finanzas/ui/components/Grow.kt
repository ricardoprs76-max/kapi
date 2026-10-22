package com.aura.finanzas.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/**
 * Rectángulo (en coordenadas de la raíz) desde el que debe crecer la próxima hoja de formulario. Si una hoja se
 * abre con este valor, en lugar de subir desde abajo nace del botón que se tocó, como el análisis de una cuenta.
 */
val LocalGrowOrigin = staticCompositionLocalOf<Rect?> { null }

/**
 * Ventana que crece desde [origin] (el botón o la tarjeta que se tocó) hasta ocupar casi toda la pantalla y, al
 * cerrar, vuelve a encogerse hacia allí. El fondo se oscurece mientras tanto y el contenido aparece cuando la
 * ventana ya casi llegó.
 *
 * Con [card] = true es exactamente la animación del análisis de una cuenta (y la del detalle de un tipo de
 * activo): una tarjeta que se estira hasta volverse una hoja que deja ver arriba un poco de la pantalla.
 */
@Composable
fun GrowOverlay(
  origin: Rect,
  onClosed: () -> Unit,
  tint: Brush? = null,
  card: Boolean = false,
  content: @Composable (close: () -> Unit) -> Unit,
) {
  val progress = remember { Animatable(0f) }
  var closing by remember { mutableStateOf(false) }
  LaunchedEffect(closing) {
    if (closing) {
      progress.animateTo(0f, tween(if (card) 620 else 560, easing = FastOutSlowInEasing))
      onClosed()
    } else {
      progress.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 85f))
    }
  }
  BackHandler { closing = true }
  val close = { closing = true }
  val surface = if (card) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainer
  val brush = tint ?: Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(0.8f)))
  val density = LocalDensity.current
  val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val w = constraints.maxWidth.toFloat()
    val h = constraints.maxHeight.toFloat()
    val target = if (card) Rect(0f, h * 0.13f, w, h) else Rect(0f, statusTop + with(density) { 10.dp.toPx() }, w, h)
    val from = when {
      !origin.isEmpty -> origin
      card -> Rect(w * 0.3f, h, w * 0.7f, h * 1.1f)
      else -> Rect(w * 0.4f, h * 0.9f, w * 0.6f, h)
    }
    Box(
      Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * progress.value.coerceIn(0f, 1f))) }
        .clickable(remember { MutableInteractionSource() }, null) { closing = true },
    )
    Box(
      Modifier
        .layout { measurable, _ ->
          val t = progress.value
          val cw = lerp(from.width, target.width, t).roundToInt().coerceAtLeast(1)
          val ch = lerp(from.height, target.height, t).roundToInt().coerceAtLeast(1)
          val placeable = measurable.measure(Constraints.fixed(cw, ch))
          layout(cw, ch) { placeable.place(lerp(from.left, target.left, t).roundToInt(), lerp(from.top, target.top, t).roundToInt()) }
        }
        .graphicsLayer {
          val t = progress.value.coerceIn(0f, 1f)
          // Arranca redonda como el botón y termina con las esquinas de arriba redondeadas.
          val start = if (card) 24.dp.toPx() else minOf(from.width, from.height) / 2f
          val r = lerp(start, if (card) 38.dp.toPx() else 32.dp.toPx(), t)
          shape = RoundedCornerShape(topStart = r, topEnd = r, bottomEnd = r * (1f - t), bottomStart = r * (1f - t))
          clip = true
        }
        .drawBehind {
          drawRect(surface)
          drawRect(brush, alpha = (1f - progress.value * (if (card) 1.5f else 1.6f)).coerceIn(0f, 1f))
        }
        .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
      Box(
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
          .requiredSize(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
          .graphicsLayer {
            val t = if (card) ((progress.value - 0.4f) / 0.5f).coerceIn(0f, 1f) else ((progress.value - 0.45f) / 0.45f).coerceIn(0f, 1f)
            alpha = t
            translationY = (1f - t) * (if (card) 36 else 30).dp.toPx()
          },
      ) { content(close) }
    }
  }
}
