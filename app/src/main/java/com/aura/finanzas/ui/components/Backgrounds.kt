package com.aura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.theme.LocalFin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Un fondo animado que se puede elegir en Ajustes. Los marcados [premium] son parte de Kapi Premium. */
class BgStyle(val name: String, val premium: Boolean)

/** El orden es el que se guarda en los ajustes: no reordenar, solo agregar al final. */
val bgStyles = listOf(
  BgStyle("Aura", false), BgStyle("Bolitas", false), BgStyle("Aurora", true),
  BgStyle("Ondas", true), BgStyle("Constelación", true), BgStyle("Burbujas", true),
)

private class Dot(val x: Float, val y: Float, val r: Float, val vx: Float, val vy: Float, val phase: Float, val color: Int, val alpha: Float)

private fun dots(seed: Int, count: Int): List<Dot> {
  val rnd = Random(seed)
  return List(count) {
    Dot(
      rnd.nextFloat(), rnd.nextFloat(), 1.4f + rnd.nextFloat().pow(2.4f) * 8.5f,
      (rnd.nextFloat() - 0.5f) * 0.014f, 0.006f + rnd.nextFloat() * 0.016f,
      rnd.nextFloat() * 6.283f, rnd.nextInt(5), 0.30f + rnd.nextFloat() * 0.55f,
    )
  }
}

/**
 * Fondo animado de la app, en el estilo [style] (índice de [bgStyles]). [burst] (de 1 a 0) empuja las partículas
 * de lado al cambiar de pantalla. El tiempo solo se lee en la fase de dibujo, así que no hay recomposición por
 * fotograma; con [animated] en false queda estático (ahorra batería).
 */
@Composable
fun AuraBackground(animated: Boolean, modifier: Modifier = Modifier, style: Int = 0, burst: () -> Float = { 0f }, paused: Boolean = false) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  val time = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(animated, paused) {
    if (!animated || paused) return@LaunchedEffect
    val start = withFrameNanos { it }
    val offset = time.floatValue
    // El fondo se mueve despacio: basta con redibujarlo unas 30 veces por segundo. Entre un cuadro y otro no se
    // pide ninguno a la pantalla, así que el teléfono descansa (o dedica ese tiempo a transiciones y desplazamientos).
    while (true) {
      withFrameNanos { time.floatValue = offset + (it - start) / 1_000_000_000f }
      delay(FRAME_MS)
    }
  }
  // Los degradados suaves se pintan en una capa 8 veces más pequeña y se amplían,
  // lo que reduce unas 64 veces los píxeles rellenados en cada fotograma.
  val lowRes = Modifier.layout { measurable, constraints ->
    val w = constraints.maxWidth
    val h = constraints.maxHeight
    val placeable = measurable.measure(Constraints.fixed((w / BG_SCALE).coerceAtLeast(1), (h / BG_SCALE).coerceAtLeast(1)))
    layout(w, h) {
      placeable.placeWithLayer(0, 0) {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = w.toFloat() / placeable.width
        scaleY = h.toFloat() / placeable.height
        compositingStrategy = CompositingStrategy.Offscreen
      }
    }
  }
  val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)
  val bg = scheme.background
  Box(modifier.fillMaxSize().background(bg)) {
    when (style) {
      1 -> Canvas(Modifier.fillMaxSize()) { bolitas(time.floatValue, bg, colors) }
      2 -> Canvas(Modifier.fillMaxSize().then(lowRes)) { aurora(time.floatValue, colors, dark) }
      3 -> Canvas(Modifier.fillMaxSize()) { ondas(time.floatValue, colors, dark) }
      4 -> {
        val points = remember { dots(23, 38) }
        Canvas(Modifier.fillMaxSize().then(lowRes)) { blobs(time.floatValue * 0.5f, colors, if (dark) 0.34f else 0.36f) }
        Canvas(Modifier.fillMaxSize()) { constelacion(time.floatValue, burst(), points, colors, if (dark) Color.White else scheme.onSurface) }
      }
      5 -> {
        val bubbles = remember { dots(5, 20) }
        Canvas(Modifier.fillMaxSize()) { burbujas(time.floatValue, burst(), bubbles, colors, dark) }
      }
      else -> {
        val particles = remember { dots(11, 78) }
        val dotColors = colors + listOf(Color(0xFFFF8A65), if (dark) Color.White else scheme.onSurface.copy(0.55f))
        Canvas(Modifier.fillMaxSize().then(lowRes)) { blobs(time.floatValue, colors, if (dark) 0.50f else 0.55f) }
        Canvas(Modifier.fillMaxSize()) { particulas(time.floatValue, burst(), particles, dotColors, dark) }
      }
    }
    // AMOLED: un velo negro deja las decoraciones como un brillo tenue sobre negro (casi todos los píxeles apagados).
    if (LocalFin.current.amoled) Box(Modifier.fillMaxSize().background(Color.Black.copy(0.72f)))
  }
}

