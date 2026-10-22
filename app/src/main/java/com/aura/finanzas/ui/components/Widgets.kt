package com.aura.finanzas.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlinx.coroutines.delay

private val numberFormat = NumberFormat.getInstance(Locale.forLanguageTag("es-CO")).apply { maximumFractionDigits = 0 }

fun money(v: Double, sign: Boolean = false): String {
  val prefix = if (v < 0) "-" else if (sign && v > 0) "+" else ""
  return prefix + "$ " + numberFormat.format(abs(v).roundToLong())
}

/** Cifra corta para ejes y etiquetas: 1,2 M · 350 k. */
fun shortMoney(v: Double): String {
  val a = abs(v)
  return when {
    a >= 1_000_000 -> (if (a >= 10_000_000) "%.0f".format(a / 1_000_000) else "%.1f".format(Locale.US, a / 1_000_000).replace('.', ',').removeSuffix(",0")) + " M"
    a >= 1_000 -> "%.0f".format(a / 1_000) + " k"
    else -> "%.0f".format(a)
  }
}

/** Cifra que cuenta hasta su valor al cambiar; con [hidden] muestra puntos. */
@Composable
fun AnimatedAmount(value: Double, style: TextStyle, color: Color, modifier: Modifier = Modifier, hidden: Boolean = false, sign: Boolean = false) {
  val anim = remember { Animatable(0f) }
  LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(1300, easing = FastOutSlowInEasing)) }
  val shown = if (anim.isRunning) anim.value.toDouble() else value
  Text(if (hidden) "$ ••••••" else money(shown, sign), style = style, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Semáforo: verde hasta 70 %, amarillo hasta 100 %, rojo si se pasa. */
@Composable
fun trafficColor(fraction: Float): Color {
  val fin = LocalFin.current
  return when {
    fraction >= 1f -> fin.danger
    fraction >= 0.7f -> fin.warn
    else -> fin.ok
  }
}

@Composable
fun TrafficBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 10.dp) {
  val p by animateFloatAsState(fraction.coerceIn(0f, 1f), Motion.spatial(), label = "bar")
  val color by animateColorAsState(trafficColor(fraction), Motion.effects(), label = "barc")
  Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(0.10f))) {
    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
  }
}

class Slice(val value: Double, val color: Color, val label: String = "", val emoji: String = "")

