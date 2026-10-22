package com.aura.finanzas.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.theme.Motion
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private class Page(val icon: String, val title: String, val body: String)

private val pages = listOf(
  Page("WALLET", "Todo tu dinero en un panel", "Cuentas, billeteras, ingresos y gastos del mes, con tu saldo consolidado siempre a la vista."),
  Page("CHART", "Tu patrimonio, claro", "Activos, CDTs y deudas; presupuestos con semáforo, gastos fijos y metas de ahorro."),
  Page("BULB", "Con inteligencia artificial", "Escanea recibos, dicta gastos y recibe diagnósticos con tu propia clave de Gemini o Claude."),
)

private class Particle(val angle: Float, val radius: Float, val speed: Float, val size: Float, val color: Int)

/**
 * Bienvenida de tres páginas. Las partículas giran alrededor del icono y se dispersan mientras se desliza
 * entre páginas, para volver a reunirse al asentarse.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val pager = rememberPagerState { pages.size }
  val scope = rememberCoroutineScope()
  val time = remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    val start = withFrameNanos { it }
    while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
  }
  val particles = remember {
    val rnd = Random(7)
    List(90) { Particle(rnd.nextFloat() * 6.283f, 0.55f + rnd.nextFloat() * 0.9f, 0.15f + rnd.nextFloat() * 0.5f, 2f + rnd.nextFloat() * 5f, rnd.nextInt(3)) }
  }
  val colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary)

  Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
      TextButton(onDone, Modifier.height(48.dp)) { Text("Saltar") }
    }
    HorizontalPager(pager, Modifier.weight(1f)) { index ->
      val page = pages[index]
      // 0 cuando la página está centrada, 1 cuando está a una página de distancia.
      val away = { ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f) }
      Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize()) {
            val t = time.floatValue
            val scatter = 1f + away() * 2.2f
            val center = Offset(size.width / 2, size.height / 2)
            val base = size.minDimension * 0.34f
            for (p in particles) {
              val a = p.angle + t * p.speed
              val r = base * p.radius * scatter * (1f + 0.06f * sin(t * 1.3f + p.angle * 3f))
              drawCircle(
                colors[p.color].copy(alpha = (0.75f - away() * 0.6f) * (0.4f + 0.6f * (1f - (p.radius - 0.55f) / 0.9f))),
                p.size.dp.toPx() / 2,
                center + Offset(cos(a) * r, sin(a) * r * 0.82f),
              )
            }
          }
          IsoIcon(page.icon, scheme.primary, Modifier.graphicsLayer {
            val s = 1f - away() * 0.4f
            scaleX = s; scaleY = s
            alpha = 1f - away()
            translationY = sin(time.floatValue * 1.4f) * 6.dp.toPx()
          }, size = 130.dp)
        }
        Text(page.title, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
      }
    }
    Row(Modifier.padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      repeat(pages.size) { i ->
        val w by animateDpAsState(if (pager.currentPage == i) 28.dp else 8.dp, Motion.spatialBouncy(), label = "dot")
        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(if (pager.currentPage == i) scheme.primary else scheme.onSurface.copy(0.2f)))
      }
    }
    val last = pager.currentPage == pages.lastIndex
    Box(
      Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
        .background(scheme.primary)
        .clickable(remember { MutableInteractionSource() }, ripple()) {
          haptic.performHapticFeedback(HapticFeedbackType.LongPress)
          if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = Motion.spatial()) }
        },
      contentAlignment = Alignment.Center,
    ) { Text(if (last) "Empezar" else "Siguiente", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary) }
  }
}