private fun wrap(v: Float): Float = v - kotlin.math.floor(v)

/** Onda triangular entre 0 y 1: va y vuelve cada 2 × [period] segundos, a velocidad constante. */
private fun tri(t: Float, period: Float): Float = 1f - abs((t / period) % 2f - 1f)

/** Manchas de color grandes que se desplazan lentamente. */
private fun DrawScope.blobs(t: Float, colors: List<Color>, a: Float) {
  val w = size.width
  val h = size.height
  val centers = listOf(
    Offset(w * (0.18f + 0.14f * sin(t * 0.21f)), h * (0.12f + 0.07f * cos(t * 0.17f))),
    Offset(w * (0.88f + 0.12f * cos(t * 0.16f)), h * (0.40f + 0.10f * sin(t * 0.13f))),
    Offset(w * (0.30f + 0.20f * sin(t * 0.11f + 2f)), h * (0.86f + 0.08f * cos(t * 0.19f))),
  )
  val radii = listOf(w * 0.85f, w * 0.75f, w * 0.9f)
  val alphas = listOf(a, a * 0.8f, a * 0.7f)
  for (i in 0..2) {
    drawCircle(Brush.radialGradient(listOf(colors[i].copy(alphas[i]), Color.Transparent), centers[i], radii[i]), radii[i], centers[i])
  }
}

/** Aura: partículas de varios tamaños que flotan hacia arriba, más densas en la parte alta. */
private fun DrawScope.particulas(t: Float, push: Float, dots: List<Dot>, colors: List<Color>, dark: Boolean) {
  val w = size.width
  val h = size.height
  for (d in dots) {
    // La posición vertical se comprime hacia arriba: allí hay más partículas y se mueven más despacio.
    val raw = wrap(d.y - d.vy * t)
    val y = raw.pow(1.55f)
    val x = wrap(d.x + d.vx * t + 0.018f * sin(t * 0.5f + d.phase) + push * (0.10f + d.r * 0.035f))
    val edge = (minOf(raw, 1f - raw) / 0.07f).coerceIn(0f, 1f)
    val pulse = 0.85f + 0.15f * sin(t * 0.9f + d.phase * 2f)
    drawCircle(colors[d.color].copy(alpha = (d.alpha * edge * pulse * (if (dark) 0.8f else 0.75f)).coerceIn(0f, 1f)), d.r.dp.toPx() / 2f * (1f + push * 0.3f), Offset(x * w, y * h))
  }
}

/** Bolitas: el fondo de "Mis Finanzas". Un degradado que se desliza y dos esferas grandes y tenues que van y vienen. */
private fun DrawScope.bolitas(t: Float, bg: Color, colors: List<Color>) {
  val w = size.width
  val o1 = w * (-0.28f + 1.11f * tri(t, 12f))
  val o2 = w * (1.11f * (1f - tri(t, 15f)))
  drawRect(Brush.linearGradient(listOf(bg, colors[0].copy(0.15f), colors[2].copy(0.12f), bg), Offset(o1, 0f), Offset(o2, size.height)))
  drawCircle(colors[0].copy(0.18f), w / 1.3f, Offset(o1 / 1.2f, size.height / 3.5f))
  drawCircle(colors[2].copy(0.12f), w / 1.5f, Offset(o2 * 0.9f, size.height / 1.1f))
}

/** Aurora: cintas de luz que ondulan de lado a lado, como una aurora boreal. */
private fun DrawScope.aurora(t: Float, colors: List<Color>, dark: Boolean) {
  val w = size.width
  val h = size.height
  val steps = 18
  for (k in 0..3) {
    val color = colors[k % 3]
    val base = 0.10f + 0.22f * k
    val thick = h * (0.30f - 0.03f * k)
    val amp = h * 0.07f
    val speed = 0.22f + 0.07f * k
    fun top(i: Int): Float {
      val f = i / steps.toFloat()
      return h * base + amp * sin(f * (3.2f + k) + t * speed + k * 1.7f) + amp * 0.6f * sin(f * 7.1f - t * speed * 0.6f + k)
    }
    val path = Path()
    var min = Float.MAX_VALUE
    var max = -Float.MAX_VALUE
    for (i in 0..steps) {
      val y = top(i)
      min = minOf(min, y); max = maxOf(max, y)
      if (i == 0) path.moveTo(0f, y) else path.lineTo(w * i / steps, y)
    }
    for (i in steps downTo 0) path.lineTo(w * i / steps, top(i) + thick)
    path.close()
    val a = (if (dark) 0.46f else 0.40f) * (0.75f + 0.25f * sin(t * 0.3f + k * 2f))
    drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, color.copy(a), Color.Transparent), min, max + thick))
  }
}

