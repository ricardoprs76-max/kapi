package com.aura.finanzas.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.finanzas.FinanceState
import com.aura.finanzas.LocalBrain
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.AccountType
import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.groupDigits
import com.aura.finanzas.ui.components.GrowOverlay
import com.aura.finanzas.ui.components.LocalGrowOrigin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.aura.finanzas.ui.components.ColorPicker
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphButton
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.IconPicker
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.Segmented
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import com.aura.finanzas.ui.theme.accountBrush
import com.aura.finanzas.ui.theme.paletteColor
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val fieldDate = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Esquinas de los campos del formulario de registro: rectas y sobrias, como una ficha. */
private val TxCorner = 12.dp

/** Hoja con el contenido desplazable y el margen estándar de los formularios; sube con el teclado. */
@Composable
internal fun FormSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
  // Abierta desde el botón +: la hoja nace del botón en lugar de subir desde abajo.
  LocalGrowOrigin.current?.let { origin ->
    GrowOverlay(origin, onDismiss) { close ->
      Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) { content(close) }
    }
    return
  }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
  ModalBottomSheet(
    onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
  ) {
    Column(
      Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content(close) }
  }
}

@Composable
internal fun PrimaryButton(text: String, enabled: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val bg by animateColorAsState(if (enabled) scheme.primary else scheme.onSurface.copy(0.12f), Motion.effects(), label = "pb")
  Box(
    modifier.height(48.dp).pressScale(interaction, enabled).clip(RoundedCornerShape(24.dp)).background(bg)
      .clickable(interaction, ripple(), enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
      },
    contentAlignment = Alignment.Center,
  ) { Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant, maxLines = 1) }
}

@Composable
internal fun DeleteButton(text: String, onClick: () -> Unit) {
  TextButton(onClick, Modifier.fillMaxWidth().height(48.dp)) { Text(text, color = MaterialTheme.colorScheme.error) }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, onConfirm: () -> Unit, confirmText: String = "Eliminar", onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = { TextButton(onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } },
    dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
  )
}

@Composable
internal fun Label(text: String) =
  Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)

@Composable
internal fun SheetTitle(text: String) =
  Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)

