package com.aura.finanzas.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.AccountType
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.LabeledDonut
import com.aura.finanzas.ui.components.Segmented
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.theme.Motion
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.finanzas.FinanceState
import com.aura.finanzas.currentValue
import com.aura.finanzas.data.Asset
import com.aura.finanzas.data.Budget
import com.aura.finanzas.data.Debt
import com.aura.finanzas.data.FixedExpense
import com.aura.finanzas.data.Goal
import com.aura.finanzas.gain
import com.aura.finanzas.returnPct
import com.aura.finanzas.ui.theme.accountBrush
import com.aura.finanzas.ui.components.AnimatedAmount
import com.aura.finanzas.ui.components.DonutChart
import com.aura.finanzas.ui.components.GlassCard
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.Slice
import com.aura.finanzas.ui.components.TrafficBar
import com.aura.finanzas.ui.components.enterStagger
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.components.trafficColor
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.paletteColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object AssetKind {
  val all = listOf("INMUEBLE", "ACCIONES", "INVERSION", "VEHICULO", "AHORRO", "CDT", "OTRO")
  fun label(k: String) = when (k) {
    "INMUEBLE" -> "Inmueble"; "ACCIONES" -> "Acciones"; "INVERSION" -> "Inversión"; "VEHICULO" -> "Vehículo"; "AHORRO" -> "Ahorro"; "CDT" -> "CDT"; else -> "Otro"
  }
  fun icon(k: String) = when (k) {
    "INMUEBLE" -> "HOME"; "ACCIONES" -> "STOCK"; "INVERSION" -> "CHART"; "VEHICULO" -> "CAR"; "AHORRO" -> "SAFE"; "CDT" -> "BANK"; else -> "COIN"
  }
  // Las acciones usan el púrpura (0); los demás conservan el color que ya tenían.
  fun colorIdx(k: String) = if (k == "ACCIONES") 0 else listOf("INMUEBLE", "INVERSION", "VEHICULO", "AHORRO", "CDT", "OTRO").indexOf(k).coerceAtLeast(0) + 1
}

object DebtKind {
  val all = listOf("PRESTAMO", "TARJETA", "HIPOTECA", "OTRO")
  fun label(k: String) = when (k) { "PRESTAMO" -> "Préstamo"; "TARJETA" -> "Tarjeta de crédito"; "HIPOTECA" -> "Hipoteca"; else -> "Otra deuda" }
  fun icon(k: String) = when (k) { "TARJETA" -> "CARD"; "HIPOTECA" -> "BUILDING"; "PRESTAMO" -> "CASH"; else -> "COIN" }
}

class WorthActions(
  val editAsset: (Asset) -> Unit,
  val deleteAsset: (Asset) -> Unit,
  /** Abre el detalle de un tipo de activo, creciendo desde su casilla. */
  val openKind: (String, androidx.compose.ui.geometry.Rect) -> Unit,
  val unpayFixed: (FixedExpense) -> Unit,
  val editDebt: (Debt) -> Unit,
  val editBudget: (Budget) -> Unit,
  val editFixed: (FixedExpense) -> Unit,
  val editGoal: (Goal) -> Unit,
  val payFixed: (FixedExpense) -> Unit,
  val contribute: (Goal) -> Unit,
)

