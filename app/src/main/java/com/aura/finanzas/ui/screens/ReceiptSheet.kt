package com.aura.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.finanzas.FinanceState
import com.aura.finanzas.ReceiptItem
import com.aura.finanzas.ReceiptScan
import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphButton
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.groupDigits
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.theme.LocalFin
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val receiptDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Un renglón de la factura mientras se revisa: se puede desmarcar, renombrar, corregir su valor y cambiar de categoría. */
private class Line(item: ReceiptItem) {
  var on by mutableStateOf(true)
  var name by mutableStateOf(item.name)
  var digits by mutableStateOf(item.amount.toLong().takeIf { it > 0 }?.toString() ?: "")
  var categoryId by mutableStateOf(item.categoryId)
  /** Categoría que eligió la IA (si la persona la cambia, deja de mostrarse la marca de IA). */
  val aiCategory = if (item.byAi) item.categoryId else null
  val doubt = item.doubt
  val type = item.type
  val amount get() = digits.toDoubleOrNull() ?: 0.0
}

/**
 * Factura leída producto por producto: se revisa la lista y cada producto marcado se registra como un gasto.
 * Si la lectura salió mal: [onReadAgain] vuelve a leer la misma foto con más cuidado y [onNewPhoto] toma otra.
 */
@Composable
fun ReceiptSheet(
  state: FinanceState, scan: ReceiptScan, onSave: (List<Tx>) -> Unit,
  onReadAgain: (() -> Unit)? = null, onNewPhoto: (() -> Unit)? = null, onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val lines = remember(scan) { scan.items.map(::Line) }
  var accountId by remember(scan) { mutableStateOf(state.accountById[scan.accountId]?.id ?: state.accounts.firstOrNull()?.id) }
  var date by remember(scan) { mutableStateOf(scan.date) }
  var pickDate by remember { mutableStateOf(false) }
  var allOpen by remember { mutableStateOf(false) }
  val categories = state.categories.filter { it.kind == TxType.EXPENSE }
  val voice = scan.voice
  val zone = ZoneId.systemDefault()
  val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()

  val chosen = lines.filter { it.on }
  val sum = chosen.sumOf { it.amount }
  val valid = accountId != null && chosen.isNotEmpty() && chosen.all { it.amount > 0 && it.name.isNotBlank() }

  FormSheet(onDismiss) { close ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(if (voice) "Dictado: ${lines.size} movimientos" else "Factura: ${lines.size} productos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
      GlyphButton(Glyph.CLOSE, "Cerrar", onClick = close)
    }
    Text(
      if (voice) "Esto fue lo que entendí. Cada movimiento marcado se registra por separado, con su propio valor y categoría. Desmarca lo que no quieras y corrige tocando."
      else "Esto fue lo que leí. Cada producto marcado se registra como un gasto aparte. Los valores ya incluyen el IVA. Desmarca lo que no quieras y corrige nombres o valores tocándolos.",
      style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
    )
    if (!voice && (onReadAgain != null || onNewPhoto != null)) {
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.primary.copy(0.07f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          if (scan.careful) "¿Sigue mal? Prueba con otra foto: plana, con buena luz y la factura completa."
          else "¿Lo leí mal? Puedo leer la misma foto con más cuidado (tarda un poco más) o puedes tomar otra.",
          style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (onReadAgain != null && !scan.careful) Pill("Leer de nuevo", true, Modifier.weight(1f), onClick = onReadAgain)
          if (onNewPhoto != null) Pill("Otra foto", scan.careful, Modifier.weight(1f), leading = { GlyphIcon(Glyph.CAMERA, if (scan.careful) scheme.onPrimary else scheme.primary, size = 18.dp) }, onClick = onNewPhoto)
        }
      }
    }
    if (lines.any { it.aiCategory != null }) {
      Text(
        "✨ La IA clasificó los productos marcados con «IA». Toca una categoría para cambiarla.",
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
      )
    }

    if (state.accounts.isNotEmpty()) {
      Label(if (voice) "Cuenta" else "Cuenta de cargo")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { a -> CheckChip(a.name, accountId == a.id) { accountId = a.id } }
      }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FieldBox("Fecha (DD/MM/AAAA)", Modifier.weight(1f), onClick = { pickDate = true }) {
        Text(day.format(receiptDate), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
      }
      Box(Modifier.padding(top = 9.dp)) { GlyphButton(Glyph.CALENDAR, "Elegir fecha") { pickDate = true } }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(if (voice) "Movimientos" else "Productos", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
      if (!voice) Box {
        TextButton({ allOpen = true }) { Text("Categoría para todos") }
        CategoryMenu(allOpen, categories, { allOpen = false }) { c -> lines.forEach { it.categoryId = c.id } }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      lines.forEach { l ->
        val income = l.type == TxType.INCOME
        LineRow(l, state.categoryById[l.categoryId], if (income) state.categories.filter { it.kind == TxType.INCOME } else categories, if (income) fin.income else fin.expense)
      }
    }

    Column {
      Row {
        Text((if (voice) "Gastos marcados" else "Suma de lo marcado") + " (${chosen.count { it.type != TxType.INCOME }})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Text(money(chosen.filter { it.type != TxType.INCOME }.sumOf { it.amount }), style = MaterialTheme.typography.titleMedium, color = fin.expense)
      }
      val incomes = chosen.filter { it.type == TxType.INCOME }
      if (incomes.isNotEmpty()) Row {
        Text("Ingresos marcados (${incomes.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Text(money(incomes.sumOf { it.amount }), style = MaterialTheme.typography.titleMedium, color = fin.income)
      }
      val printed = scan.total
      if (printed != null && kotlin.math.abs(printed - sum) >= 1) {
        Text(
          "El total impreso en la factura es ${money(printed)}. Los valores ya incluyen el IVA, así que la diferencia son productos o descuentos que no se leyeron bien.",
          Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
      }
      if (lines.any { it.doubt && it.on }) {
        Text("Los resaltados en rojo no se leyeron con claridad: revisa su valor.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = scheme.error)
      }
    }

    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(close) { Text("Cancelar") }
      PrimaryButton(if (voice) "Registrar ${chosen.size} por separado" else if (chosen.size == 1) "Registrar 1 gasto" else "Registrar ${chosen.size} gastos", valid, Modifier.weight(1f)) {
        val tag = scan.merchant.replace(",", " ").trim().ifBlank { "Factura" }.take(40)
        // Cada gasto lleva un milisegundo menos que el anterior para que la lista conserve el orden de la factura.
        onSave(chosen.mapIndexed { i, it -> Tx(type = it.type, amount = it.amount, accountId = accountId!!, categoryId = it.categoryId, note = it.name.trim(), date = date - i, tags = if (voice) "" else tag, source = if (voice) "VOZ" else "RECIBO") })
        close()
      }
    }

    if (pickDate) {
      val pickerState = rememberDatePickerState(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
      DatePickerDialog(
        onDismissRequest = { pickDate = false },
        confirmButton = {
          TextButton({
            pickerState.selectedDateMillis?.let { ms ->
              date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            }
            pickDate = false
          }) { Text("Aceptar") }
        },
        dismissButton = { TextButton({ pickDate = false }) { Text("Cancelar") } },
      ) { DatePicker(pickerState) }
    }
  }
}

@Composable
private fun CategoryMenu(open: Boolean, categories: List<Category>, onClose: () -> Unit, onPick: (Category) -> Unit) {
  DropdownMenu(open, onClose) {
    categories.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { onPick(c); onClose() }) }
  }
}

@Composable
private fun LineRow(l: Line, category: Category?, categories: List<Category>, amountColor: Color) {
  val scheme = MaterialTheme.colorScheme
  val warn = l.doubt && l.on
  val shape = RoundedCornerShape(16.dp)
  var menu by remember { mutableStateOf(false) }
  val ink = if (l.on) scheme.onSurface else scheme.onSurface.copy(0.38f)
  Row(
    Modifier.fillMaxWidth().clip(shape).background(if (warn) scheme.error.copy(0.08f) else scheme.onSurface.copy(0.04f))
      .then(if (warn) Modifier.border(1.dp, scheme.error.copy(0.45f), shape) else Modifier).padding(start = 2.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable { l.on = !l.on }, contentAlignment = Alignment.Center) {
      Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (l.on) scheme.primary else Color.Transparent).border(1.5.dp, if (l.on) scheme.primary else scheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
      ) { if (l.on) GlyphIcon(Glyph.CHECK, scheme.onPrimary, size = 14.dp) }
    }
    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
      BasicTextField(
        l.name, { l.name = it.take(80) }, Modifier.fillMaxWidth(), enabled = l.on, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ink, fontWeight = FontWeight.Medium),
        cursorBrush = SolidColor(scheme.primary), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      )
      Box {
        Text(
          (category?.name ?: "Sin categoría") + (if (l.aiCategory != null && l.categoryId == l.aiCategory) " · IA ✨" else "") + " ▾", Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = l.on) { menu = true }.padding(vertical = 3.dp),
          style = MaterialTheme.typography.labelMedium, color = if (l.on) scheme.primary else ink,
        )
        CategoryMenu(menu, categories, { menu = false }) { l.categoryId = it.id }
      }
    }
    val shown = groupDigits(l.digits)
    BasicTextField(
      value = TextFieldValue(shown, TextRange(shown.length)),
      onValueChange = { v -> l.digits = v.text.filter(Char::isDigit).trimStart('0').take(9) },
      modifier = Modifier.width(96.dp), enabled = l.on, singleLine = true,
      textStyle = MaterialTheme.typography.titleSmall.copy(color = if (!l.on) ink else if (warn) scheme.error else amountColor, textAlign = TextAlign.End),
      cursorBrush = SolidColor(scheme.primary), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      decorationBox = { inner ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("$", style = MaterialTheme.typography.titleSmall, color = ink.copy(0.6f))
          Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) Text("valor", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall, color = scheme.error, textAlign = TextAlign.End)
            inner()
          }
        }
      },
    )
  }
}
