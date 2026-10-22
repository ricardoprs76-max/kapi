package com.aura.finanzas.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import com.aura.finanzas.ui.theme.Palettes
import com.aura.finanzas.ui.theme.paletteColor

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Une este elemento con el de la misma [key] en la pantalla de destino (la tarjeta se transforma en el detalle). */
@Composable
fun Modifier.sharedCard(key: String): Modifier {
  val shared = LocalSharedScope.current ?: return this
  val anim = LocalAnimScope.current ?: return this
  return with(shared) {
    sharedBounds(rememberSharedContentState(key), anim, boundsTransform = BoundsTransform { _, _ -> Motion.spatial() })
  }
}

/** Selector visual de iconos: rejilla de iconos isométricos; el elegido crece y se resalta. */
@Composable
fun IconPicker(selected: String, tint: Color, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    isoIconNames.forEach { name ->
      val on = name == selected
      val scale by animateFloatAsState(if (on) 1.15f else 0.9f, Motion.spatialBouncy(), label = "icon")
      Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
          .background(if (on) tint.copy(0.22f) else scheme.onSurface.copy(0.05f))
          .border(if (on) 2.dp else 0.dp, if (on) tint else Color.Transparent, RoundedCornerShape(18.dp))
          .semantics { contentDescription = name }
          .clickable(remember { MutableInteractionSource() }, ripple(), role = Role.RadioButton) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(name)
          },
        contentAlignment = Alignment.Center,
      ) { IsoIcon(name, tint, Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, size = 38.dp) }
    }
  }
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Palettes.forEachIndexed { i, p ->
      val ring by animateDpAsState(if (i == selected) 3.dp else 0.dp, Motion.spatialBouncy(), label = "ring")
      Box(
        Modifier.size(42.dp).semantics { contentDescription = p.name }.clip(CircleShape)
          .clickable(remember { MutableInteractionSource() }, ripple(), role = Role.RadioButton) { onSelect(i) },
        contentAlignment = Alignment.Center,
      ) {
        Box(Modifier.size(34.dp).border(ring, scheme.onSurface, CircleShape).padding(ring + 2.dp).clip(CircleShape).background(paletteColor(i, dark)))
      }
    }
  }
}

/** Botón redondo de 48 dp con un glifo. */
@Composable
fun GlyphButton(glyph: Glyph, description: String, modifier: Modifier = Modifier, rotation: Float = 0f, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  Box(
    modifier.size(40.dp).clip(CircleShape).background(scheme.onSurface.copy(0.08f)).semantics { contentDescription = description }
      .clickable(remember { MutableInteractionSource() }, ripple(), role = Role.Button) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
      },
    contentAlignment = Alignment.Center,
  ) { GlyphIcon(glyph, scheme.onSurface, Modifier.graphicsLayer { rotationZ = rotation }) }
}

/** Línea suavizada con relleno degradado que se revela de izquierda a derecha. */
@Composable
fun LineChart(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
  val reveal = remember { Animatable(0f) }
  LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, Motion.spatial()) }
  Canvas(modifier) {
    if (values.size < 2) return@Canvas
    val min = values.min()
    val max = values.max()
    val range = (max - min).takeIf { it > 0f } ?: 1f
    val pad = 6.dp.toPx()
    val h = size.height - pad * 2
    val step = size.width / (values.size - 1)
    fun pt(i: Int) = Offset(i * step, if (max == min) size.height / 2 else pad + h * (1f - (values[i] - min) / range))
    val line = Path().apply {
      moveTo(pt(0).x, pt(0).y)
      for (i in 1 until values.size) {
        val a = pt(i - 1)
        val b = pt(i)
        val mid = (a.x + b.x) / 2
        cubicTo(mid, a.y, mid, b.y, b.x, b.y)
      }
    }
    val fill = Path().apply {
      addPath(line)
      lineTo(size.width, size.height)
      lineTo(0f, size.height)
      close()
    }
    clipRect(right = size.width * reveal.value.coerceIn(0f, 1f)) {
      drawPath(fill, Brush.verticalGradient(listOf(color.copy(0.35f), Color.Transparent)))
      drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    val last = pt(values.lastIndex)
    if (reveal.value > 0.97f) {
      drawCircle(color.copy(0.25f), 9.dp.toPx(), last)
      drawCircle(color, 4.5.dp.toPx(), last)
    }
  }
}