/** Recuadro con borde y etiqueta flotante, igual al de los campos de texto, para contenido que no se escribe. */
@Composable
internal fun FieldBox(label: String, modifier: Modifier = Modifier, corner: Dp = 22.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(corner)
  Box(modifier.padding(top = 9.dp)) {
    Box(
      Modifier.fillMaxWidth().height(54.dp).clip(shape).border(1.5.dp, scheme.outline.copy(0.6f), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
      contentAlignment = Alignment.CenterStart,
    ) { content() }
    Text(
      label, Modifier.padding(start = 18.dp).offset(y = (-9).dp).background(scheme.surfaceContainer).padding(horizontal = 6.dp),
      style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
    )
  }
}

/** Registrar o editar un gasto o un ingreso. */
@Composable
fun TxSheet(state: FinanceState, initialType: String, existing: Tx?, presetAccountId: Long?, onSave: (Tx) -> Unit, onDelete: (Tx) -> Unit, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  // La categoría sugerida al abrir es la última que se usó para ese tipo de movimiento.
  fun defaultCategory(type: String): Long? =
    state.txs.firstOrNull { it.type == type && it.categoryId != null && state.categoryById.containsKey(it.categoryId) }?.categoryId
      ?: state.categories.firstOrNull { it.kind == type }?.id
  var type by rememberSaveable { mutableStateOf(existing?.type ?: initialType) }
  var digits by rememberSaveable { mutableStateOf(existing?.amount?.toLong()?.takeIf { it > 0 }?.toString() ?: "") }
  var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId ?: defaultCategory(existing?.type ?: initialType)) }
  var accountId by rememberSaveable { mutableStateOf(existing?.accountId ?: presetAccountId ?: state.accounts.firstOrNull()?.id) }
  val toAccountId = existing?.toAccountId
  var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
  // Un movimiento interpretado por la IA llega con id 0: se revisa y se guarda como nuevo.
  val isEdit = existing != null && existing.id != 0L
  var date by rememberSaveable { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
  var pickDate by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }
  var categoriesOpen by remember { mutableStateOf(false) }
  var noteFocused by remember { mutableStateOf(false) }
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  val done = { focus.clearFocus(); keyboard?.hide(); Unit }
  // Texto para el que ya se eligió (o descartó) una sugerencia: no se vuelve a ofrecer hasta que cambie.
  var settledNote by remember { mutableStateOf(existing?.note ?: "") }

  val amount = digits.toDoubleOrNull() ?: 0.0
  val transfer = type == TxType.TRANSFER
  val categories = state.categories.filter { it.kind == type }
  val valid = amount > 0 && accountId != null && (transfer || categoryId != null)
  val zone = ZoneId.systemDefault()
  val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate()
  val typeColor = when (type) { TxType.EXPENSE -> fin.expense; TxType.INCOME -> fin.income; else -> scheme.primary }

  FormSheet(onDismiss) { close ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(if (isEdit) "Editar Movimiento" else "Registrar Factura / Movimiento", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
      GlyphButton(Glyph.CLOSE, "Cerrar", onClick = close)
    }

    if (transfer) {
      Text("Transferencia entre cuentas", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
    } else {
      Segmented(
        listOf("Gasto", "Ingreso"), if (type == TxType.INCOME) 1 else 0,
        { i ->
          val picked = if (i == 1) TxType.INCOME else TxType.EXPENSE
          if (picked != type) { type = picked; categoryId = defaultCategory(picked); categoriesOpen = false }
        },
        thumb = typeColor, onThumb = Color.White, height = 44.dp,
      )
    }

    if (existing != null && !isEdit) {
      Text(
        (if (existing.source == "RECIBO") "Esto fue lo que leí del recibo." else "Esto fue lo que entendí.") + " Revisa valor, categoría, cuenta y fecha antes de registrar.",
        style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
      )
    }

    // Los dos campos de texto llevan el color del tipo de movimiento al enfocarse.
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = typeColor, focusedLabelColor = typeColor, cursorColor = typeColor)
    OutlinedTextField(
      digits, { v -> digits = v.filter(Char::isDigit).trimStart('0').take(12) }, Modifier.fillMaxWidth(),
      label = { Text("Monto / Cantidad ($)") }, singleLine = true, shape = RoundedCornerShape(TxCorner), colors = fieldColors,
      textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), visualTransformation = Thousands,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { done() }),
    )

    // Concepto, con sugerencias tomadas de registros anteriores que contienen lo escrito
    Column {
      OutlinedTextField(
        note, { note = it.take(80) }, Modifier.fillMaxWidth().onFocusChanged { noteFocused = it.isFocused },
        label = { Text("Concepto o Descripción breve") }, singleLine = true, shape = RoundedCornerShape(TxCorner), colors = fieldColors,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { done() }),
      )
      val suggestions = remember(state.txs, note, type) {
        val q = LocalBrain.norm(note.trim())
        if (q.length < 2) emptyList()
        else state.txs.asSequence().filter { it.type == type && it.note.isNotBlank() && LocalBrain.norm(it.note).contains(q) }
          .distinctBy { Triple(LocalBrain.norm(it.note), it.amount, it.categoryId) }.take(5).toList()
      }
      AnimatedVisibility(
        noteFocused && suggestions.isNotEmpty() && note != settledNote,
        enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()), exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
      ) {
        Column(
          Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(TxCorner)).background(scheme.surfaceContainerLowest)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(TxCorner)),
        ) {
          suggestions.forEachIndexed { i, s ->
            val c = state.categoryById[s.categoryId]
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(0.6f)))
            Row(
              Modifier.fillMaxWidth().clickable {
                note = s.note; settledNote = s.note
                digits = s.amount.toLong().toString()
                if (s.categoryId != null && state.categoryById.containsKey(s.categoryId)) categoryId = s.categoryId
              }.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IsoIcon(c?.icon ?: "COIN", paletteColor(c?.colorIdx ?: 5, fin.isDark), size = 38.dp)
              Text(s.note, Modifier.padding(horizontal = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text(money(s.amount), style = MaterialTheme.typography.titleMedium, color = typeColor)
            }
          }
        }
      }
    }

    if (state.accounts.isEmpty()) {
      Text("Primero crea una cuenta con \"+ Añadir\" en Inicio.", color = scheme.error, style = MaterialTheme.typography.bodyMedium)
    } else {
      Label(if (transfer) "Cuenta de origen" else "Cuenta de Cargo/Abono")
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.accounts.forEach { a -> CheckChip(a.name, accountId == a.id) { accountId = a.id } }
      }
      if (transfer) Text("Hacia: ${toAccountId?.let { state.accountById[it]?.name } ?: "?"}", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }

    if (!transfer) {
      val selected = state.categoryById[categoryId]
      val turn by animateFloatAsState(if (categoriesOpen) 180f else 0f, Motion.spatial(), label = "caret")
      Column {
        FieldBox("Categoría", corner = TxCorner, onClick = { done(); categoriesOpen = !categoriesOpen }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IsoIcon(selected?.icon ?: "COIN", paletteColor(selected?.colorIdx ?: 5, fin.isDark), size = 32.dp, muted = selected == null)
            Text(selected?.name ?: "Elige una categoría", Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            GlyphIcon(Glyph.DOWN, scheme.onSurfaceVariant, Modifier.graphicsLayer { rotationZ = turn })
          }
        }
        AnimatedVisibility(
          categoriesOpen,
          enter = expandVertically(Motion.spatial()) + fadeIn(Motion.effects()), exit = shrinkVertically(Motion.spatial()) + fadeOut(Motion.effects()),
        ) {
          FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { c ->
              Pill(c.name, categoryId == c.id, leading = { IsoIcon(c.icon, paletteColor(c.colorIdx, fin.isDark), size = 24.dp) }) { categoryId = c.id; categoriesOpen = false }
            }
          }
        }
      }
    }

    Text("Detalles Adicionales", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FieldBox("Fecha (DD/MM/AAAA)", Modifier.weight(1f), corner = TxCorner, onClick = { pickDate = true }) {
        Text(day.format(fieldDate), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
      }
      Box(Modifier.padding(top = 9.dp)) { GlyphButton(Glyph.CALENDAR, "Elegir fecha") { pickDate = true } }
    }

    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      TextButton(close) { Text("Cancelar") }
      PrimaryButton(if (isEdit) "Guardar" else "Registrar", valid, Modifier.weight(1f)) {
        val base = existing ?: Tx(type = type, amount = amount, accountId = accountId!!)
        onSave(
          base.copy(
            type = type, amount = amount, accountId = accountId!!, toAccountId = if (transfer) toAccountId else null,
            categoryId = if (transfer) null else categoryId, note = note.trim(), date = date,
          ),
        )
        close()
      }
    }
    if (isEdit) DeleteButton("Eliminar movimiento") { confirmDelete = true }

    if (pickDate) {
      val pickerState = rememberDatePickerState(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
      DatePickerDialog(
        onDismissRequest = { pickDate = false },
        confirmButton = {
          TextButton({
            pickerState.selectedDateMillis?.let { ms ->
              val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
              date = picked.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            }
            pickDate = false
          }) { Text("Aceptar") }
        },
        dismissButton = { TextButton({ pickDate = false }) { Text("Cancelar") } },
      ) { DatePicker(pickerState) }
    }
    if (confirmDelete && existing != null) {
      ConfirmDialog("¿Eliminar movimiento?", "El saldo de la cuenta se recalculará.", { confirmDelete = false; onDelete(existing); close() }) { confirmDelete = false }
    }
  }
}

