package com.aura.finanzas.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * Tarjeta de vidrio: relleno translúcido, borde luminoso y, si [tint] no es nulo, un velo del color dado.
 * [level] 0–2 sube la opacidad para marcar la jerarquía de elevación.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(28.dp),
  tint: Color? = null,
  level: Int = 1,
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val dark = LocalFin.current.isDark
  val boost = level * 0.035f
  // En AMOLED el vidrio es apenas un gris casi negro, para que la pantalla siga siendo negra.
  val glass = if (LocalFin.current.amoled) 0.55f else 1f
  val fill = if (dark) Brush.linearGradient(listOf(Color.White.copy((0.10f + boost) * glass), Color.White.copy((0.035f + boost) * glass)))
  else Brush.linearGradient(listOf(Color.White.copy(0.74f + boost * 2), Color.White.copy(0.46f + boost * 2)))
  val edge = if (dark) Brush.linearGradient(listOf(Color.White.copy(0.34f), Color.White.copy(0.03f), Color.White.copy(0.12f)))
  else Brush.linearGradient(listOf(Color.White, Color.White.copy(0.35f), Color.White.copy(0.8f)))
  val interaction = remember { MutableInteractionSource() }
  val haptic = LocalHapticFeedback.current
  var m = modifier.pressScale(interaction, enabled = onClick != null).clip(shape).background(fill)
  if (tint != null) {
    m = m.background(Brush.linearGradient(listOf(tint.copy(if (dark) 0.42f else 0.30f), tint.copy(0.06f))))
  }
  m = m.border(1.dp, edge, shape)
  if (onClick != null) {
    m = m.clickable(interaction, ripple()) {
      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      onClick()
    }
  }
  Column(m, content = content)
}

/** Encoge un poco el elemento mientras se mantiene presionado. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, enabled: Boolean = true, to: Float = 0.965f): Modifier {
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed && enabled) to else 1f, Motion.spatialFast(), label = "press")
  return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Entrada escalonada: cada elemento sube y aparece con un pequeño retraso según su [index]. */
@Composable
fun Modifier.enterStagger(index: Int): Modifier {
  val p = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay(index * 95L)
    p.animateTo(1f, Motion.spatial())
  }
  return graphicsLayer {
    alpha = p.value.coerceIn(0f, 1f)
    translationY = (1f - p.value) * 44.dp.toPx()
    val s = 0.94f + 0.06f * p.value
    scaleX = s; scaleY = s
  }
}