/** Tasa sin decimales sobrantes: 11.0 → 11, 11.5 → 11,5. */
private fun pct(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')

/** Patrimonio: lo que se tiene (activos) menos lo que se debe. Las cuentas del inicio van aparte. */
@Composable
fun WorthScreen(state: FinanceState, hide: Boolean, actions: WorthActions) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val byKind = remember(state.assets) {
    state.assets.groupBy { it.kind }.map { (k, list) -> k to list.sumOf { it.currentValue() } }.sortedByDescending { it.second }
  }
  LazyColumn(
    Modifier.fillMaxSize().statusBarsPadding(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = BottomBarSpace),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item(key = "title") { ScreenHeader("MI PATRIMONIO", Modifier.enterStagger(0)) }
    item(key = "hero") {
      val shape = RoundedCornerShape(26.dp)
      val warm = lerp(scheme.primary, Color(0xFFFF5A3C), 0.45f)
      Column(
        Modifier.fillMaxWidth().enterStagger(1).shadow(12.dp, shape, spotColor = scheme.primary).clip(shape)
          .background(Brush.linearGradient(listOf(scheme.primary, warm, lerp(scheme.primary, Color(0xFFFF7A70), 0.5f))))
          .drawBehind {
            // Círculos tenues de adorno, como ondas dentro de la tarjeta.
            drawCircle(Color.White.copy(0.07f), size.width * 0.42f, Offset(size.width * 0.82f, size.height * 0.15f))
            drawCircle(Color.White.copy(0.06f), size.width * 0.30f, Offset(size.width * 0.05f, size.height * 1.05f))
          }
          .padding(horizontal = 18.dp, vertical = 16.dp),
      ) {
        Text("PATRIMONIO NETO ESTIMADO", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.9f), letterSpacing = 1.2.sp)
        val long = money(state.netWorth).length > 14
        AnimatedAmount(
          state.netWorth, if (long) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
          Color.White, Modifier.padding(top = 6.dp), hidden = hide,
        )
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF4ADE80).copy(0.35f)), contentAlignment = Alignment.Center) {
            GlyphIcon(Glyph.ARROW_UP, Color(0xFF4ADE80), size = 14.dp)
          }
          Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text("Activos", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.85f))
            AnimatedAmount(state.assetsTotal, MaterialTheme.typography.titleMedium, Color.White, hidden = hide)
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("Pasivos", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.85f))
            AnimatedAmount(state.debtsTotal, MaterialTheme.typography.titleMedium, Color.White, hidden = hide)
          }
        }
      }
    }
    item(key = "donut") {
      GlassCard(Modifier.fillMaxWidth().enterStagger(2), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
          SectionTitle("Distribución de activos 📊", null, {})
          if (byKind.isEmpty()) {
            Text("Agrega tu primer activo con el botón + y aquí verás cómo se reparte.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
          } else {
            // Versión compacta: dona más pequeña y cifra corta en el centro (el detalle está en los tipos de activo de abajo).
            LabeledDonut(
              byKind.map { (k, v) -> Slice(v, paletteColor(AssetKind.colorIdx(k), fin.isDark), AssetKind.label(k)) },
              "Activos", if (hide) "••••" else "$ " + com.aura.finanzas.ui.components.shortMoney(state.assetsTotal),
              Modifier.fillMaxWidth().padding(top = 10.dp), stroke = 11.dp, minHeight = 132.dp, ringRadius = 47.dp,
            )
          }
        }
      }
    }
    if (byKind.isNotEmpty()) {
      item(key = "filter-title") {
        Column(Modifier.enterStagger(3)) {
          SectionTitle("Tipos de activo 🏦", null, {})
          SectionHint("Toca un tipo para ver sus registros")
        }
      }
      item(key = "tiles") {
        Column(Modifier.enterStagger(4), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          byKind.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              row.forEach { (kind, value) ->
                KindTile(kind, value, if (state.assetsTotal > 0) value / state.assetsTotal else 0.0, hide, Modifier.weight(1f)) { bounds ->
                  actions.openKind(kind, bounds)
                }
              }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
      }
    }
    item(key = "debts-title") { SectionTitle("Pasivos (deudas)", "Nueva", { actions.editDebt(Debt(name = "", balance = 0.0, icon = "CASH")) }) }
    if (state.debts.isEmpty()) {
      item(key = "debts-empty") { EmptyCard("CARD", "Sin deudas", "Registra créditos, tarjetas o hipotecas.", onClick = null) }
    }
    items(state.debts, key = { "debt-${it.id}" }) { d ->
      val subtitle = buildString {
        append(DebtKind.label(d.kind))
        d.ratePct?.let { append(" · ${pct(it)} % E.A.") }
        d.monthlyPayment?.let { if (!hide) append(" · cuota ${money(it)}") }
        d.dueDay?.let { append(" · día $it") }
      }
      ItemRow(d.icon, fin.expense, d.name, subtitle, { Amount(d.balance, hide, fin.expense) }) { actions.editDebt(d) }
    }
  }
}