/** Chip de elección única: el elegido se tiñe del color principal y muestra un visto. */
@Composable
internal fun CheckChip(text: String, selected: Boolean, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val bg by animateColorAsState(if (selected) scheme.primary.copy(0.12f) else scheme.onSurface.copy(0.04f), Motion.effects(), label = "chip")
  val edge by animateColorAsState(if (selected) scheme.primary.copy(0.45f) else scheme.outlineVariant, Motion.effects(), label = "chipe")
  val shape = RoundedCornerShape(10.dp)
  val interaction = remember { MutableInteractionSource() }
  Row(
    Modifier.pressScale(interaction, to = 0.94f).height(38.dp).clip(shape).background(bg).border(1.5.dp, edge, shape)
      .clickable(interaction, ripple()) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedVisibility(selected) { GlyphIcon(Glyph.CHECK, scheme.primary, Modifier.padding(end = 6.dp), size = 16.dp) }
    Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) scheme.primary else scheme.onSurface, maxLines = 1)
  }
}

@Composable
internal fun AccountRow(accounts: List<Account>, selected: Long?, onSelect: (Long) -> Unit) {
  val dark = LocalFin.current.isDark
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(accounts, key = { it.id }) { a ->
      Pill(a.name, selected == a.id, leading = { IsoIcon(a.icon, paletteColor(a.colorIdx, dark), size = 28.dp) }) { onSelect(a.id) }
    }
  }
}

/** Muestras con el degradado real de la tarjeta de la cuenta; la elegida se rodea con un aro. */
@Composable
private fun AccountColorPicker(selected: Int, onSelect: (Int) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    repeat(7) { i ->
      Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).clickable(remember { MutableInteractionSource() }, ripple()) { onSelect(i) }
          .border(if (i == selected) 2.5.dp else 0.dp, if (i == selected) scheme.onSurface else Color.Transparent, RoundedCornerShape(14.dp)).padding(5.dp)
          .clip(RoundedCornerShape(10.dp)).background(accountBrush(i)),
      )
    }
  }
}

