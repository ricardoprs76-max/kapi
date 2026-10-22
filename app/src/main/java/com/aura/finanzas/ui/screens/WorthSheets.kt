package com.aura.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aura.finanzas.FinanceState
import com.aura.finanzas.data.Asset
import com.aura.finanzas.data.Budget
import com.aura.finanzas.data.Debt
import com.aura.finanzas.data.FixedExpense
import com.aura.finanzas.data.Goal
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.ColorPicker
import com.aura.finanzas.ui.components.IconPicker
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.Segmented
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.paletteColor
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Muestra los dígitos con puntos de miles mientras se escribe (1234567 → 1.234.567). */
internal object Thousands : VisualTransformation {
  override fun filter(text: AnnotatedString): TransformedText {
    val s = text.text
    val out = StringBuilder()
    for (i in s.indices) {
      out.append(s[i])
      val rest = s.length - 1 - i
      if (rest > 0 && rest % 3 == 0) out.append('.')
    }
    val shown = out.toString()
    val mapping = object : OffsetMapping {
      override fun originalToTransformed(offset: Int): Int {
        val total = (s.length - 1).coerceAtLeast(0) / 3
        val after = (s.length - 1 - offset).coerceAtLeast(0) / 3
        return (offset + total - after).coerceIn(0, shown.length)
      }
      override fun transformedToOriginal(offset: Int): Int =
        shown.take(offset.coerceIn(0, shown.length)).count { it != '.' }
    }
    return TransformedText(AnnotatedString(shown), mapping)
  }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, label: String = "Nombre") = OutlinedTextField(
  value, { onChange(it.take(40)) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(20.dp),
  keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
)

@Composable
private fun AmountField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) = OutlinedTextField(
  value, { v -> onChange(v.filter(Char::isDigit).trimStart('0').take(13)) }, modifier, label = { Text(label) }, prefix = { Text("$ ") },
  singleLine = true, shape = RoundedCornerShape(20.dp), visualTransformation = Thousands,
  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
)

/** Campo numérico corto (tasa, plazo, día); con [decimal] admite coma o punto. */
@Composable
private fun SmallNumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = false, suffix: String? = null) = OutlinedTextField(
  value, { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) }, modifier, label = { Text(label) },
  suffix = suffix?.let { { Text(it) } }, singleLine = true, shape = RoundedCornerShape(20.dp),
  keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
)

private fun amountText(v: Double) = if (v > 0) v.toLong().toString() else ""
private fun String.toRate() = replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
private fun rateText(v: Double?) = v?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: ""
private fun monthsBetween(start: Long, end: Long): Long {
  val zone = ZoneId.systemDefault()
  return ChronoUnit.MONTHS.between(Instant.ofEpochMilli(start).atZone(zone), Instant.ofEpochMilli(end).atZone(zone).plusDays(1))
}
private fun plusMonths(start: Long, months: Long): Long =
  Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).plusMonths(months).toInstant().toEpochMilli()

