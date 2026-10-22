package com.aura.finanzas.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import com.aura.finanzas.data.Tx
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import com.aura.finanzas.ui.components.PetalChart
import com.aura.finanzas.ui.components.Slice
import java.time.LocalDate
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.aura.finanzas.FinanceState
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.SharedPerson
import com.aura.finanzas.ui.components.iconEmoji
import com.aura.finanzas.ui.theme.petalColors
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.AnimatedAmount
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.MorphChart
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.Ring
import com.aura.finanzas.ui.components.Segmented
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.components.Pace
import com.aura.finanzas.ui.components.PaceChart
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.components.shortMoney
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import com.aura.finanzas.ui.components.GrowOverlay
import com.aura.finanzas.ui.theme.accountBrush
import com.aura.finanzas.ui.theme.accountColors
import com.aura.finanzas.ui.theme.paletteColor
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val es: Locale = Locale.forLanguageTag("es-CO")
private val chipMonth = DateTimeFormatter.ofPattern("MMM yyyy", es)

/**
 * Análisis de una cuenta. Se abre creciendo desde su tarjeta del inicio: la tarjeta se estira hasta volverse
 * una hoja que sube desde abajo y, cuando ya casi llegó, el contenido aparece. Al cerrar hace el camino inverso.
 */
@Composable
fun AccountAnalysis(
  state: FinanceState,
  accountId: Long,
  origin: Rect,
  hide: Boolean,
  onClosed: () -> Unit,
  onNewTx: (String) -> Unit,
  onMoves: () -> Unit,
  onShare: () -> Unit,
  refreshing: Boolean,
  onRefresh: () -> Unit,
  people: List<SharedPerson> = emptyList(),
  myEmail: String = "",
) {
  val account = state.accountById[accountId]
  LaunchedEffect(account == null) { if (account == null) onClosed() }
  if (account == null) return
  GrowOverlay(origin, onClosed, tint = accountBrush(account.colorIdx), card = true) { close ->
    AnalysisContent(state, account, hide, close, onNewTx, onMoves, onShare, refreshing, onRefresh, people, myEmail)
  }
}

