package com.aura.finanzas.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.theme.LocalFin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Iconos de cuentas, categorías y secciones: un emoji a color sobre un círculo de tono pastel.
 * Los nombres (WALLET, FOOD…) son los que se guardan en la base de datos.
 */
private val emojis: Map<String, String> = linkedMapOf(
  "WALLET" to "👛", "BANK" to "🏦", "CARD" to "💳", "CASH" to "💵", "COIN" to "💰", "HOME" to "🏠",
  "BUILDING" to "🏢", "CAR" to "🚗", "FOOD" to "🍔", "BAG" to "🛍️", "HEALTH" to "💊", "TV" to "📺",
  "BULB" to "💡", "CHART" to "📊", "SAFE" to "🔐", "GIFT" to "🎁", "SUITCASE" to "✈️", "BOOK" to "📚",
  "PHONE" to "📱", "GEAR" to "⚙️", "RECEIPT" to "📄", "COFFEE" to "☕", "DUMBBELL" to "💪", "FUEL" to "⛽",
  "TARGET" to "🎯", "MARKET" to "🛒", "BUS" to "🚌", "PET" to "🐶", "BABY" to "👶", "FAMILY" to "👪",
  "HEART" to "❤️", "MUSIC" to "🎵", "GAME" to "🎮", "SHIRT" to "👕", "PARTY" to "🎉", "TOOLS" to "🔧",
  "STOCK" to "📈", "WATER" to "💧", "FRUIT" to "🍎", "PIZZA" to "🍕", "BEER" to "🍻", "BEAUTY" to "💅", "STAR" to "⭐",
)

val isoIconNames: List<String> = emojis.keys.toList()

/** Emoji del icono [name], para dibujarlo dentro de un gráfico. */
fun iconEmoji(name: String): String = emojis[name] ?: "💰"

@Composable
fun IsoIcon(name: String, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, muted: Boolean = false) {
  val dark = LocalFin.current.isDark
  // Ficha cuadrada de esquinas suaves, casi blanca, con un toque del color y una sombra leve: el dibujo resalta
  // como un objeto sobre una baldosa.
  val fill = if (dark) Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.10f).copy(0.34f), tint.copy(0.16f)))
  else Brush.verticalGradient(listOf(Color.White, lerp(tint, Color.White, 0.80f)))
  val fontSize = with(LocalDensity.current) { (size * 0.56f).toSp() }
  val shape = RoundedCornerShape(size * 0.30f)
  Box(
    modifier.size(size).graphicsLayer { alpha = if (muted) 0.45f else 1f }
      .shadow(if (dark) 0.dp else size * 0.10f, shape, ambientColor = tint.copy(0.35f), spotColor = tint.copy(0.45f))
      .clip(shape).background(fill)
      .border(1.dp, if (dark) Color.White.copy(0.16f) else lerp(tint, Color.White, 0.62f), shape),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      emojis[name] ?: "💰", fontSize = fontSize, lineHeight = fontSize, maxLines = 1,
      style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
    )
  }
}

/** Glifos de trazo redondeado para acciones y pestañas, dibujados en tiempo real. */
enum class Glyph {
  PLUS, EDIT, CAMERA, MIC, SWAP, EYE, EYE_OFF, CHECK, BACKSPACE, CHEVRON, SHARE,
  GEAR, CLOSE, CALENDAR, BOLT, DASH, PIE, TREND, SPARK, DOWN, ARROW_UP, ARROW_DOWN, PEOPLE, LIST, BARS, TRASH,
}