/** Línea de detalle de un activo: tipo, tasa y vencimiento de un CDT, o unidades y rentabilidad de unas acciones. */
internal fun assetSubtitle(a: Asset, hide: Boolean): String = buildString {
  append(AssetKind.label(a.kind))
  if (a.kind == "CDT") {
    a.ratePct?.let { append(" · ${pct(it)} % E.A.") }
    a.endDate?.let { end ->
      val days = ((end - System.currentTimeMillis()) / 86_400_000L)
      append(if (days > 0) " · vence en $days días" else " · vencido")
    }
  }
  if (a.kind == "ACCIONES") {
    a.units?.let { append(" · ${pct(it)} und") }
    a.returnPct()?.let { r -> append(" · " + (if (r > 0) "+" else "") + "%.1f".format(java.util.Locale.US, r).replace('.', ',') + " %") }
    if (!hide && a.buyPrice != null && a.sellPrice != null) {
      append("\nCompra ${money(a.buyPrice)} → venta ${money(a.sellPrice)}")
      a.gain()?.let { append(" · " + (if (it < 0) "pérdida " else "ganancia ") + money(kotlin.math.abs(it))) }
    }
  }
}

/**
 * Detalle de un tipo de activo: crece desde su casilla y muestra el total acumulado y cada registro guardado
 * en esa sección, con botones para editarlo o eliminarlo.
 */
@Composable
fun AssetKindDetail(state: FinanceState, kind: String, origin: androidx.compose.ui.geometry.Rect, hide: Boolean, actions: WorthActions, onClosed: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val tint = paletteColor(AssetKind.colorIdx(kind), fin.isDark)
  val items = state.assets.filter { it.kind == kind }
  val total = items.sumOf { it.currentValue() }
  var confirm by remember { mutableStateOf<Asset?>(null) }
  LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onClosed() }
  com.aura.finanzas.ui.components.GrowOverlay(origin, onClosed, tint = Brush.linearGradient(listOf(tint, tint.copy(0.7f))), card = true) { close ->
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(top = 24.dp, bottom = 18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IsoIcon(AssetKind.icon(kind), tint, size = 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
          Text(AssetKind.label(kind).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface)
          Text("Tipo de activo · ${items.size} " + if (items.size == 1) "registro" else "registros", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
      }
      Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tint.copy(0.14f)).padding(16.dp)) {
        Text("Monto total acumulado", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        AnimatedAmount(total, MaterialTheme.typography.headlineSmall, if (fin.isDark) tint else lerp(tint, Color.Black, 0.3f), hidden = hide)
        if (state.assetsTotal > 0) Text("${((total / state.assetsTotal) * 100).roundToInt()} % de tus activos", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
      SectionTitle("Registros guardados en esta sección", null, {})
      items.forEach { a ->
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLowest).border(1.dp, tint.copy(0.35f), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(assetSubtitle(a, hide), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(if (hide) "••••" else money(a.currentValue()), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = fin.income)
          }
          Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, ripple()) { actions.editAsset(a) },
            contentAlignment = Alignment.Center,
          ) { GlyphIcon(Glyph.EDIT, scheme.primary, size = 20.dp) }
          Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, ripple()) { confirm = a },
            contentAlignment = Alignment.Center,
          ) { GlyphIcon(Glyph.TRASH, scheme.error, size = 20.dp) }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.TextButton({ actions.editAsset(Asset(name = "", kind = kind, value = 0.0, icon = AssetKind.icon(kind))) }) {
          Text("+ Agregar", fontWeight = FontWeight.Bold, color = scheme.primary)
        }
        androidx.compose.material3.TextButton(close) { Text("Cerrar", fontWeight = FontWeight.Bold, color = scheme.primary) }
      }
    }
  }
  confirm?.let { a ->
    ConfirmDialog("¿Eliminar ${a.name}?", "Se quita de tu patrimonio. No se puede deshacer.", { confirm = null; actions.deleteAsset(a) }) { confirm = null }
  }
}

