package com.aura.finanzas.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

private const val RISE_MS = 680
private val riseEasing = CubicBezierEasing(0.22f, 0.82f, 0.2f, 1f)

/**
 * Punto de la pantalla (en fracciones de ancho y alto) desde el que nace la próxima pantalla: la pestaña o el
 * botón que se tocó. Por defecto, el centro del borde inferior.
 */
var revealOrigin by mutableStateOf(Offset(0.5f, 1f))

/**
 * Transición entre pantallas, igual a la de abrir una cuenta: la pantalla nueva nace como una tarjeta de color
 * en el punto que se tocó, crece hasta ocupar todo —precedida por dos capas tenues, como una estela— y su
 * contenido aparece mientras el color se desvanece.
 *
 * La pantalla que sale se recorta por fuera de esa misma tarjeta. Como las dos usan el mismo reloj, el borde
 * coincide sin importar cuál queda encima (al avanzar o al volver atrás).
 */
@Composable
fun AnimatedVisibilityScope.WaveScreen(content: @Composable () -> Unit) {
  val progress by transition.animateFloat({ tween(RISE_MS, easing = riseEasing) }, label = "nace") { if (it == EnterExitState.Visible) 1f else 0f }
  val exiting = transition.targetState == EnterExitState.PostExit
  val entering = !exiting && transition.currentState == EnterExitState.PreEnter
  val color = MaterialTheme.colorScheme.primary
  // Las tres formas se reutilizan en cada fotograma en lugar de crear objetos nuevos (menos trabajo de memoria).
  val paths = remember { Array(3) { Path() } }

  Box(
    Modifier.fillMaxSize().drawWithContent {
      val screen = this
      if (!entering && !exiting) {
        drawContent()
        return@drawWithContent
      }
      val t = if (entering) progress else 1f - progress
      val origin = revealOrigin
      val radius = 44.dp.toPx()
      val card = card(paths[0], size, t, origin, radius)
      if (exiting) {
        clipPath(card, ClipOp.Difference) { screen.drawContent() }
      } else {
        val fade = (1f - t).coerceIn(0f, 1f)
        if (fade > 0.01f) {
          drawPath(card(paths[1], size, (t + 0.16f).coerceAtMost(1f), origin, radius), color.copy(alpha = 0.16f * fade))
          drawPath(card(paths[2], size, (t + 0.08f).coerceAtMost(1f), origin, radius), color.copy(alpha = 0.30f * fade))
        }
        clipPath(card) {
          // El contenido llega con un pequeño desplazamiento desde el punto de origen.
          translate(top = (origin.y - 0.5f) * 2f * fade * 28.dp.toPx()) { screen.drawContent() }
          drawRect(color.copy(alpha = (1f - t * 1.7f).coerceIn(0f, 1f)))
        }
      }
    },
  ) { content() }
}

/** Tarjeta redondeada que crece desde [origin] (t = 0) hasta cubrir la pantalla (t = 1). */
private fun card(path: Path, size: Size, t: Float, origin: Offset, radius: Float): Path {
  val w = size.width
  val h = size.height
  val cx = origin.x * w
  val cy = origin.y * h
  val left = lerp(cx - w * 0.16f, 0f, t).coerceAtLeast(0f)
  val right = lerp(cx + w * 0.16f, w, t).coerceAtMost(w)
  val top = lerp(cy - h * 0.035f, 0f, t).coerceAtLeast(0f)
  val bottom = lerp(cy + h * 0.035f, h, t).coerceAtMost(h)
  val r = radius * (1f - t * t)
  path.rewind()
  path.addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(r, r)))
  return path
}