/** Crear (id 0) o editar una cuenta. */
@Composable
fun AccountSheet(initial: Account, onSave: (Account) -> Unit, onDelete: (Account) -> Unit, movements: Int = 0, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var type by rememberSaveable { mutableStateOf(initial.type) }
  var balance by rememberSaveable { mutableStateOf(if (isNew || initial.initialBalance == 0.0) "" else initial.initialBalance.toLong().toString()) }
  var limit by rememberSaveable { mutableStateOf(if (initial.monthlyLimit > 0) initial.monthlyLimit.toLong().toString() else "") }
  var colorIdx by rememberSaveable { mutableStateOf(initial.colorIdx) }
  var confirmDelete by remember { mutableStateOf(false) }

  FormSheet(onDismiss) { close ->
    SheetTitle(if (isNew) "Nueva cuenta" else "Editar cuenta")
    OutlinedTextField(
      name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(22.dp),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Label("Tipo")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      items(AccountType.all) { t -> Pill(AccountType.label(t), type == t) { type = t } }
    }
    OutlinedTextField(
      balance, { v -> balance = v.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(),
      label = { Text("Saldo inicial (opcional)") }, prefix = { Text("$ ") },
      singleLine = true, shape = RoundedCornerShape(22.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Label("Color")
    AccountColorPicker(colorIdx) { colorIdx = it }
    PrimaryButton(if (isNew) "Crear cuenta" else "Guardar cambios", name.isNotBlank()) {
      onSave(initial.copy(name = name.trim(), type = type, initialBalance = balance.toDoubleOrNull() ?: 0.0, monthlyLimit = limit.toDoubleOrNull() ?: 0.0, icon = AccountType.icon(type), colorIdx = colorIdx))
      close()
    }
    if (!isNew) DeleteButton("Eliminar cuenta") { confirmDelete = true }
    if (confirmDelete) {
      ConfirmDialog(
        "¿Eliminar la cuenta ${initial.name}?",
        (when (movements) { 0 -> "La cuenta no tiene movimientos"; 1 -> "Se eliminará también su único movimiento"; else -> "Se eliminarán también sus $movements movimientos" }) +
          (if (initial.shareId.isNotEmpty()) " y dejarás de verla en este teléfono (la otra persona la conserva)" else "") + ". No se puede deshacer.",
        { confirmDelete = false; onDelete(initial); close() },
        confirmText = "Sí, eliminar",
      ) { confirmDelete = false }
    }
  }
}

/** Crear (id 0) o editar una categoría. */
@Composable
fun CategorySheet(initial: Category, onSave: (Category) -> Unit, onDelete: (Category) -> Unit, onDismiss: () -> Unit) {
  val isNew = initial.id == 0L
  var name by rememberSaveable { mutableStateOf(initial.name) }
  var kind by rememberSaveable { mutableStateOf(initial.kind) }
  var colorIdx by rememberSaveable { mutableStateOf(initial.colorIdx) }
  var icon by rememberSaveable { mutableStateOf(initial.icon) }
  var confirmDelete by remember { mutableStateOf(false) }
  val tint: Color = paletteColor(colorIdx, LocalFin.current.isDark)

  FormSheet(onDismiss) { close ->
    Row(verticalAlignment = Alignment.CenterVertically) {
      IsoIcon(icon, tint, size = 52.dp)
      Box(Modifier.padding(start = 12.dp)) { SheetTitle(if (isNew) "Nueva categoría" else "Editar categoría") }
    }
    OutlinedTextField(
      name, { name = it.take(30) }, Modifier.fillMaxWidth(), label = { Text("Nombre") }, singleLine = true, shape = RoundedCornerShape(22.dp),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Segmented(listOf("Gasto", "Ingreso"), if (kind == TxType.INCOME) 1 else 0, { kind = if (it == 1) TxType.INCOME else TxType.EXPENSE })
    Label("Color")
    ColorPicker(colorIdx, { colorIdx = it })
    Label("Icono")
    IconPicker(icon, tint, { icon = it })
    PrimaryButton(if (isNew) "Crear categoría" else "Guardar cambios", name.isNotBlank()) {
      onSave(initial.copy(name = name.trim(), kind = kind, icon = icon, colorIdx = colorIdx, sort = if (isNew) 999 else initial.sort))
      close()
    }
    if (!isNew) DeleteButton("Eliminar categoría") { confirmDelete = true }
    if (confirmDelete) {
      ConfirmDialog(
        "¿Eliminar ${initial.name}?", "Sus movimientos se conservan y quedan como \"Sin categoría\".",
        { confirmDelete = false; onDelete(initial); close() },
      ) { confirmDelete = false }
    }
  }
}