/** Ondas: capas de olas que avanzan a distinta velocidad, abajo y (más tenues) arriba. */
private fun DrawScope.ondas(t: Float, colors: List<Color>, dark: Boolean) {
  val w = size.width
  val h = size.height
  val steps = 28
  fun wave(level: Float, amp: Float, freq: Float, phase: Float, bottom: Boolean, color: Color) {
    val path = Path()
    path.moveTo(0f, if (bottom) h else 0f)
    for (i in 0..steps) {
      val f = i / steps.toFloat()
      val y = h * level + amp * sin(f * freq * 2f * PI.toFloat() + phase) + amp * 0.35f * sin(f * freq * 4.3f * PI.toFloat() - phase * 1.3f)
      path.lineTo(w * f, y)
    }
    path.lineTo(w, if (bottom) h else 0f)
    path.close()
    drawPath(path, color)
  }
  val a = if (dark) 0.20f else 0.17f
  for (k in 0..3) {
    wave(0.58f + 0.10f * k, h * (0.030f - 0.004f * k), 1.1f + 0.35f * k, t * (0.35f + 0.13f * k) + k * 1.9f, true, colors[k % 3].copy(a + 0.03f * k))
  }
  for (k in 0..1) {
    wave(0.10f + 0.07f * k, h * 0.022f, 1.3f + 0.4f * k, -t * (0.28f + 0.1f * k) + k * 2.4f, false, colors[(k + 1) % 3].copy(a * 0.7f))
  }
}

/** Constelación: puntos que derivan despacio y se unen con una línea cuando quedan cerca. */
private fun DrawScope.constelacion(t: Float, push: Float, dots: List<Dot>, colors: List<Color>, ink: Color) {
  val w = size.width
  val h = size.height
  val reach = w * 0.24f
  val xs = FloatArray(dots.size)
  val ys = FloatArray(dots.size)
  dots.forEachIndexed { i, d ->
    xs[i] = wrap(d.x + d.vx * 1.6f * t + push * 0.06f) * w
    ys[i] = wrap(d.y + (d.vy - 0.014f) * 1.4f * t + 0.012f * sin(t * 0.4f + d.phase)) * h
  }
  val line = 1.dp.toPx()
  for (i in dots.indices) {
    for (j in i + 1 until dots.size) {
      val dist = hypot(xs[i] - xs[j], ys[i] - ys[j])
      if (dist < reach) drawLine(ink.copy(alpha = 0.26f * (1f - dist / reach)), Offset(xs[i], ys[i]), Offset(xs[j], ys[j]), line, StrokeCap.Round)
    }
  }
  dots.forEachIndexed { i, d ->
    val twinkle = 0.7f + 0.3f * sin(t * 1.1f + d.phase * 3f)
    drawCircle(colors[d.color % 3].copy(alpha = 0.85f * twinkle), (1.6f + d.r * 0.22f).dp.toPx(), Offset(xs[i], ys[i]))
  }
}

/** Burbujas: círculos translúcidos con un borde de luz que suben despacio y se mecen. */
private fun DrawScope.burbujas(t: Float, push: Float, dots: List<Dot>, colors: List<Color>, dark: Boolean) {
  val w = size.width
  val h = size.height
  drawRect(Brush.verticalGradient(listOf(colors[0].copy(if (dark) 0.16f else 0.12f), Color.Transparent, colors[1].copy(if (dark) 0.20f else 0.16f))))
  val rim = 1.3.dp.toPx()
  for (d in dots) {
    val r = (14f + d.r * 5.4f).dp.toPx()
    val rise = wrap(d.y + d.vy * 1.3f * t)
    val y = h + r - rise * (h + 2 * r)
    val x = wrap(d.x + 0.03f * sin(t * 0.45f + d.phase) + push * 0.05f) * w
    val color = colors[d.color % 3]
    val a = d.alpha * (if (dark) 0.36f else 0.30f)
    val center = Offset(x, y)
    drawCircle(Brush.radialGradient(listOf(color.copy(a * 0.25f), color.copy(a)), center, r), r, center)
    drawCircle((if (dark) Color.White else color).copy(a * 0.9f), r, center, style = Stroke(rim))
    drawCircle(Color.White.copy(a * 1.1f), r * 0.13f, center + Offset(-r * 0.38f, -r * 0.42f))
  }
}

private const val BG_SCALE = 8
private const val FRAME_MS = 26L