@Composable
private fun AnalysisContent(
  state: FinanceState, account: Account, hide: Boolean,
  onClose: () -> Unit, onNewTx: (String) -> Unit, onMoves: () -> Unit, onShare: () -> Unit,
  refreshing: Boolean, onRefresh: () -> Unit, people: List<SharedPerson>, myEmail: String,
) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val accent = if (fin.isDark) paletteColor(account.colorIdx, true) else accountColors(account.colorIdx).let { it[it.size / 2] }
  val zone = remember { ZoneId.systemDefault() }
  val txs = remember(state.txs, account.id) { state.txs.filter { it.accountId == account.id && it.type != TxType.TRANSFER } }
  val byMonth = remember(txs) { txs.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) } }
  val months = remember(byMonth) { byMonth.keys.sortedDescending().take(12) }
  // Mes elegido como texto (aaaa-mm); vacío = todos los meses.
  var monthKey by rememberSaveable { mutableStateOf("") }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var bars by rememberSaveable { mutableStateOf(true) }
  val month = months.firstOrNull { it.toString() == monthKey }
  val scoped = if (month != null) byMonth[month] ?: emptyList() else txs
  val income = scoped.filter { it.type == TxType.INCOME }.sumOf { it.amount }
  val expense = scoped.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
  val net = if (month == null) state.balances[account.id] ?: 0.0 else income - expense
  val avg = state.avg3[account.id]
  val kind = if (tab == 2) TxType.INCOME else TxType.EXPENSE
  val kindColor = if (tab == 2) fin.income else fin.expense
  val last6 = remember { (5 downTo 0).map { YearMonth.now().minusMonths(it.toLong()) } }
  val series = remember(byMonth, kind) { last6.map { ym -> (byMonth[ym]?.filter { it.type == kind }?.sumOf { it.amount } ?: 0.0).toFloat() } }
  val labels = remember { last6.map { it.month.getDisplayName(TextStyle.SHORT, es).take(3).removeSuffix(".") } }
  val average = (if (tab == 2) avg?.income else avg?.expense) ?: 0.0

  val swipe = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  val tabCount = 4
  // Deslizar hacia abajo actualiza la cuenta: recalcula y trae lo que otra persona haya registrado en ella.
  PullToRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize()) {
  Column(
    Modifier.fillMaxSize()
      .pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
          onDragStart = { total = 0f },
          onDragEnd = {
            val next = if (total < 0) tab + 1 else tab - 1
            if (kotlin.math.abs(total) > 56.dp.toPx() && next in 0 until tabCount) tab = next
            scope.launch { swipe.animateTo(0f, Motion.spatialBouncy()) }
          },
          onDragCancel = { scope.launch { swipe.animateTo(0f, Motion.spatialBouncy()) } },
        ) { change, dx ->
          change.consume()
          total += dx
          scope.launch { swipe.snapTo((total * 0.35f).coerceIn(-90.dp.toPx(), 90.dp.toPx())) }
        }
      }
      .graphicsLayer { translationX = swipe.value }
      .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(top = 26.dp, bottom = 18.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(account.name, style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Análisis de Cuenta" + if (account.shareId.isNotEmpty()) " · compartida 👥" else "", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (account.shareId.isNotEmpty()) {
          sharedLines(people, myEmail).forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      // Cambia el gráfico entre barras y línea; una forma se transforma en la otra.
      val interaction = remember { MutableInteractionSource() }
      Box(
        Modifier.pressScale(interaction, to = 0.88f).size(44.dp).clip(CircleShape).background(accent.copy(0.14f)).clickable(interaction, ripple()) { bars = !bars },
        contentAlignment = Alignment.Center,
      ) {
        AnimatedContent(bars, transitionSpec = { (fadeIn(Motion.effects()) + slideInVertically(Motion.spatialBouncy()) { it / 2 }) togetherWith fadeOut(Motion.effects()) }, label = "tipo") { b ->
          GlyphIcon(if (b) Glyph.TREND else Glyph.BARS, accent, size = 22.dp)
        }
      }
    }

    Text("Filtrar por Mes:", style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      item { MonthChip("Todos los meses 📅", month == null, accent) { monthKey = "" } }
      items(months, key = { it.toString() }) { ym ->
        MonthChip(ym.format(chipMonth).replace(".", "").replaceFirstChar { it.uppercase() }, month == ym, accent) { monthKey = ym.toString() }
      }
    }

    Segmented(
      listOf("Flujo", "Gastos", "Ingresos", "Distribución"), tab, { tab = it }, thumb = accent, onThumb = Color.White, height = 44.dp,
      textStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    )

    if (tab == 0) {
      val total = income + expense
      val inShare = if (total > 0) (income / total).toFloat() else 0f
      val outShare = if (total > 0) (expense / total).toFloat() else 0f
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        FlowRing("Ingresos", income, inShare, accent, Glyph.ARROW_UP, fin.income, hide)
        Wave(accent)
        FlowRing("Gastos", expense, outShare, accent, Glyph.ARROW_DOWN, fin.expense, hide)
      }
      val netColor = if (net < 0) fin.expense else fin.income
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(netColor.copy(0.10f)).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(if (month == null) "BALANCE NETO DE CUENTA" else "BALANCE DEL MES", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        AnimatedAmount(net, MaterialTheme.typography.headlineMedium, netColor, hidden = hide)
      }
    }

    if (tab == 3) {
      // Distribución de los gastos: una flor con un pétalo por categoría
      val spread = remember(scoped) {
        val all = scoped.filter { it.type == TxType.EXPENSE }.groupBy { it.categoryId }.map { it.key to it.value.sumOf { t -> t.amount } }.sortedByDescending { it.second }
        fun slice(i: Int, id: Long?, v: Double) = Slice(v, petalColors[i % petalColors.size], state.categoryById[id]?.name ?: "Sin categoría", iconEmoji(state.categoryById[id]?.icon ?: "COIN"))
        if (all.size <= 8) all.mapIndexed { i, (id, v) -> slice(i, id, v) }
        else all.take(7).mapIndexed { i, (id, v) -> slice(i, id, v) } + Slice(all.drop(7).sumOf { it.second }, petalColors[7], "Otros", "➕")
      }
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          (month?.format(chipMonth)?.replace(".", "")?.replaceFirstChar { it.uppercase() } ?: "Todos los meses") + " · total gastado",
          style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        )
        AnimatedAmount(expense, MaterialTheme.typography.headlineLarge, scheme.onSurface, hidden = hide)
        if (spread.isEmpty()) {
          Text("Sin gastos en este periodo.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else {
          PetalChart(spread, Modifier.fillMaxWidth().height(310.dp).padding(top = 6.dp))
        }
      }
    }

    // Ritmo del mes: lo acumulado este mes frente a la media de los meses anteriores en el mismo día (como Money
    // Lover); debajo, el promedio mensual y su gráfico por mes.
    if (tab != 3) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(scheme.onSurface.copy(0.04f)).padding(16.dp)) {
      val pace = remember(byMonth, kind) { monthPace(byMonth, kind, zone) }
      val current = pace.current.lastOrNull() ?: 0.0
      Text(if (tab == 2) "Ingresos de este mes" else "Gastos de este mes", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
      AnimatedAmount(current, MaterialTheme.typography.headlineSmall, kindColor, hidden = hide)
      pace.average?.getOrNull(pace.current.size - 1)?.let { same ->
        if (!hide) {
          val diff = current - same
          Text(
            when {
              same <= 0 -> "Los meses anteriores no tenían movimientos a esta altura."
              kotlin.math.abs(diff) < same * 0.02 -> "Igual que la media a esta altura del mes."
              else -> "${if (diff > 0) "+" else "−"} ${money(kotlin.math.abs(diff))} (${(kotlin.math.abs(diff) / same * 100).roundToInt()} %) vs. la media a esta altura"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
              same <= 0 -> scheme.onSurfaceVariant
              (diff > 0) == (tab != 2) -> fin.warn
              else -> fin.ok
            },
          )
        }
      }
      if (!hide) PaceChart(pace, kindColor, Modifier.fillMaxWidth().padding(top = 10.dp))
      if (!hide && pace.average == null) {
        Text(
          "Aún no hay meses anteriores para comparar. Desde el próximo mes verás aquí la línea gris con tu media.",
          Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(16.dp))
      Text(
        (if (tab == 2) "Promedio de ingresos" else "Promedio de gastos") + when (avg?.months ?: 0) { 0 -> " · este mes"; 1 -> " · mes anterior"; else -> " · últimos ${avg?.months} meses" },
        style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant,
      )
      Row(verticalAlignment = Alignment.Bottom) {
        AnimatedAmount(average, MaterialTheme.typography.titleLarge, scheme.onSurface, hidden = hide)
        Text(" al mes", Modifier.padding(bottom = 2.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      }
      if (!hide) {
        MorphChart(
          series, labels, kindColor, bars, Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp),
          average = average.toFloat(), averageLabel = "Promedio ${shortMoney(average)}",
        )
      }
    }

    if (tab != 0) {
      val parts = remember(scoped, kind, tab) {
        scoped.filter { it.type == kind }.groupBy { it.categoryId }.map { it.key to it.value.sumOf { t -> t.amount } }.sortedByDescending { it.second }.take(if (tab == 3) 7 else 6)
      }
      val sum = parts.sumOf { it.second }
      if (parts.isEmpty()) {
        Text("Sin ${if (tab == 2) "ingresos" else "gastos"} en este periodo.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      }
      parts.forEachIndexed { index, (categoryId, value) ->
        val c = state.categoryById[categoryId]
        // En Distribución cada renglón lleva el color de su pétalo.
        val tint = if (tab == 3) petalColors[index % petalColors.size] else paletteColor(c?.colorIdx ?: 5, fin.isDark)
        val share by animateFloatAsState((value / sum).toFloat(), tween(1100, easing = FastOutSlowInEasing), label = "parte")
        Row(verticalAlignment = Alignment.CenterVertically) {
          IsoIcon(c?.icon ?: "COIN", tint, size = 40.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Row {
              Text(c?.name ?: "Sin categoría", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(if (hide) "${(value / sum * 100).roundToInt()} %" else "${money(value)} · ${(value / sum * 100).roundToInt()} %", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            }
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(scheme.onSurface.copy(0.08f))) {
              Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(tint))
            }
          }
        }
      }
    }

    Text("Desliza a los lados para cambiar entre Flujo, Gastos, Ingresos y Distribución", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Pill("Movimientos", false, Modifier.weight(1f), leading = { GlyphIcon(Glyph.LIST, scheme.onSurface, size = 20.dp) }, onClick = onMoves)
      Pill("Compartir", false, Modifier.weight(1f), leading = { GlyphIcon(Glyph.PEOPLE, scheme.onSurface, size = 20.dp) }, onClick = onShare)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      TextButton(onClose) { Text("Cerrar", fontWeight = FontWeight.Bold, color = accent) }
    }
  }
  }
}

/**
 * Con quién está vinculada una cuenta compartida, en dos renglones: quién la creó y quiénes colaboran en ella.
 * Se ve igual desde el teléfono de quien la creó y desde el de quien colabora.
 */
fun sharedLines(people: List<SharedPerson>, myEmail: String): List<String> {
  val me = myEmail.lowercase()
  fun who(email: String) = if (email == me) "$email (tú)" else email
  if (me.isEmpty()) return listOf("Inicia sesión para ver con quién está vinculada")
  if (people.isEmpty()) return listOf("Desliza hacia abajo para cargar con quién está vinculada")
  val owner = people.firstOrNull { it.owner }
  val partners = people.filter { !it.owner }
  return listOf(
    "👤 Creada por: " + (owner?.let { who(it.email) } ?: "—"),
    "🤝 " + (if (partners.size > 1) "Colaboradores: " else "Colaborador: ") + (partners.joinToString(", ") { who(it.email) }.ifEmpty { "aún nadie ha aceptado" }),
  )
}

/** Con quién está compartida la cuenta, en una frase. */
fun sharedSummary(people: List<SharedPerson>, myEmail: String): String {
  val me = myEmail.lowercase()
  val others = people.filter { it.email != me }
  val owner = people.firstOrNull { it.owner }
  return when {
    me.isEmpty() -> "Conecta tu cuenta de Google en Ajustes para ver con quién está compartida"
    people.isEmpty() -> "Compartida · desliza hacia abajo para ver con quién"
    others.isEmpty() -> "Aún no la ha abierto nadie más"
    owner != null && owner.email != me -> "Te la compartió ${owner.email}" + others.filter { !it.owner }.takeIf { it.isNotEmpty() }?.joinToString(", ", " · también: ") { it.email }.orEmpty()
    else -> "Compartida con " + others.joinToString(", ") { it.email }
  }
}

/**
 * Lo acumulado día a día en el mes en curso (hasta hoy) y la media acumulada, día por día, de hasta 3 meses
 * anteriores que tengan movimientos. Un mes más corto que el actual repite su total en los días que le faltan.
 */
private fun monthPace(byMonth: Map<YearMonth, List<Tx>>, kind: String, zone: ZoneId): Pace {
  val now = YearMonth.now()
  val length = now.lengthOfMonth()
  fun cumulative(ym: YearMonth): DoubleArray {
    val daily = DoubleArray(ym.lengthOfMonth())
    byMonth[ym]?.filter { it.type == kind }?.forEach { t ->
      daily[Instant.ofEpochMilli(t.date).atZone(zone).dayOfMonth - 1] += t.amount
    }
    for (i in 1 until daily.size) daily[i] += daily[i - 1]
    return daily
  }
  val current = cumulative(now).take(LocalDate.now().dayOfMonth)
  val previous = (1..3).map { now.minusMonths(it.toLong()) }.filter { byMonth.containsKey(it) }.map(::cumulative)
  val average = if (previous.isEmpty()) null else (1..length).map { d -> previous.sumOf { it[minOf(d, it.size) - 1] } / previous.size }
  return Pace(current, average, previous.size, now)
}

@Composable
private fun MonthChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val bg by animateColorAsState(if (selected) accent.copy(0.16f) else Color.Transparent, Motion.effects(), label = "mes")
  val edge by animateColorAsState(if (selected) Color.Transparent else scheme.outline.copy(0.7f), Motion.effects(), label = "mesb")
  val shape = RoundedCornerShape(16.dp)
  val interaction = remember { MutableInteractionSource() }
  Box(
    Modifier.pressScale(interaction, to = 0.94f).height(40.dp).clip(shape).background(bg).border(1.5.dp, edge, shape).clickable(interaction, ripple(), onClick = onClick).padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) { Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (selected) accent else scheme.onSurface, maxLines = 1) }
}

/** Anillo de ingresos o gastos: crece según su parte del flujo y es más grande cuanto más pesa. */
@Composable
private fun FlowRing(label: String, value: Double, share: Float, accent: Color, glyph: Glyph, glyphColor: Color, hide: Boolean) {
  val scheme = MaterialTheme.colorScheme
  val size by animateFloatAsState(104f + 34f * share, Motion.spatialBouncy(), label = "anillo")
  Ring(share, accent, Modifier.size(size.dp)) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      GlyphIcon(glyph, glyphColor, size = 16.dp)
      Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
      Text(if (hide) "••••" else if (value >= 10_000_000) "$ " + shortMoney(value) else money(value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
    }
  }
}

/** Ondita punteada entre los dos anillos. */
@Composable
private fun Wave(color: Color) {
  Canvas(Modifier.width(44.dp).height(20.dp)) {
    val path = Path()
    for (i in 0..20) {
      val f = i / 20f
      val x = size.width * f
      val y = size.height / 2 + size.height * 0.32f * sin(f * 2f * PI.toFloat())
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 9f))))
  }
}