/** Casilla de un tipo de activo, con su valor y su porcentaje; al tocarla se abre su detalle desde ella. */
@Composable
private fun KindTile(kind: String, value: Double, share: Double, hide: Boolean, modifier: Modifier, onOpen: (androidx.compose.ui.geometry.Rect) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  val tint = paletteColor(AssetKind.colorIdx(kind), dark)
  val shape = RoundedCornerShape(16.dp)
  val interaction = remember { MutableInteractionSource() }
  var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
  val text = if (hide) "••••" else money(value)
  Column(
    modifier.pressScale(interaction, to = 0.94f).onGloballyPositioned { bounds = it.boundsInRoot() }.clip(shape)
      .background(tint.copy(if (dark) 0.30f else 0.36f)).border(1.5.dp, tint.copy(0.65f), shape)
      .clickable(interaction, ripple()) { onOpen(bounds) }.padding(vertical = 10.dp, horizontal = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(AssetKind.label(kind), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
    Text(
      text, fontWeight = FontWeight.ExtraBold, color = if (dark) tint else lerp(tint, Color.Black, 0.25f), maxLines = 1,
      fontSize = when { text.length <= 11 -> 15.sp; text.length <= 13 -> 13.sp; else -> 11.sp },
    )
    Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
  }
}

@Composable
private fun Amount(v: Double, hide: Boolean, color: Color, big: Boolean = false) = Text(
  if (hide) "••••" else money(v),
  style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = color, maxLines = 1,
)

@Composable
private fun StatTile(title: String, value: Double, color: Color, hide: Boolean, modifier: Modifier) {
  GlassCard(modifier) {
    Column(Modifier.padding(14.dp)) {
      Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      Text(
        if (hide) "••••" else money(value), style = MaterialTheme.typography.titleSmall, color = color,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun ItemRow(icon: String, tint: Color, title: String, subtitle: String, trailing: @Composable () -> Unit, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
      IsoIcon(icon, tint, size = 46.dp)
      Column(Modifier.padding(start = 12.dp).weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
      }
      Box(Modifier.padding(start = 8.dp)) { trailing() }
    }
  }
}

/** Presupuesto: topes de gasto (de todas las cuentas o de una), metas de ahorro y gastos fijos. */
@Composable
fun BudgetScreen(state: FinanceState, hide: Boolean, actions: WorthActions) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var accountSel by rememberSaveable { mutableLongStateOf(com.aura.finanzas.data.ALL_ACCOUNTS) }
  // Si la cuenta elegida se elimina, se vuelve a "Todas las cuentas".
  if (accountSel != com.aura.finanzas.data.ALL_ACCOUNTS && state.accountById[accountSel] == null && state.accounts.isNotEmpty()) accountSel = com.aura.finanzas.data.ALL_ACCOUNTS
  val scheme = MaterialTheme.colorScheme
  LazyColumn(
    Modifier.fillMaxSize().statusBarsPadding(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = BottomBarSpace),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item(key = "title") { ScreenHeader("MI PRESUPUESTO", Modifier.enterStagger(0), "Topes de gasto, ahorros y pagos fijos") }
    item(key = "tabs") {
      Segmented(
        listOf("Gastos 📊", "Ahorros 🎯", "Fijos 🔄"), tab, { tab = it }, Modifier.enterStagger(1),
        height = 44.dp, container = scheme.surfaceContainerLowest.copy(0.85f), textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      )
    }
    // Las claves incluyen la sección para que cada cambio de pestaña vuelva a animar la entrada.
    when (tab) {
      0 -> budgets(state, hide, actions, accountSel) { accountSel = it }
      1 -> goals(state, hide, actions)
      else -> fixed(state, hide, actions)
    }
  }
}

/** Gasto del mes de la cuenta [account] ([ALL_ACCOUNTS] = todas): el total y el de cada categoría. */
private fun monthSpend(state: FinanceState, account: Long): Pair<Double, Map<Long?, Double>> {
  if (account == com.aura.finanzas.data.ALL_ACCOUNTS) return state.monthExpense to state.monthSpent
  val start = LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
  val list = state.txs.filter { it.accountId == account && it.type == com.aura.finanzas.data.TxType.EXPENSE && it.date >= start }
  return list.sumOf { it.amount } to list.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amount } }
}

// ---------- Presupuestos ----------

private fun LazyListScope.budgets(state: FinanceState, hide: Boolean, actions: WorthActions, account: Long, onAccount: (Long) -> Unit) {
  val mine = state.budgets.filter { it.accountId == account }
  val total = mine.firstOrNull { it.isTotal && it.monthlyLimit > 0 }
  val withLimit = mine.filter { !it.isTotal && it.monthlyLimit > 0 }
  val (monthTotal, monthBy) = monthSpend(state, account)
  val scopeName = state.accountById[account]?.name
  // Selector de cuenta: el presupuesto se arma para todas las cuentas juntas o para una sola.
  item(key = "bg-accounts") {
    Column(Modifier.enterStagger(2)) {
      SectionTitle("Presupuesto de", null, {})
      LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { CheckChip("Todas las cuentas", account == com.aura.finanzas.data.ALL_ACCOUNTS) { onAccount(com.aura.finanzas.data.ALL_ACCOUNTS) } }
        items(state.accounts, key = { it.id }) { a -> CheckChip(a.name, account == a.id) { onAccount(a.id) } }
      }
    }
  }
  // Tope de todos los gastos del mes de la cuenta elegida (o de todas), sin importar la categoría
  item(key = "bg-total-$account") {
    val scheme = MaterialTheme.colorScheme
    val today = LocalDate.now()
    GlassCard(
      Modifier.fillMaxWidth().enterStagger(3), level = 2, shape = RoundedCornerShape(24.dp),
      onClick = { actions.editBudget(total ?: Budget(categoryId = com.aura.finanzas.data.TOTAL_BUDGET, monthlyLimit = 0.0, accountId = account)) },
    ) {
      Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IsoIcon("CHART", scheme.primary, size = 40.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (scopeName == null) "Todos los gastos del mes" else "Gastos del mes en $scopeName", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(
              if (total == null) "Sin tope todavía · toca para ponerlo" else if (hide) "Con tope mensual" else "${money(monthTotal)} de ${money(total.monthlyLimit)}",
              style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
          }
          if (total != null) {
            val fraction = (monthTotal / total.monthlyLimit).toFloat()
            Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.titleLarge, color = trafficColor(fraction))
          } else {
            GlyphIcon(Glyph.PLUS, scheme.primary, size = 22.dp)
          }
        }
        if (total != null) {
          val fraction = (monthTotal / total.monthlyLimit).toFloat()
          val left = total.monthlyLimit - monthTotal
          val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
          TrafficBar(fraction, Modifier.padding(top = 12.dp), height = 12.dp)
          Text(
            when {
              hide -> if (left < 0) "Sobregasto" else "Dentro del tope"
              left < 0 -> "Te pasaste por ${money(-left)}"
              else -> "Te quedan ${money(left)} · ${money(left / daysLeft)} por día en los $daysLeft días que faltan"
            },
            Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
            color = if (fraction * 100 >= total.alertPct) trafficColor(fraction) else scheme.onSurfaceVariant,
          )
        } else {
          Text(
            if (scopeName == null) "Un solo tope para todo lo que gastas en el mes, sumando todas las cuentas y categorías. Te aviso antes de pasarte."
            else "Un tope para lo que gastas en el mes desde $scopeName. Te aviso antes de pasarte.",
            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface,
          )
        }
      }
    }
  }
  item(key = "bg-title-$account") { SectionTitle("Por categoría", "Nuevo", { actions.editBudget(Budget(categoryId = 0, monthlyLimit = 0.0, accountId = account)) }, Modifier.enterStagger(4)) }
  if (withLimit.isEmpty()) {
    item(key = "bg-empty-$account") {
      EmptyCard("TARGET", "Sin presupuestos por categoría", "Ponle un tope mensual a Mercado, Transporte, Entretenimiento… y mira cuánto te queda en cada una.", Modifier.enterStagger(5)) {
        actions.editBudget(Budget(categoryId = 0, monthlyLimit = 0.0, accountId = account))
      }
    }
  } else {
    item(key = "bg-summary-$account") {
      val limit = withLimit.sumOf { it.monthlyLimit }
      val spent = withLimit.sumOf { monthBy[it.categoryId] ?: 0.0 }
      SectionHint(
        (if (withLimit.size == 1) "1 categoría con tope" else "${withLimit.size} categorías con tope") + if (hide) "" else " · llevas ${money(spent)} de ${money(limit)}",
        Modifier.padding(horizontal = 6.dp),
      )
    }
  }
  items(withLimit, key = { "budget-${it.id}" }) { b ->
    val scheme = MaterialTheme.colorScheme
    val dark = LocalFin.current.isDark
    val cat = state.categoryById[b.categoryId]
    val spent = monthBy[b.categoryId] ?: 0.0
    val fraction = (spent / b.monthlyLimit).toFloat()
    val left = b.monthlyLimit - spent
    GlassCard(Modifier.fillMaxWidth(), onClick = { actions.editBudget(b) }) {
      Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IsoIcon(cat?.icon ?: "COIN", paletteColor(cat?.colorIdx ?: 5, dark), size = 40.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(cat?.name ?: "Categoría eliminada", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(if (hide) "••••" else "${money(spent)} de ${money(b.monthlyLimit)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = trafficColor(fraction))
        }
        TrafficBar(fraction, Modifier.padding(top = 12.dp))
        Text(
          when {
            hide -> if (left < 0) "Sobregasto" else "Dentro del límite"
            left < 0 -> "Te pasaste por ${money(-left)}"
            fraction * 100 >= b.alertPct -> "Cuidado: te quedan ${money(left)}"
            else -> "Te quedan ${money(left)}"
          },
          style = MaterialTheme.typography.bodySmall, color = if (fraction * 100 >= b.alertPct) trafficColor(fraction) else scheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
    }
  }
}

// ---------- Gastos fijos y suscripciones ----------

private fun LazyListScope.fixed(state: FinanceState, hide: Boolean, actions: WorthActions) {
  val month = YearMonth.now().toString()
  val today = LocalDate.now().dayOfMonth
  val active = state.fixed.filter { it.active }
  item(key = "fx-summary") {
    val scheme = MaterialTheme.colorScheme
    val fin = LocalFin.current
    Row(Modifier.fillMaxWidth().enterStagger(0), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatTile("Total al mes", active.sumOf { it.amount }, scheme.onSurface, hide, Modifier.weight(1f))
      StatTile("Por pagar", active.filter { it.lastPaidMonth != month }.sumOf { it.amount }, fin.expense, hide, Modifier.weight(1f))
      StatTile("Suscripciones", active.filter { it.subscription }.sumOf { it.amount }, scheme.primary, hide, Modifier.weight(1f))
    }
  }
  item(key = "fx-title") { SectionTitle("Pagos de cada mes", "Nuevo", { actions.editFixed(FixedExpense(name = "", amount = 0.0)) }) }
  if (state.fixed.isEmpty()) {
    item(key = "fx-empty") { EmptyCard("BULB", "Sin gastos fijos", "Arriendo, servicios, internet, plataformas de streaming…", onClick = null) }
  }
  items(state.fixed, key = { "fixed-${it.id}" }) { f ->
    val scheme = MaterialTheme.colorScheme
    val fin = LocalFin.current
    val paid = f.lastPaidMonth == month
    val (status, color) = when {
      !f.active -> "Pausado" to scheme.onSurfaceVariant
      paid -> "Pagado este mes" to fin.ok
      f.dayOfMonth < today -> "Vencido hace ${today - f.dayOfMonth} días" to fin.danger
      f.dayOfMonth == today -> "Vence hoy" to fin.warn
      f.dayOfMonth - today <= 5 -> "Vence en ${f.dayOfMonth - today} días" to fin.warn
      else -> "Día ${f.dayOfMonth}" to scheme.onSurfaceVariant
    }
    GlassCard(Modifier.fillMaxWidth(), onClick = { actions.editFixed(f) }) {
      Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IsoIcon(f.icon, scheme.primary, size = 44.dp, muted = !f.active)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
          Text(f.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text((if (f.subscription) "Suscripción · " else "") + status, style = MaterialTheme.typography.bodySmall, color = color)
          Amount(f.amount, hide, scheme.onSurface)
        }
        if (f.active) PaidToggle(paid) { if (paid) actions.unpayFixed(f) else actions.payFixed(f) }
      }
    }
  }
}

/** Interruptor de dos estados para un gasto fijo: "No pagado" (por pagar) o "Pagado ✓". */
@Composable
private fun PaidToggle(paid: Boolean, onToggle: () -> Unit) {
  val fin = LocalFin.current
  val scheme = MaterialTheme.colorScheme
  val color = if (paid) fin.ok else fin.expense
  val shape = RoundedCornerShape(14.dp)
  val interaction = remember { MutableInteractionSource() }
  Box(
    Modifier.pressScale(interaction, to = 0.92f).height(40.dp).clip(shape).background(color.copy(0.14f)).border(1.5.dp, color.copy(0.6f), shape)
      .clickable(interaction, ripple(), onClick = onToggle).padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(if (paid) "Pagado ✓" else "No pagado", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (paid) color else scheme.onSurface)
  }
}

// ---------- Metas de ahorro ----------

private fun LazyListScope.goals(state: FinanceState, hide: Boolean, actions: WorthActions) {
  item(key = "gl-title") { SectionTitle("Metas de ahorro", "Nueva", { actions.editGoal(Goal(name = "", target = 0.0)) }, Modifier.enterStagger(0)) }
  if (state.goals.isEmpty()) {
    item(key = "gl-empty") { EmptyCard("TARGET", "Sin metas todavía", "Un viaje, un fondo de emergencia, la cuota inicial…", Modifier.enterStagger(1), onClick = null) }
  }
  items(state.goals, key = { "goal-${it.id}" }) { g ->
    val scheme = MaterialTheme.colorScheme
    val color = paletteColor(g.colorIdx, LocalFin.current.isDark)
    val fraction = if (g.target > 0) (g.saved / g.target).toFloat().coerceIn(0f, 1f) else 0f
    val left = (g.target - g.saved).coerceAtLeast(0.0)
    GlassCard(Modifier.fillMaxWidth(), tint = color, onClick = { actions.editGoal(g) }) {
      Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IsoIcon(g.icon, color, size = 48.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(g.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (hide) "••••" else "${money(g.saved)} de ${money(g.target)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(10.dp).clip(CircleShape).background(scheme.onSurface.copy(0.10f))) {
          Box(Modifier.fillMaxWidth(fraction).height(10.dp).clip(CircleShape).background(color))
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          val hint = when {
            left <= 0 -> "¡Meta cumplida!"
            hide -> "En progreso"
            g.deadline != null -> {
              val months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(java.time.Instant.ofEpochMilli(g.deadline).atZone(java.time.ZoneId.systemDefault()))).coerceAtLeast(1)
              "Faltan ${money(left)} · ahorra ${money(left / months)} al mes"
            }
            else -> "Faltan ${money(left)}"
          }
          Text(hint, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurface.copy(0.8f))
          if (left > 0) Pill("Aportar", true) { actions.contribute(g) }
        }
      }
    }
  }
}