@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
  Canvas(modifier.size(size)) {
    val s = this.size.width
    val st = Stroke(s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
      drawLine(tint, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), st.width, StrokeCap.Round)
    fun path(vararg pts: Float, close: Boolean = false, fill: Boolean = false) {
      val p = Path()
      p.moveTo(pts[0] * s, pts[1] * s)
      for (i in 2 until pts.size step 2) p.lineTo(pts[i] * s, pts[i + 1] * s)
      if (close) p.close()
      if (fill) drawPath(p, tint)
      drawPath(p, tint, style = st)
    }
    fun block(x: Float, y: Float, w: Float, h: Float) =
      drawRoundRect(tint, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(s * 0.07f))
    fun star(cx: Float, cy: Float, r: Float) {
      val p = Path()
      for (i in 0 until 8) {
        val rr = if (i % 2 == 0) r else r * 0.34f
        val a = -PI / 2 + i * PI / 4
        val x = (cx + rr * cos(a).toFloat()) * s
        val y = (cy + rr * sin(a).toFloat()) * s
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
      }
      p.close()
      drawPath(p, tint)
      drawPath(p, tint, style = Stroke(s * 0.04f, join = StrokeJoin.Round))
    }
    when (glyph) {
      Glyph.PLUS -> { line(0.5f, 0.2f, 0.5f, 0.8f); line(0.2f, 0.5f, 0.8f, 0.5f) }
      Glyph.EDIT -> { path(0.2f, 0.8f, 0.24f, 0.62f, 0.66f, 0.2f, 0.8f, 0.34f, 0.38f, 0.76f, close = true, fill = true) }
      Glyph.CAMERA -> {
        path(0.14f, 0.32f, 0.34f, 0.32f, 0.4f, 0.22f, 0.6f, 0.22f, 0.66f, 0.32f, 0.86f, 0.32f, 0.86f, 0.78f, 0.14f, 0.78f, close = true)
        drawCircle(tint, s * 0.13f, Offset(s * 0.5f, s * 0.54f), style = st)
      }
      Glyph.MIC -> {
        drawRoundRect(tint, Offset(s * 0.38f, s * 0.12f), Size(s * 0.24f, s * 0.46f), CornerRadius(s * 0.12f))
        drawArc(tint, 0f, 180f, false, Offset(s * 0.26f, s * 0.24f), Size(s * 0.48f, s * 0.48f), style = st)
        line(0.5f, 0.72f, 0.5f, 0.88f)
      }
      Glyph.SWAP -> { path(0.2f, 0.36f, 0.8f, 0.36f); path(0.64f, 0.2f, 0.8f, 0.36f, 0.64f, 0.52f); path(0.8f, 0.66f, 0.2f, 0.66f); path(0.36f, 0.5f, 0.2f, 0.66f, 0.36f, 0.82f) }
      Glyph.EYE, Glyph.EYE_OFF -> {
        drawOval(tint, Offset(s * 0.1f, s * 0.28f), Size(s * 0.8f, s * 0.44f), style = st)
        drawCircle(tint, s * 0.1f, Offset(s * 0.5f, s * 0.5f), style = st)
        if (glyph == Glyph.EYE_OFF) line(0.18f, 0.82f, 0.82f, 0.18f)
      }
      Glyph.CHECK -> path(0.2f, 0.52f, 0.42f, 0.74f, 0.82f, 0.28f)
      Glyph.BACKSPACE -> { path(0.36f, 0.24f, 0.88f, 0.24f, 0.88f, 0.76f, 0.36f, 0.76f, 0.1f, 0.5f, close = true); line(0.5f, 0.4f, 0.7f, 0.6f); line(0.7f, 0.4f, 0.5f, 0.6f) }
      Glyph.CHEVRON -> path(0.38f, 0.22f, 0.66f, 0.5f, 0.38f, 0.78f)
      Glyph.SHARE -> { path(0.3f, 0.46f, 0.2f, 0.46f, 0.2f, 0.84f, 0.8f, 0.84f, 0.8f, 0.46f, 0.7f, 0.46f); line(0.5f, 0.14f, 0.5f, 0.62f); path(0.34f, 0.3f, 0.5f, 0.14f, 0.66f, 0.3f) }
      Glyph.GEAR -> {
        for (i in 0 until 8) {
          val a = i * PI / 4
          line(0.5f + 0.27f * cos(a).toFloat(), 0.5f + 0.27f * sin(a).toFloat(), 0.5f + 0.38f * cos(a).toFloat(), 0.5f + 0.38f * sin(a).toFloat())
        }
        drawCircle(tint, s * 0.25f, Offset(s * 0.5f, s * 0.5f), style = Stroke(s * 0.13f))
      }
      Glyph.CLOSE -> { line(0.24f, 0.24f, 0.76f, 0.76f); line(0.76f, 0.24f, 0.24f, 0.76f) }
      Glyph.CALENDAR -> {
        drawRoundRect(tint, Offset(s * 0.16f, s * 0.22f), Size(s * 0.68f, s * 0.62f), CornerRadius(s * 0.1f), style = st)
        line(0.16f, 0.42f, 0.84f, 0.42f); line(0.34f, 0.12f, 0.34f, 0.28f); line(0.66f, 0.12f, 0.66f, 0.28f)
        block(0.52f, 0.54f, 0.18f, 0.18f)
      }
      Glyph.BOLT -> path(0.56f, 0.08f, 0.22f, 0.56f, 0.46f, 0.56f, 0.40f, 0.92f, 0.78f, 0.42f, 0.54f, 0.42f, close = true, fill = true)
      Glyph.DASH -> { block(0.14f, 0.14f, 0.30f, 0.40f); block(0.52f, 0.14f, 0.34f, 0.22f); block(0.14f, 0.62f, 0.30f, 0.24f); block(0.52f, 0.44f, 0.34f, 0.42f) }
      Glyph.PIE -> {
        drawArc(tint, 0f, 270f, true, Offset(s * 0.12f, s * 0.16f), Size(s * 0.72f, s * 0.72f))
        drawArc(tint, -90f, 90f, true, Offset(s * 0.18f, s * 0.10f), Size(s * 0.72f, s * 0.72f))
      }
      Glyph.TREND -> { path(0.12f, 0.72f, 0.38f, 0.46f, 0.54f, 0.60f, 0.86f, 0.28f); path(0.66f, 0.28f, 0.86f, 0.28f, 0.86f, 0.48f) }
      Glyph.SPARK -> { star(0.40f, 0.58f, 0.34f); star(0.78f, 0.24f, 0.17f); star(0.80f, 0.78f, 0.11f) }
      Glyph.DOWN -> path(0.26f, 0.40f, 0.5f, 0.64f, 0.74f, 0.40f)
      Glyph.ARROW_UP -> { line(0.5f, 0.82f, 0.5f, 0.2f); path(0.26f, 0.44f, 0.5f, 0.2f, 0.74f, 0.44f) }
      Glyph.ARROW_DOWN -> { line(0.5f, 0.18f, 0.5f, 0.8f); path(0.26f, 0.56f, 0.5f, 0.8f, 0.74f, 0.56f) }
      Glyph.PEOPLE -> {
        drawCircle(tint, s * 0.13f, Offset(s * 0.36f, s * 0.34f))
        drawCircle(tint, s * 0.10f, Offset(s * 0.70f, s * 0.38f))
        drawArc(tint, 180f, 180f, true, Offset(s * 0.10f, s * 0.54f), Size(s * 0.52f, s * 0.56f))
        drawArc(tint, 180f, 180f, true, Offset(s * 0.52f, s * 0.56f), Size(s * 0.38f, s * 0.48f))
      }
      Glyph.LIST -> { line(0.2f, 0.28f, 0.8f, 0.28f); line(0.2f, 0.5f, 0.8f, 0.5f); line(0.2f, 0.72f, 0.6f, 0.72f) }
      Glyph.BARS -> { block(0.16f, 0.50f, 0.16f, 0.36f); block(0.42f, 0.30f, 0.16f, 0.56f); block(0.68f, 0.14f, 0.16f, 0.72f) }
      Glyph.TRASH -> {
        line(0.16f, 0.28f, 0.84f, 0.28f); path(0.38f, 0.28f, 0.40f, 0.16f, 0.60f, 0.16f, 0.62f, 0.28f)
        path(0.24f, 0.28f, 0.30f, 0.86f, 0.70f, 0.86f, 0.76f, 0.28f); line(0.42f, 0.42f, 0.43f, 0.72f); line(0.58f, 0.42f, 0.57f, 0.72f)
      }
    }
  }
}