/** Dona de distribución; los arcos crecen desde cero al aparecer. */
@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, stroke: Dp = 18.dp) {
  val grow = remember { Animatable(0f) }
  LaunchedEffect(slices.size) { grow.snapTo(0f); grow.animateTo(1f, Motion.spatial()) }
  val track = MaterialTheme.colorScheme.onSurface.copy(0.08f)
  Canvas(modifier) {
    val sw = stroke.toPx()
    val d = size.minDimension - sw
    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
    val total = slices.sumOf { it.value }
    if (total <= 0) return@Canvas
    val gap = if (slices.size > 1) 5f else 0f
    var start = -90f
    for (s in slices) {
      val sweep = (s.value / total * 360.0).toFloat()
      val shown = ((sweep - gap) * grow.value.coerceIn(0f, 1.05f)).coerceAtLeast(0.5f)
      drawArc(s.color, start + gap / 2, shown, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
      start += sweep
    }
  }
}

/**
 * Dona con el porcentaje y el nombre de cada parte alrededor, unidos por una línea guía, y un título con
 * su cifra en el centro.
 */
@Composable
fun LabeledDonut(
  slices: List<Slice>, centerTitle: String, centerValue: String, modifier: Modifier = Modifier, stroke: Dp = 15.dp,
  minHeight: Dp = 0.dp, ringRadius: Dp? = null,
) {
  val grow = remember { Animatable(0f) }
  LaunchedEffect(slices.size, slices.sumOf { it.value }) { grow.snapTo(0f); grow.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
  val scheme = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val pctStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
  val nameStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant, fontSize = 9.sp)
  val titleStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant, fontSize = 10.sp)
  val valueStyle = MaterialTheme.typography.labelLarge.copy(color = scheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 12.sp)
  val total = slices.sumOf { it.value }
  val texts = remember(slices, pctStyle, nameStyle) {
    slices.map { s ->
      measurer.measure("${if (total > 0) (s.value / total * 100).toInt() else 0}%", pctStyle) to
        measurer.measure(if (s.label.length > 11) s.label.take(10) + "…" else s.label, nameStyle)
    }
  }
  val center2 = remember(centerTitle, centerValue, titleStyle, valueStyle) {
    measurer.measure(centerTitle, titleStyle) to measurer.measure(centerValue, valueStyle, maxLines = 1)
  }
  // Alto que necesitan las etiquetas del lado con más partes para quedar una debajo de otra sin montarse: si no
  // caben en [minHeight], la tarjeta crece (la dona conserva su tamaño).
  val density = androidx.compose.ui.platform.LocalDensity.current
  val needed = remember(slices, texts) {
    var right = 0
    var left = 0
    var start = -90f
    slices.forEach { s ->
      val sweep = if (total > 0) (s.value / total * 360.0).toFloat() else 0f
      if (sweep >= 8f) { if (cos((start + sweep / 2) * PI.toFloat() / 180f) >= 0f) right++ else left++ }
      start += sweep
    }
    val h = texts.maxOfOrNull { it.first.size.height + it.second.size.height } ?: 0
    with(density) { ((h + 4.dp.toPx()) * maxOf(left, right)).toDp() + 8.dp }
  }
  Canvas(modifier.height(maxOf(minHeight, needed))) {
    val sw = stroke.toPx()
    val radius = ringRadius?.toPx() ?: minOf(size.height * 0.36f, size.width * 0.22f)
    val center = Offset(size.width / 2, size.height / 2)
    val tl = Offset(center.x - radius, center.y - radius)
    val box = Size(radius * 2, radius * 2)
    drawArc(scheme.onSurface.copy(0.07f), 0f, 360f, false, tl, box, style = Stroke(sw))
    if (total > 0) {
      val g = grow.value
      // Etiqueta con línea guía, que aparece cuando el arco ya casi termina de crecer.
      val alpha = ((g - 0.6f) / 0.4f).coerceIn(0f, 1f)
      class Label(val i: Int, val a: Offset, val bx: Float, val right: Boolean, var y: Float)
      val labels = ArrayList<Label>()
      var start = -90f
      slices.forEachIndexed { i, s ->
        val sweep = (s.value / total * 360.0).toFloat()
        val gap = if (slices.size > 1) 9f else 0f
        drawArc(s.color, start + gap / 2, ((sweep - gap) * g).coerceAtLeast(0.5f), false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
        if (alpha > 0f && sweep >= 8f) {
          val mid = (start + sweep / 2) * PI.toFloat() / 180f
          val dir = Offset(cos(mid), sin(mid))
          val b = center + dir * (radius + sw / 2 + 16.dp.toPx())
          // Arriba o abajo del anillo (p. ej. un solo tipo al 100 %) la etiqueta se corre hacia el lado: en una
          // tarjeta baja no cabe debajo y se montaba sobre el anillo.
          val minX = radius + sw / 2 + 10.dp.toPx()
          val bx = if (dir.x >= 0) maxOf(b.x, center.x + minX) else minOf(b.x, center.x - minX)
          labels += Label(i, center + dir * (radius + sw / 2 + 3.dp.toPx()), bx, dir.x >= 0, b.y)
        }
        start += sweep
      }
      // En cada lado, las etiquetas se ordenan de arriba abajo y se separan lo justo para no montarse.
      for (side in listOf(true, false)) {
        val list = labels.filter { it.right == side }.sortedBy { it.y }
        val h = list.maxOfOrNull { texts[it.i].first.size.height + texts[it.i].second.size.height }?.toFloat() ?: continue
        val step = h + 4.dp.toPx()
        for (k in 1 until list.size) list[k].y = maxOf(list[k].y, list[k - 1].y + step)
        list.lastOrNull()?.let { it.y = minOf(it.y, size.height - h / 2) }
        for (k in list.size - 2 downTo 0) list[k].y = minOf(list[k].y, list[k + 1].y - step)
        list.firstOrNull()?.let { it.y = maxOf(it.y, h / 2) }
        // Si la primera bajó para no salirse por arriba, las siguientes se vuelven a separar de ella.
        for (k in 1 until list.size) list[k].y = maxOf(list[k].y, list[k - 1].y + step)
      }
      for (l in labels) {
        val color = slices[l.i].color.copy(alpha)
        val c = Offset(l.bx + (if (l.right) 20.dp.toPx() else -20.dp.toPx()), l.y)
        val guide = Path().apply { moveTo(l.a.x, l.a.y); lineTo(l.bx, l.y); lineTo(c.x, c.y) }
        drawPath(guide, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        val (pct, name) = texts[l.i]
        val gapX = 5.dp.toPx()
        val px = if (l.right) c.x + gapX else c.x - gapX - pct.size.width
        val nx = if (l.right) c.x + gapX else c.x - gapX - name.size.width
        val top = c.y - (pct.size.height + name.size.height) / 2f
        drawText(pct, topLeft = Offset(px.coerceIn(0f, size.width - pct.size.width), top), alpha = alpha)
        drawText(name, topLeft = Offset(nx.coerceIn(0f, size.width - name.size.width), top + pct.size.height), alpha = alpha)
      }
    }
    val (title, value) = center2
    drawText(title, topLeft = Offset(center.x - title.size.width / 2, center.y - title.size.height))
    drawText(value, topLeft = Offset(center.x - value.size.width / 2, center.y + 2.dp.toPx()))
  }
}

/** Anillo de avance con contenido en el centro; el arco crece con calma al aparecer o cambiar. */
@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, stroke: Dp = 13.dp, content: @Composable () -> Unit) {
  val p by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1400, easing = FastOutSlowInEasing), label = "ring")
  val track = MaterialTheme.colorScheme.onSurface.copy(0.08f)
  Box(modifier, contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val sw = stroke.toPx()
      val d = size.minDimension - sw
      val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
      drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
      if (p > 0.004f) drawArc(color, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
    }
    content()
  }
}