@Composable
fun AssetSheet(initial: Asset, onSave: (Asset) -> Unit, onDelete: (Asset) -> Unit, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var kind by rememberSaveable { mutableStateOf(initial.kind) }
  var value by rememberSaveable { mutableStateOf(amountText(initial.value)) }
  var rate by rememberSaveable { mutableStateOf(rateText(initial.ratePct)) }
  var months by rememberSaveable {
    mutableStateOf(if (initial.startDate != null && initial.endDate != null) monthsBetween(initial.startDate, initial.endDate).toString() else "")
  }
  var units by rememberSaveable { mutableStateOf(rateText(initial.units)) }
  var buy by rememberSaveable { mutableStateOf(amountText(initial.buyPrice ?: 0.0)) }
  var sell by rememberSaveable { mutableStateOf(amountText(initial.sellPrice ?: 0.0)) }
  var confirmDelete by remember { mutableStateOf(false) }
  val cdt = kind == "CDT"
  val stock = kind == "ACCIONES"
  val dark = LocalFin.current.isDark
  val fin = LocalFin.current
  val count = units.toRate() ?: 0.0
  val buyPrice = buy.toDoubleOrNull() ?: 0.0
  val sellPrice = sell.toDoubleOrNull() ?: 0.0

  FormSheet(onDismiss) { close ->
    SheetTitle(if (isNew) "Nuevo activo" else "Editar activo")
    NameField(name, { name = it })
    Label("Tipo")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(AssetKind.all) { k ->
        Pill(AssetKind.label(k), kind == k, leading = { IsoIcon(AssetKind.icon(k), paletteColor(AssetKind.colorIdx(k), dark), size = 26.dp) }) { kind = k }
      }
    }
    if (stock) {
      SmallNumberField(units, { units = it }, "Cantidad de acciones", Modifier.fillMaxWidth(), decimal = true, suffix = "und")
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AmountField(buy, { buy = it }, "Precio de compra", Modifier.weight(1f))
        AmountField(sell, { sell = it }, "Precio de venta", Modifier.weight(1f))
      }
      Text(
        "Precios por acción. En \"venta\" pon el precio al que vendiste o, si aún las tienes, el precio de hoy.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (count > 0 && buyPrice > 0 && sellPrice > 0) {
        val gain = (sellPrice - buyPrice) * count
        val pct = (sellPrice - buyPrice) / buyPrice * 100
        val tone = if (gain < 0) fin.expense else fin.income
        Column(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(tone.copy(0.10f)).padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row {
            Text("Rentabilidad", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text((if (pct > 0) "+" else "") + "%.2f".format(java.util.Locale.US, pct).replace('.', ',') + " %", style = MaterialTheme.typography.titleMedium, color = tone)
          }
          Row {
            Text(if (gain < 0) "Pérdida" else "Ganancia", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(money(gain, sign = true), style = MaterialTheme.typography.bodyMedium, color = tone)
          }
          Row {
            Text("Invertido ${money(buyPrice * count)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Valor ${money(sellPrice * count)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    } else {
      AmountField(value, { value = it }, if (cdt) "Capital invertido" else "Valor actual")
    }
    if (cdt) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallNumberField(rate, { rate = it }, "Tasa E.A.", Modifier.weight(1f), decimal = true, suffix = "%")
        SmallNumberField(months, { months = it }, "Plazo", Modifier.weight(1f), suffix = "meses")
      }
      Text(
        "Te avisamos cuando el CDT esté por vencer.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    PrimaryButton(if (isNew) "Agregar activo" else "Guardar cambios", name.isNotBlank() && if (stock) count > 0 && buyPrice > 0 && sellPrice > 0 else value.isNotEmpty()) {
      val start = initial.startDate ?: System.currentTimeMillis()
      val term = months.toLongOrNull()?.takeIf { it > 0 }
      onSave(
        initial.copy(
          name = name.trim(), kind = kind, value = if (stock) count * sellPrice else value.toDouble(), icon = AssetKind.icon(kind),
          units = if (stock) count else null, buyPrice = if (stock) buyPrice else null, sellPrice = if (stock) sellPrice else null,
          ratePct = if (cdt) rate.toRate() else null,
          startDate = if (cdt) start else null,
          endDate = if (cdt && term != null) plusMonths(start, term) else null,
        ),
      )
      close()
    }
    if (!isNew) DeleteButton("Eliminar activo") { confirmDelete = true }
    if (confirmDelete) ConfirmDialog("¿Eliminar ${initial.name}?", "Dejará de sumar a tu patrimonio.", { confirmDelete = false; onDelete(initial); close() }) { confirmDelete = false }
  }
}

@Composable
fun DebtSheet(initial: Debt, onSave: (Debt) -> Unit, onDelete: (Debt) -> Unit, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var kind by rememberSaveable { mutableStateOf(initial.kind) }
  var balance by rememberSaveable { mutableStateOf(amountText(initial.balance)) }
  var rate by rememberSaveable { mutableStateOf(rateText(initial.ratePct)) }
  var payment by rememberSaveable { mutableStateOf(amountText(initial.monthlyPayment ?: 0.0)) }
  var day by rememberSaveable { mutableStateOf(initial.dueDay?.toString() ?: "") }
  var confirmDelete by remember { mutableStateOf(false) }

  FormSheet(onDismiss) { close ->
    SheetTitle(if (isNew) "Nueva deuda" else "Editar deuda")
    NameField(name, { name = it })
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(DebtKind.all) { k -> Pill(DebtKind.label(k), kind == k) { kind = k } }
    }
    AmountField(balance, { balance = it }, "Saldo que debes hoy")
    AmountField(payment, { payment = it }, "Cuota mensual (opcional)")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      SmallNumberField(rate, { rate = it }, "Tasa E.A.", Modifier.weight(1f), decimal = true, suffix = "%")
      SmallNumberField(day, { day = it.take(2) }, "Día de pago", Modifier.weight(1f))
    }
    PrimaryButton(if (isNew) "Agregar deuda" else "Guardar cambios", name.isNotBlank() && balance.isNotEmpty()) {
      onSave(
        initial.copy(
          name = name.trim(), kind = kind, balance = balance.toDouble(), ratePct = rate.toRate(),
          monthlyPayment = payment.toDoubleOrNull(), dueDay = day.toIntOrNull()?.coerceIn(1, 31), icon = DebtKind.icon(kind),
        ),
      )
      close()
    }
    if (!isNew) DeleteButton("Eliminar deuda") { confirmDelete = true }
    if (confirmDelete) ConfirmDialog("¿Eliminar ${initial.name}?", "Dejará de restar de tu patrimonio.", { confirmDelete = false; onDelete(initial); close() }) { confirmDelete = false }
  }
}

@Composable
fun BudgetSheet(state: FinanceState, initial: Budget, onSave: (Budget) -> Unit, onDelete: (Budget) -> Unit, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  val dark = LocalFin.current.isDark
  val scheme = MaterialTheme.colorScheme
  // Los presupuestos son de una cuenta (o de todas): las reglas de "uno por categoría" se miran dentro de esa cuenta.
  val sameScope = state.budgets.filter { it.id != initial.id && it.accountId == initial.accountId }
  val accountName = state.accountById[initial.accountId]?.name
  val hasTotal = sameScope.any { it.isTotal }
  var total by rememberSaveable { mutableStateOf(initial.isTotal) }
  // Cada categoría admite un solo presupuesto por cuenta: solo se ofrecen las que aún no tienen.
  val taken = sameScope.map { it.categoryId }.toSet()
  val zone = java.time.ZoneId.systemDefault()
  val monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val monthTxs = state.txs.filter { it.type == TxType.EXPENSE && it.date >= monthStart && (accountName == null || it.accountId == initial.accountId) }
  val options = state.categories.filter { it.kind == TxType.EXPENSE && it.id !in taken }
  var categoryId by rememberSaveable { mutableStateOf(initial.categoryId.takeIf { it > 0L }) }
  var limit by rememberSaveable { mutableStateOf(amountText(initial.monthlyLimit)) }
  var alert by rememberSaveable { mutableStateOf(initial.alertPct) }
  var confirmDelete by remember { mutableStateOf(false) }

  FormSheet(onDismiss) { close ->
    SheetTitle(if (isNew) "Nuevo presupuesto" else "Editar presupuesto")
    Text(if (accountName == null) "Para todas las cuentas" else "Para la cuenta $accountName", style = MaterialTheme.typography.titleSmall, color = scheme.primary)
    // Al crear se elige el alcance; uno ya creado no cambia de tipo.
    if (isNew && !hasTotal) Segmented(listOf("Todos los gastos", "Una categoría"), if (total) 0 else 1, { total = it == 0 })
    if (total) {
      val spentAll = monthTxs.sumOf { it.amount }
      Text(
        (if (accountName == null) "Tope para todo lo que gastas en el mes, sumando todas las cuentas y categorías." else "Tope para todo lo que gastas en el mes desde $accountName.") +
          if (spentAll > 0) " Este mes llevas ${money(spentAll)}." else "",
        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
      )
    } else {
      Label("Categoría")
      if (options.isEmpty()) {
        Text("Todas las categorías de gasto ya tienen presupuesto.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(options, key = { it.id }) { c ->
            Pill(c.name, categoryId == c.id, leading = { IsoIcon(c.icon, paletteColor(c.colorIdx, dark), size = 28.dp) }) { categoryId = c.id }
          }
        }
      }
    }
    AmountField(limit, { limit = it }, "Límite mensual")
    if (!total) categoryId?.let { id ->
      val spent = monthTxs.filter { it.categoryId == id }.sumOf { it.amount }
      if (spent > 0) Text("Este mes llevas ${money(spent)} en esta categoría.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
    Label("Avisarme al llegar al")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(50, 70, 80, 90).forEach { p -> Pill("$p %", alert == p, Modifier.weight(1f)) { alert = p } }
    }
    PrimaryButton(if (isNew) "Crear presupuesto" else "Guardar cambios", (total || categoryId != null) && (limit.toDoubleOrNull() ?: 0.0) > 0) {
      onSave(initial.copy(categoryId = if (total) com.aura.finanzas.data.TOTAL_BUDGET else categoryId!!, monthlyLimit = limit.toDouble(), alertPct = alert))
      close()
    }
    if (!isNew) DeleteButton("Eliminar presupuesto") { confirmDelete = true }
    if (confirmDelete) ConfirmDialog("¿Eliminar presupuesto?", "Tus movimientos no se tocan.", { confirmDelete = false; onDelete(initial); close() }) { confirmDelete = false }
  }
}

@Composable
fun FixedSheet(state: FinanceState, initial: FixedExpense, onSave: (FixedExpense) -> Unit, onDelete: (FixedExpense) -> Unit, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val isNew = initial.id == 0L
  val dark = LocalFin.current.isDark
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var amount by rememberSaveable { mutableStateOf(amountText(initial.amount)) }
  var day by rememberSaveable { mutableStateOf(initial.dayOfMonth.toString()) }
  var subscription by rememberSaveable { mutableStateOf(initial.subscription) }
  var active by rememberSaveable { mutableStateOf(initial.active) }
  var categoryId by rememberSaveable { mutableStateOf(initial.categoryId) }
  var accountId by rememberSaveable { mutableStateOf(initial.accountId) }
  var icon by rememberSaveable { mutableStateOf(initial.icon) }
  var confirmDelete by remember { mutableStateOf(false) }
  val month = java.time.YearMonth.now().toString()
  var paid by rememberSaveable { mutableStateOf(initial.lastPaidMonth == month) }

  FormSheet(onDismiss) { close ->
    SheetTitle(if (isNew) "Nuevo gasto fijo" else "Editar gasto fijo")
    NameField(name, { name = it })
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      AmountField(amount, { amount = it }, "Valor mensual", Modifier.weight(2f))
      SmallNumberField(day, { day = it.take(2) }, "Día", Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("Es una suscripción", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text("Streaming, apps, gimnasio…", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
      Switch(subscription, { subscription = it })
    }
    if (!isNew) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Activo", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Switch(active, { active = it })
      }
    }
    Label("Este mes")
    Segmented(listOf("No pagado", "Pagado ✓"), if (paid) 1 else 0, { paid = it == 1 }, height = 44.dp)
    Label("Categoría (opcional)")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(state.categories.filter { it.kind == TxType.EXPENSE }, key = { it.id }) { c ->
        Pill(c.name, categoryId == c.id, leading = { IsoIcon(c.icon, paletteColor(c.colorIdx, dark), size = 28.dp) }) {
          categoryId = if (categoryId == c.id) null else c.id
        }
      }
    }
    Label("Al pagar, descontar de (opcional)")
    if (state.accounts.isEmpty()) {
      Text("Aún no tienes cuentas.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    } else {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.accounts, key = { it.id }) { a ->
          Pill(a.name, accountId == a.id, leading = { IsoIcon(a.icon, paletteColor(a.colorIdx, dark), size = 28.dp) }) {
            accountId = if (accountId == a.id) null else a.id
          }
        }
      }
    }
    Text(
      if (accountId != null) "Al marcarlo como pagado se registra el gasto en esa cuenta (y se quita si lo vuelves a \"No pagado\")." else "Sin cuenta, \"Pagado\" solo lo marca y no registra el gasto.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    Label("Icono")
    IconPicker(icon, scheme.primary, { icon = it })
    PrimaryButton(if (isNew) "Agregar gasto fijo" else "Guardar cambios", name.isNotBlank() && amount.isNotEmpty()) {
      onSave(
        initial.copy(
          name = name.trim(), amount = amount.toDouble(), dayOfMonth = (day.toIntOrNull() ?: 1).coerceIn(1, 31),
          subscription = subscription, active = active, categoryId = categoryId, accountId = accountId, icon = icon,
          lastPaidMonth = if (paid) month else if (initial.lastPaidMonth == month) "" else initial.lastPaidMonth,
        ),
      )
      close()
    }
    if (!isNew) DeleteButton("Eliminar gasto fijo") { confirmDelete = true }
    if (confirmDelete) ConfirmDialog("¿Eliminar ${initial.name}?", "Los pagos ya registrados se conservan.", { confirmDelete = false; onDelete(initial); close() }) { confirmDelete = false }
  }
}

