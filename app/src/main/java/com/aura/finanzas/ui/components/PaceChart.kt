package com.aura.finanzas.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Gasto (o ingreso) acumulado día a día del mes en curso y la media de los meses anteriores en el mismo día. */
class Pace(val current: List<Double>, val average: List<Double>?, val months: Int, val month: YearMonth)

/**
 * Gráfico del ritmo del mes: la línea de color es lo acumulado este mes hasta hoy, con su
 * relleno; la gris, la media acumulada de los meses anteriores para todo el mes. Arriba, una etiqueta con el día
 * elegido (hoy al abrir; se puede tocar o arrastrar para ver otro día) y lo que llevaba la media a esa altura.
 */
@Composable
fun PaceChart(pace: Pace, color: Color, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  // Morado de la referencia para la media; el color de la cuenta (gasto o ingreso) para este mes.
  val avgColor = Color(0xFF8B7CF0)
  val measurer = rememberTextMeasurer()
  val days = pace.month.lengthOfMonth()
  val today = pace.current.size.coerceAtLeast(1)
  var selected by remember(pace) { mutableIntStateOf(today) }
  val reveal = remember { Animatable(0f) }
  LaunchedEffect(pace) { reveal.snapTo(0f); reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }

  val axisStyle = MaterialTheme.typography.labelSmall.copy(color = scheme.onSurfaceVariant, fontSize = 11.sp)
  // La etiqueta va sobre un fondo oscuro (o claro en modo oscuro): texto en el color opuesto.
  val tipStyle = MaterialTheme.typography.bodySmall.copy(color = scheme.inverseOnSurface.copy(0.8f), fontSize = 12.sp)
  val maxValue = maxOf(pace.current.maxOrNull() ?: 0.0, pace.average?.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
  // Paso "redondo" del eje (1, 2, 2,5 o 5 por potencia de 10) para no más de 4 rayas.
  val step = remember(maxValue) {
    val raw = maxValue / 3.0
    val p = 10.0.pow(floor(log10(raw)))
    listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * p }.first { it >= raw }
  }
  // Sin margen arriba: el punto más alto toca el borde redondeado, como en la referencia.
  val top = maxValue
  val label = { d: Int -> "%02d/%02d".format(d, pace.month.monthValue) }

  Column(modifier) {
    Canvas(
      Modifier.fillMaxWidth().height(230.dp)
        .pointerInput(pace) {
          val pick = { x: Float ->
            val right = 44.dp.toPx()
            val f = (x / (size.width - right)).coerceIn(0f, 1f)
            selected = (1 + (f * (days - 1)).roundToInt()).coerceIn(1, today)
          }
          detectTapGestures { pick(it.x) }
        }
        .pointerInput(pace) {
          val right = 44.dp.toPx()
          detectDragGestures { change, _ ->
            val f = (change.position.x / (size.width - right)).coerceIn(0f, 1f)
            selected = (1 + (f * (days - 1)).roundToInt()).coerceIn(1, today)
          }
        },
    ) {
      val right = 44.dp.toPx()
      val tipH = 54.dp.toPx()
      val bottomH = 22.dp.toPx()
      val chartTop = tipH + 10.dp.toPx()
      val chartBottom = size.height - bottomH
      val w = size.width - right
      fun x(d: Int) = if (days <= 1) 0f else (d - 1).toFloat() / (days - 1) * w
      fun y(v: Double) = (chartBottom - (v / top) * (chartBottom - chartTop)).toFloat()

      // Valor más alto del gráfico, pequeño a la derecha, y fechas de inicio y fin del mes.
      val t = measurer.measure(shortMoney(top), axisStyle)
      drawText(t, topLeft = Offset(w + 8.dp.toPx(), chartTop))
      val zero = measurer.measure("0", axisStyle)
      drawText(zero, topLeft = Offset(w + 8.dp.toPx(), chartBottom - zero.size.height))
      val first = measurer.measure(label(1), axisStyle)
      val last = measurer.measure(label(days), axisStyle)
      drawText(first, topLeft = Offset(0f, chartBottom + 4.dp.toPx()))
      drawText(last, topLeft = Offset(w - last.size.width, chartBottom + 4.dp.toPx()))

      // Dos áreas translúcidas con degradado y su línea de borde, una delante de otra, dentro de un marco de esquinas
      // redondeadas: atrás la media de los meses anteriores (todo el mes), delante lo que va de este mes. Crecen
      // desde abajo al aparecer.
      val grow = reveal.value
      fun yy(v: Double) = chartBottom - (chartBottom - y(v)) * grow
      // Curva suave: un punto cada pocos días (siempre el primero y el último), unidos con curvas de Bézier.
      fun nodes(values: List<Double>): Pair<List<Float>, List<Float>> {
        val every = (values.size / 8).coerceAtLeast(1)
        val idx = (values.indices step every).toMutableList().also { if (it.last() != values.lastIndex) it += values.lastIndex }
        return idx.map { x(it + 1) } to idx.map { yy(values[it]) }
      }
      fun curve(values: List<Double>): Path {
        val (px, py) = nodes(values)
        return Path().apply {
          moveTo(px[0], py[0])
          for (k in 1 until px.size) {
            val mid = (px[k - 1] + px[k]) / 2
            cubicTo(mid, py[k - 1], mid, py[k], px[k], py[k])
          }
        }
      }
      // Altura de esa misma curva en la posición [at], para que el punto del día elegido quede sobre la línea.
      fun curveY(values: List<Double>, at: Float): Float {
        val (px, py) = nodes(values)
        if (px.size == 1 || at <= px[0]) return py[0]
        val k = (1 until px.size).firstOrNull { at <= px[it] } ?: return py.last()
        val x0 = px[k - 1]; val x1 = px[k]; val mid = (x0 + x1) / 2
        var lo = 0f; var hi = 1f
        repeat(24) {
          val t = (lo + hi) / 2
          val u = 1 - t
          val bx = u * u * u * x0 + 3 * u * u * t * mid + 3 * u * t * t * mid + t * t * t * x1
          if (bx < at) lo = t else hi = t
        }
        val t = (lo + hi) / 2
        val u = 1 - t
        return u * u * u * py[k - 1] + 3 * u * u * t * py[k - 1] + 3 * u * t * t * py[k] + t * t * t * py[k]
      }
      fun area(values: List<Double>, line: Path): Path = Path().apply {
        addPath(line)
        lineTo(x(values.size), chartBottom)
        lineTo(x(1), chartBottom)
        close()
      }
      val corner = CornerRadius(22.dp.toPx())
      val frame = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, chartTop, w, chartBottom, corner))
      }
      clipPath(frame) {
        drawRect(
          Brush.verticalGradient(listOf(scheme.onSurface.copy(0.02f), scheme.onSurface.copy(0.06f)), startY = chartTop, endY = chartBottom),
          Offset(0f, chartTop), Size(w, chartBottom - chartTop),
        )
        // Guías punteadas muy suaves al 25, 50 y 75 %.
        val dots = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 6.dp.toPx()))
        for (f in listOf(0.25f, 0.5f, 0.75f)) {
          val gy = chartBottom - (chartBottom - chartTop) * f
          drawLine(scheme.onSurface.copy(0.10f), Offset(12.dp.toPx(), gy), Offset(w - 12.dp.toPx(), gy), 1.dp.toPx(), cap = StrokeCap.Round, pathEffect = dots)
        }
        pace.average?.takeIf { it.isNotEmpty() }?.let { avg ->
          val line = curve(avg)
          drawPath(area(avg, line), Brush.verticalGradient(listOf(avgColor.copy(0.50f), avgColor.copy(0.06f)), startY = chartTop, endY = chartBottom))
          drawPath(line, avgColor.copy(0.9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (pace.current.isNotEmpty()) {
          val line = curve(pace.current)
          drawPath(area(pace.current, line), Brush.verticalGradient(listOf(color.copy(0.62f), color.copy(0.10f)), startY = chartTop, endY = chartBottom))
          // Borde derecho (hoy) que se desvanece hacia abajo, en vez de un corte seco.
          val ex = x(pace.current.size)
          drawLine(
            Brush.verticalGradient(listOf(color.copy(0.7f), color.copy(0f)), startY = yy(pace.current.last()), endY = chartBottom),
            Offset(ex, yy(pace.current.last())), Offset(ex, chartBottom), 1.5.dp.toPx(),
          )
          drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
      }

      if (reveal.value > 0.95f && pace.current.isNotEmpty()) {
        val d = selected.coerceIn(1, pace.current.size)
        val sx = x(d)
        val cur = pace.current[d - 1]
        val sy = curveY(pace.current, sx)
        // Línea del día elegido, punteada y suave, y punto con halo.
        drawLine(
          scheme.onSurface.copy(0.30f), Offset(sx, chartTop), Offset(sx, chartBottom), 1.dp.toPx(),
          cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
        )
        pace.average?.getOrNull(d - 1)?.let { a ->
          val ay = curveY(pace.average!!, sx)
          drawCircle(avgColor.copy(0.25f), 8.dp.toPx(), Offset(sx, ay))
          drawCircle(avgColor, 4.dp.toPx(), Offset(sx, ay))
        }
        drawCircle(color.copy(0.18f), 13.dp.toPx(), Offset(sx, sy))
        drawCircle(color, 7.dp.toPx(), Offset(sx, sy))
        drawCircle(Color.White, 3.5.dp.toPx(), Offset(sx, sy))

        // Etiqueta: fecha y valor del día; debajo, lo que llevaba la media en el mismo día. Con un piquito hacia el día.
        val date = "%02d/%02d/%d".format(d, pace.month.monthValue, pace.month.year)
        val l1 = measurer.measure(
          buildAnnotatedString {
            append("$date  ")
            withStyle(SpanStyle(color = color.copy(alpha = 1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)) { append(money(cur).removePrefix("$ ")) }
          },
          tipStyle,
        )
        val l2 = pace.average?.getOrNull(d - 1)?.let { a ->
          measurer.measure(
            buildAnnotatedString {
              append("Media a esa fecha  ")
              withStyle(SpanStyle(color = avgColor, fontWeight = FontWeight.Bold)) { append(money(a).removePrefix("$ ")) }
            },
            tipStyle,
          )
        }
        val pad = 12.dp.toPx()
        val boxW = maxOf(l1.size.width, l2?.size?.width ?: 0) + pad * 2
        val boxH = l1.size.height + (l2?.size?.height ?: 0) + pad * 1.4f
        val bx = (sx - boxW / 2).coerceIn(0f, size.width - boxW)
        val tipColor = scheme.inverseSurface.copy(0.86f)
        drawRoundRect(tipColor, Offset(bx, 0f), Size(boxW, boxH), CornerRadius(16.dp.toPx()))
        val nx = sx.coerceIn(bx + 16.dp.toPx(), bx + boxW - 16.dp.toPx())
        drawPath(Path().apply { moveTo(nx - 7.dp.toPx(), boxH); lineTo(nx, boxH + 7.dp.toPx()); lineTo(nx + 7.dp.toPx(), boxH); close() }, tipColor)
        drawText(l1, topLeft = Offset(bx + pad, pad * 0.7f))
        l2?.let { drawText(it, topLeft = Offset(bx + pad, pad * 0.7f + l1.size.height)) }
      }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
      Legend(color, "Este mes")
      if (pace.average != null) Legend(avgColor, if (pace.months == 1) "Mes anterior" else "Media de ${pace.months} meses")
    }
  }
}

@Composable
private fun Legend(color: Color, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(18.dp).clip(CircleShape).background(color.copy(0.22f)), contentAlignment = Alignment.Center) {
      Box(Modifier.size(10.dp).clip(CircleShape).background(color))
    }
    Spacer(Modifier.width(7.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
  }
}