/**
 * Gráfico por periodos que se transforma entre línea y barras ([bars]): las barras nacen de los puntos de la
 * línea mientras esta se desvanece. Cada periodo tiene una columna translúcida de fondo; las barras llevan un
 * degradado (el periodo actual, más intenso, con brillo y su valor encima) y la línea, un área degradada debajo.
 * [average] dibuja una línea punteada suave con su etiqueta.
 */
@Composable
fun MorphChart(
  values: List<Float>, labels: List<String>, color: Color, bars: Boolean, modifier: Modifier = Modifier,
  average: Float? = null, averageLabel: String = "",
) {
  val morph by animateFloatAsState(if (bars) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "morph")
  val reveal = remember { Animatable(0f) }
  LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
  val scheme = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant)
  val currentStyle = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold)
  val avgStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurface, fontWeight = FontWeight.SemiBold)
  val valueStyle = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
  val lastValue = values.lastOrNull() ?: 0f
  val valueText = remember(lastValue, valueStyle) { measurer.measure(shortMoney(lastValue.toDouble()), valueStyle) }
  Canvas(modifier) {
    if (values.isEmpty()) return@Canvas
    val labelH = 22.dp.toPx()
    val top = 30.dp.toPx()
    val base = size.height - labelH
    val h = base - top
    val max = maxOf(values.max(), average ?: 0f).takeIf { it > 0f } ?: 1f
    val slot = size.width / values.size
    val grow = reveal.value
    val last = values.lastIndex
    fun x(i: Int) = slot * (i + 0.5f)
    fun y(i: Int) = base - h * (values[i] / max) * grow
    val bw = slot * 0.5f
    val radius = CornerRadius(bw / 2)

    // Columnas de fondo, como cristal: marcan cada mes aunque no tenga movimientos.
    for (i in values.indices) {
      drawRoundRect(
        Brush.verticalGradient(listOf(color.copy(0.02f), color.copy(if (i == last) 0.12f else 0.07f)), startY = 4.dp.toPx(), endY = base),
        Offset(x(i) - bw / 2, 4.dp.toPx()), Size(bw, base - 4.dp.toPx()), radius,
      )
    }

    // Barras con degradado; crecen desde el punto de la línea hasta la base.
    for (i in values.indices) {
      val yTop = y(i)
      val yBottom = yTop + (base - yTop) * morph
      if (morph > 0.01f && yBottom - yTop > 0.5f) {
        val current = i == last
        val (a1, a2) = if (current) 1f to 0.55f else 0.5f to 0.14f
        if (current) {
          // Brillo suave alrededor del mes actual.
          drawRoundRect(color.copy(0.10f * morph), Offset(x(i) - bw / 2 - 5.dp.toPx(), yTop - 5.dp.toPx()), Size(bw + 10.dp.toPx(), yBottom - yTop + 5.dp.toPx()), CornerRadius(bw / 2 + 5.dp.toPx()))
        }
        drawRoundRect(
          Brush.verticalGradient(listOf(color.copy(a1 * morph), color.copy(a2 * morph)), startY = yTop, endY = base),
          Offset(x(i) - bw / 2, yTop), Size(bw, yBottom - yTop), radius,
        )
        // Reflejo claro en el borde izquierdo, para dar volumen.
        drawRoundRect(
          Color.White.copy((if (current) 0.22f else 0.12f) * morph),
          Offset(x(i) - bw / 2 + bw * 0.16f, yTop + bw * 0.22f), Size(bw * 0.14f, ((yBottom - yTop) - bw * 0.44f).coerceAtLeast(0f)), CornerRadius(bw * 0.07f),
        )
      }
      val current = i == last
      val label = measurer.measure(labels.getOrElse(i) { "" }, if (current) currentStyle else labelStyle)
      val lx = x(i) - label.size.width / 2
      val ly = base + 5.dp.toPx()
      if (current) {
        val pad = 6.dp.toPx()
        drawRoundRect(color.copy(0.14f), Offset(lx - pad, ly - 1.dp.toPx()), Size(label.size.width + pad * 2, label.size.height + 2.dp.toPx()), CornerRadius(label.size.height.toFloat()))
      }
      drawText(label, topLeft = Offset(lx, ly))
    }

    // Línea suavizada con área degradada debajo.
    val lineAlpha = 1f - morph
    if (lineAlpha > 0.01f) {
      val line = Path().apply {
        moveTo(x(0), y(0))
        for (i in 1 until values.size) {
          val mid = (x(i - 1) + x(i)) / 2
          cubicTo(mid, y(i - 1), mid, y(i), x(i), y(i))
        }
      }
      if (values.size > 1) {
        val area = Path().apply { addPath(line); lineTo(x(last), base); lineTo(x(0), base); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(0.32f * lineAlpha), color.copy(0f)), startY = top, endY = base))
        drawPath(line, color.copy(alpha = lineAlpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
      }
      for (i in values.indices) {
        val p = Offset(x(i), y(i))
        if (i == last) drawCircle(color.copy(alpha = 0.20f * lineAlpha), 11.dp.toPx(), p)
        drawCircle(color.copy(alpha = lineAlpha), (if (i == last) 6 else 4).dp.toPx(), p)
        drawCircle(Color.White.copy(alpha = lineAlpha), (if (i == last) 2.5f else 1.6f).dp.toPx(), p)
      }
    }

    // Valor del mes actual en una burbuja encima de la barra (o del punto).
    if (grow > 0.9f && lastValue > 0f) {
      val pad = 7.dp.toPx()
      val w = valueText.size.width + pad * 2
      val hh = valueText.size.height + 4.dp.toPx()
      val bx = (x(last) - w / 2).coerceIn(0f, size.width - w)
      val by = (y(last) - hh - 8.dp.toPx()).coerceAtLeast(0f)
      val alpha = ((grow - 0.9f) / 0.1f).coerceIn(0f, 1f)
      drawRoundRect(color.copy(0.95f * alpha), Offset(bx, by), Size(w, hh), CornerRadius(hh / 2))
      drawText(valueText, topLeft = Offset(bx + pad, by + 2.dp.toPx()), alpha = alpha)
    }

    if (average != null && average > 0f) {
      val ay = base - h * (average / max) * grow
      val alpha = grow.coerceIn(0f, 1f)
      drawLine(
        scheme.onSurface.copy(0.35f * alpha), Offset(0f, ay), Offset(size.width, ay), 1.5.dp.toPx(),
        cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx())),
      )
      val text = measurer.measure(averageLabel, avgStyle)
      val pad = 8.dp.toPx()
      val at = Offset(pad, (ay - text.size.height - 6.dp.toPx()).coerceAtLeast(0f))
      drawRoundRect(
        scheme.surfaceContainerLowest.copy(0.85f * alpha), Offset(at.x - pad / 2, at.y - 2.dp.toPx()),
        Size(text.size.width + pad, text.size.height + 4.dp.toPx()), CornerRadius(text.size.height.toFloat()),
      )
      drawText(text, topLeft = at.copy(x = at.x), alpha = alpha)
    }
  }
}