@Composable
fun GoalSheet(initial: Goal, onSave: (Goal) -> Unit, onDelete: (Goal) -> Unit, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var target by rememberSaveable { mutableStateOf(amountText(initial.target)) }
  var saved by rememberSaveable { mutableStateOf(amountText(initial.saved)) }
  var months by rememberSaveable {
    mutableStateOf(initial.deadline?.let { monthsBetween(System.currentTimeMillis(), it).coerceAtLeast(1).toString() } ?: "")
  }
  var colorIdx by rememberSaveable { mutableStateOf(initial.colorIdx) }
  var icon by rememberSaveable { mutableStateOf(initial.icon) }
  var confirmDelete by remember { mutableStateOf(false) }
  val tint = paletteColor(colorIdx, LocalFin.current.isDark)

  FormSheet(onDismiss) { close ->
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      IsoIcon(icon, tint, size = 52.dp)
      SheetTitle(if (isNew) "Nueva meta" else "Editar meta")
    }
    NameField(name, { name = it })
    AmountField(target, { target = it }, "¿Cuánto quieres reunir?")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      AmountField(saved, { saved = it }, "Ya tienes", Modifier.weight(2f))
      SmallNumberField(months, { months = it.take(3) }, "Plazo", Modifier.weight(1.3f), suffix = "meses")
    }
    Label("Color")
    ColorPicker(colorIdx, { colorIdx = it })
    Label("Icono")
    IconPicker(icon, tint, { icon = it })
    PrimaryButton(if (isNew) "Crear meta" else "Guardar cambios", name.isNotBlank() && target.isNotEmpty()) {
      val term = months.toLongOrNull()?.takeIf { it > 0 }
      onSave(
        initial.copy(
          name = name.trim(), target = target.toDouble(), saved = saved.toDoubleOrNull() ?: 0.0,
          deadline = term?.let { plusMonths(System.currentTimeMillis(), it) }, icon = icon, colorIdx = colorIdx,
        ),
      )
      close()
    }
    if (!isNew) DeleteButton("Eliminar meta") { confirmDelete = true }
    if (confirmDelete) ConfirmDialog("¿Eliminar ${initial.name}?", "Se pierde el seguimiento de esta meta.", { confirmDelete = false; onDelete(initial); close() }) { confirmDelete = false }
  }
}

@Composable
fun ContributeDialog(goal: Goal, onAdd: (Double) -> Unit, onDismiss: () -> Unit) {
  var amount by rememberSaveable { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Aportar a ${goal.name}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmountField(amount, { amount = it }, "Valor del aporte")
        Text("Faltan ${money((goal.target - goal.saved).coerceAtLeast(0.0))}. El aporte solo actualiza la meta; no mueve dinero entre cuentas.", style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton({ onAdd(amount.toDouble()); onDismiss() }, enabled = amount.isNotEmpty()) { Text("Aportar") } },
    dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
  )
}