/** Píldora seleccionable de al menos 40 dp de alto. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val bg by animateColorAsState(if (selected) scheme.primary else scheme.onSurface.copy(0.08f), Motion.effects(), label = "pill")
  val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurface, Motion.effects(), label = "pillt")
  val interaction = remember { MutableInteractionSource() }
  Row(
    modifier.pressScale(interaction, to = 0.94f).defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(20.dp)).background(bg)
      .clickable(interaction, ripple(), role = Role.Tab) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
      }.padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    if (leading != null) {
      leading()
      Box(Modifier.size(8.dp))
    }
    Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** Control segmentado: la pastilla de color se desliza con rebote hasta la opción elegida. */
@Composable
fun Segmented(
  options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
  height: Dp = 46.dp, thumb: Color = MaterialTheme.colorScheme.primary, onThumb: Color = MaterialTheme.colorScheme.onPrimary,
  container: Color = MaterialTheme.colorScheme.onSurface.copy(0.06f), textStyle: TextStyle? = null,
) {
  // Con cuatro o más pestañas el texto baja un punto para que "Distribución" quepa sin rozar los bordes.
  val labelStyle = textStyle ?: if (options.size >= 4) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val shape = RoundedCornerShape(height / 2.6f)
  val thumbColor by animateColorAsState(thumb, Motion.effects(), label = "segc")
  BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(shape).background(container).padding(4.dp)) {
    val w = maxWidth / options.size
    val x by animateDpAsState(w * selected, Motion.spatialBouncy(), label = "seg")
    Box(Modifier.offset(x = x).width(w).fillMaxHeight().shadow(4.dp, shape).clip(shape).background(thumbColor))
    Row(Modifier.fillMaxSize()) {
      options.forEachIndexed { i, label ->
        val fg by animateColorAsState(if (i == selected) onThumb else scheme.onSurfaceVariant, Motion.effects(), label = "segt")
        Box(
          Modifier.weight(1f).fillMaxHeight().clip(shape).clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
            if (i != selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(i)
          },
          contentAlignment = Alignment.Center,
        ) { Text(label, color = fg, style = labelStyle, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 4.dp)) }
      }
    }
  }
}

/** Separa en miles los dígitos de un valor: 1250000 → 1.250.000. */
fun groupDigits(digits: String): String = digits.reversed().chunked(3).joinToString(".").reversed()

/**
 * Flor de distribución: un pétalo por parte, cada uno de su color. El contorno marca el tamaño máximo y el
 * relleno crece desde el centro según lo que pesa esa parte frente a la mayor; su icono va dentro, junto al
 * centro, y el nombre por fuera. Con menos de [minPetals] partes, los pétalos que sobran quedan en gris.
 */
@Composable
fun PetalChart(slices: List<Slice>, modifier: Modifier = Modifier, minPetals: Int = 6) {
  val grow = remember { Animatable(0f) }
  LaunchedEffect(slices.map { it.value }) { grow.snapTo(0f); grow.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
  val scheme = MaterialTheme.colorScheme
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelMedium.copy(color = scheme.onSurface)
  val surface = scheme.surfaceContainerLowest
  val idle = scheme.onSurface.copy(0.16f).compositeOverSurface(surface, 0.16f)
  val density = LocalDensity.current
  val emojiStyle = TextStyle(fontSize = with(density) { 15.dp.toSp() })
  Canvas(modifier) {
    val n = maxOf(slices.size, minPetals)
    val max = slices.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val center = Offset(size.width / 2, size.height / 2)
    val outer = minOf(size.width / 2 - 64.dp.toPx(), size.height / 2 - 22.dp.toPx()).coerceAtLeast(40.dp.toPx())
    val inner = outer * 0.20f
    val sweep = 2f * PI.toFloat() / n
    val corner = 9.dp.toPx()
    val gap = 3.dp.toPx()
    val edge = 1.6.dp.toPx()

    // Pétalo entre dos radios: los lados dejan una separación constante con los vecinos y las esquinas van redondeadas.
    fun petal(angle: Float, r1: Float): Path {
      val a = inner + corner
      val b = maxOf(r1 - corner, a + 1f)
      fun half(r: Float) = (sweep / 2 - kotlin.math.asin(((gap / 2 + corner) / r).coerceIn(0f, 1f))).coerceAtLeast(0f)
      val path = Path()
      val steps = 10
      for (i in 0..steps) {
        val t = angle - half(b) + 2 * half(b) * i / steps
        val p = center + Offset(cos(t), sin(t)) * b
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
      }
      val hi = half(a)
      path.lineTo(center.x + cos(angle + hi) * a, center.y + sin(angle + hi) * a)
      path.lineTo(center.x + cos(angle - hi) * a, center.y + sin(angle - hi) * a)
      path.close()
      return path
    }
    // [inset] encoge el pétalo de manera pareja por todo el borde.
    fun drawPetal(angle: Float, r1: Float, color: Color, inset: Float = 0f) {
      val p = petal(angle, r1)
      drawPath(p, color)
      drawPath(p, color, style = Stroke((corner - inset) * 2, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }

    for (i in 0 until n) {
      val angle = -PI.toFloat() / 2 + i * sweep
      val s = slices.getOrNull(i)
      // Contorno del tamaño máximo: el pétalo completo en su color y, encima, el mismo un poco más pequeño en el color del fondo.
      drawPetal(angle, outer, s?.color ?: idle)
      drawPetal(angle, outer, surface, edge)
      if (s == null) continue
      val share = kotlin.math.sqrt((s.value / max).toFloat()).coerceIn(0f, 1f)
      val r = inner + corner * 2 + (outer - inner - corner * 2) * (0.30f + 0.70f * share) * grow.value
      drawPetal(angle, r, s.color, edge * 2.2f)
      val dir = Offset(cos(angle), sin(angle))
      if (s.emoji.isNotEmpty() && n <= 10) {
        val icon = measurer.measure(s.emoji, emojiStyle)
        val at = center + dir * (inner + corner + 13.dp.toPx())
        drawText(icon, topLeft = Offset(at.x - icon.size.width / 2f, at.y - icon.size.height / 2f), alpha = grow.value.coerceIn(0f, 1f))
      }
      val text = measurer.measure(if (s.label.length > 12) s.label.take(11) + "…" else s.label, labelStyle)
      val anchor = center + dir * (outer + 8.dp.toPx())
      val x = when { dir.x > 0.25f -> anchor.x; dir.x < -0.25f -> anchor.x - text.size.width; else -> anchor.x - text.size.width / 2f }
      val y = when { dir.y > 0.25f -> anchor.y; dir.y < -0.25f -> anchor.y - text.size.height; else -> anchor.y - text.size.height / 2f }
      drawText(text, topLeft = Offset(x.coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f)), y.coerceIn(0f, (size.height - text.size.height).coerceAtLeast(0f))), alpha = grow.value.coerceIn(0f, 1f))
    }
  }
}

/** Color translúcido [this] ya mezclado sobre [surface], para pintarlo opaco. */
private fun Color.compositeOverSurface(surface: Color, alpha: Float): Color = androidx.compose.ui.graphics.lerp(surface, copy(alpha = 1f), alpha)
