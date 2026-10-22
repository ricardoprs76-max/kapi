package com.aura.finanzas.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.aura.finanzas.CloudUi
import com.aura.finanzas.FinanceState
import com.aura.finanzas.data.AccountType
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.AuthUser
import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Settings
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.AdBanner
import com.aura.finanzas.ui.components.AuraBackground
import com.aura.finanzas.ui.components.bgStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.aura.finanzas.ui.components.GlassCard
import com.aura.finanzas.ui.components.rememberBanner
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphButton
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.enterStagger
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.paletteColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val syncFormat = DateTimeFormatter.ofPattern("d MMM 'a las' HH:mm", Locale.forLanguageTag("es-CO"))
private val headerFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-CO"))
private val screenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 60.dp)

@Composable
fun MovesScreen(state: FinanceState, hideAmounts: Boolean, accountFilter: Long?, onBack: () -> Unit, onEdit: (Tx) -> Unit, onDelete: (Tx) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  var toDelete by remember { mutableStateOf<Tx?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf("") }
  val groups = remember(state.txs, query, filter, accountFilter) {
    val q = query.trim().lowercase()
    state.txs.asSequence()
      .filter { accountFilter == null || it.accountId == accountFilter || it.toAccountId == accountFilter }
      .filter { filter.isEmpty() || it.type == filter }
      .filter { t ->
        q.isEmpty() || t.note.lowercase().contains(q) || t.tags.lowercase().contains(q.removePrefix("#")) ||
          state.categoryById[t.categoryId]?.name?.lowercase()?.contains(q) == true ||
          state.accountById[t.accountId]?.name?.lowercase()?.contains(q) == true ||
          t.amount.toLong().toString().contains(q)
      }
      .groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() }.toList()
  }
  val banner = rememberBanner(40)
  LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenTitle(accountFilter?.let { state.accountById[it]?.name } ?: "Movimientos", "Toca para editar · mantén presionado para eliminar", onBack = onBack) }
    item {
      OutlinedTextField(
        query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Buscar nota, categoría, cuenta, valor o #etiqueta") },
        singleLine = true, shape = RoundedCornerShape(24.dp),
      )
    }
    item {
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("" to "Todos", TxType.EXPENSE to "Gastos", TxType.INCOME to "Ingresos")) { (t, label) ->
          Pill(label, filter == t) { filter = t }
        }
      }
    }
    item { AdBanner(banner) }
    if (groups.isEmpty()) {
      item {
        if (state.txs.isEmpty()) EmptyCard("RECEIPT", "Aún no hay movimientos", "Toca el botón + para registrar el primero.", onClick = null)
        else EmptyCard("RECEIPT", "Sin resultados", "Ningún movimiento coincide con la búsqueda.", onClick = null)
      }
    }
    items(groups, key = { it.first.toEpochDay() }) { (day, txs) ->
      GlassCard(Modifier.fillMaxWidth().animateItem()) {
        Column(Modifier.padding(vertical = 8.dp)) {
          Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(dayLabel(day), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            val net = txs.sumOf { if (it.type == TxType.INCOME) it.amount else if (it.type == TxType.EXPENSE) -it.amount else 0.0 }
            if (!hideAmounts && net != 0.0) Text(money(net, sign = true), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
          }
          txs.forEach { tx ->
            TxRow(
              tx, state, hideAmounts,
              Modifier.combinedClickable(
                remember { MutableInteractionSource() }, ripple(),
                onLongClick = {
                  haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  toDelete = tx
                },
                onClick = { onEdit(tx) },
              ),
            )
          }
        }
      }
    }
  }
  toDelete?.let { tx ->
    AlertDialog(
      onDismissRequest = { toDelete = null },
      title = { Text("¿Eliminar movimiento?") },
      text = { Text("Se eliminará el movimiento de ${money(tx.amount)} y el saldo de la cuenta se recalculará.") },
      confirmButton = { TextButton({ onDelete(tx); toDelete = null }) { Text("Eliminar") } },
      dismissButton = { TextButton({ toDelete = null }) { Text("Cancelar") } },
    )
  }
}

private fun dayLabel(day: LocalDate): String = when (day) {
  LocalDate.now() -> "Hoy"
  LocalDate.now().minusDays(1) -> "Ayer"
  else -> day.format(headerFormat).replaceFirstChar { it.uppercase() }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
  val scheme = MaterialTheme.colorScheme
  Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    if (onBack != null) GlyphButton(Glyph.CHEVRON, "Volver", Modifier.padding(end = 12.dp), rotation = 180f, onClick = onBack)
    Column {
      Text(title, style = MaterialTheme.typography.headlineSmall, color = scheme.onSurface)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
  }
}

class MoreActions(
  val ai: () -> Unit,
  val assistant: () -> Unit,
  val lock: (Boolean) -> Unit,
  val exportCsv: () -> Unit,
  val backup: () -> Unit,
  val restore: () -> Unit,
  val blur: (Boolean) -> Unit,
  val hide: (Boolean) -> Unit,
  val back: () -> Unit,
  val pdf: () -> Unit,
  val reminders: (Boolean) -> Unit,
  val googleSync: () -> Unit,
  val premium: () -> Unit = {},
  val bgStyle: (Int) -> Unit = {},
  val signOut: () -> Unit = {},
  val signIn: () -> Unit = {},
  val dailyLog: (Boolean) -> Unit = {},
  val dailyTime: (Int, Int) -> Unit = { _, _ -> },
  val dailyTest: () -> Unit = {},
)

@Composable
private fun SettingRow(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    IsoIcon(icon, scheme.primary, size = 40.dp)
    Column(Modifier.padding(start = 12.dp).weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2)
    }
    GlyphIcon(Glyph.CHEVRON, scheme.onSurfaceVariant, size = 20.dp)
  }
}

@Composable
fun MoreScreen(
  state: FinanceState,
  settings: Settings,
  onPalette: (Int) -> Unit,
  onThemeMode: (Int) -> Unit,
  onAnimatedBg: (Boolean) -> Unit,
  onAddAccount: () -> Unit,
  onEditAccount: (Account) -> Unit,
  onCategories: () -> Unit,
  actions: MoreActions,
  cloud: CloudUi,
  user: AuthUser? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { ScreenTitle("Ajustes", onBack = actions.back) }
    // Una sola tarjeta: la cuenta con la que se entró y su copia en Google Drive (la misma cuenta, sin otra conexión).
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(0), level = 2) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IsoIcon("FAMILY", scheme.primary, size = 44.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
              Text(user?.name?.ifBlank { null } ?: "Tu cuenta", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1)
              Text(user?.email ?: settings.cloudEmail.ifBlank { "Sin sesión" }, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            if (user != null) Pill("Cerrar sesión", false, onClick = actions.signOut)
            else Pill("Entrar con Google", false, onClick = actions.signIn)
          }
          val status = when {
            user == null -> "Modo invitado: tus datos quedan solo en este teléfono. Entra con Google para guardarlos en la nube y compartir cuentas."
            cloud.busy -> "☁️ Guardando en la nube de Kapi…"
            cloud.message != null -> cloud.message
            settings.cloudLastSync > 0 -> "☁️ Guardado en la nube de Kapi · " + syncFormat.format(Instant.ofEpochMilli(settings.cloudLastSync).atZone(ZoneId.systemDefault()))
            else -> "☁️ Se guarda en la nube de Kapi con esta cuenta"
          }
          Text(status, style = MaterialTheme.typography.bodySmall, color = if (cloud.message != null) scheme.error else scheme.onSurfaceVariant)
          if (user != null) Pill("Sincronizar ahora", true, Modifier.fillMaxWidth(), onClick = actions.googleSync)
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(0), tint = if (settings.isPremium) null else scheme.tertiary, onClick = actions.premium) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          IsoIcon("STAR", scheme.primary, size = 44.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (settings.isPremium) "Kapi Premium activo ✨" else "Kapi Premium ✨", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(if (settings.isPremium) "Sin publicidad y con todos los fondos" else "Quita la publicidad y abre los fondos exclusivos", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          GlyphIcon(Glyph.CHEVRON, scheme.onSurfaceVariant)
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(0)) {
        Column(Modifier.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Fondo animado", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("Elige el que más te guste; toma los colores del tema.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(bgStyles.size) { i ->
              val style = bgStyles[i]
              val locked = style.premium && !settings.isPremium
              val chosen = settings.bgStyle == i
              val shape = RoundedCornerShape(18.dp)
              Box(
                Modifier.size(84.dp, 132.dp).clip(shape).border(if (chosen) 3.dp else 1.dp, if (chosen) scheme.primary else scheme.outlineVariant, shape)
                  .clickable { if (locked) actions.premium() else actions.bgStyle(i) },
              ) {
                AuraBackground(settings.animatedBackground, style = i)
                if (locked) Text("🔒", Modifier.align(Alignment.TopEnd).padding(6.dp), style = MaterialTheme.typography.labelLarge)
                if (chosen) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                  GlyphIcon(Glyph.CHECK, scheme.onPrimary, size = 14.dp)
                }
                Text(
                  style.name, Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(scheme.surface.copy(0.78f)).padding(vertical = 5.dp),
                  style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1, textAlign = TextAlign.Center,
                )
              }
            }
          }
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(0)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("Color", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          com.aura.finanzas.ui.components.ColorPicker(settings.palette, onPalette)
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(1)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("Modo", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
          com.aura.finanzas.ui.components.Segmented(listOf("Sistema", "Claro", "Oscuro", "AMOLED"), settings.themeMode.coerceIn(0, 3), onThemeMode)
          if (settings.themeMode == 3) {
            Text(
              "Negro puro: en pantallas AMOLED los píxeles negros se apagan y ahorras batería. Para ahorrar más, apaga también el movimiento del fondo.",
              style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Movimiento del fondo", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text("Desactívalo para ahorrar batería", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(settings.animatedBackground, onAnimatedBg)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Desenfoque en la barra", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text("Efecto de vidrio real; apágalo si la app se siente lenta", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(settings.blur, actions.blur)
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Ocultar cifras", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text("Muestra puntos en lugar de los valores", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(settings.hideAmounts, actions.hide)
          }
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(2), onClick = onCategories) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          IsoIcon("GIFT", scheme.primary, size = 44.dp)
          Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Categorías", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text("${state.categories.size} categorías · crea, edita y cambia iconos", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          GlyphIcon(Glyph.CHEVRON, scheme.onSurfaceVariant)
        }
      }
    }
    item {
      GlassCard(Modifier.fillMaxWidth().enterStagger(2)) {
        Column(Modifier.padding(vertical = 8.dp)) {
          SettingRow("BULB", "Inteligencia artificial", if (settings.aiKey.isBlank()) "Opcional: conecta Gemini o Claude con tu clave" else "${if (settings.aiProvider == com.aura.finanzas.data.AiProvider.CLAUDE) "Claude" else "Gemini"} · ${settings.aiModel}", actions.ai)
          Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IsoIcon("SAFE", scheme.primary, size = 40.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
              Text("Bloqueo con huella o PIN", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text("Pide desbloquear al abrir la app y tras 10 minutos sin usarla", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(settings.lockEnabled, actions.lock)
          }
          Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IsoIcon("PHONE", scheme.primary, size = 40.dp)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
              Text("Recordatorios de pagos", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
              Text("Notificación diaria con gastos fijos, cuotas y CDTs que vencen pronto", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Switch(settings.reminders, actions.reminders)
          }
          DailyLogRow(settings.dailyLog, settings.dailyHour, settings.dailyMinute, actions)
        }
      }
    }
  }
}

@Composable
fun CategoriesScreen(state: FinanceState, onBack: () -> Unit, onEdit: (Category) -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  LazyColumn(
    Modifier.fillMaxSize().statusBarsPadding(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item { ScreenTitle("Categorías", onBack = onBack) }
    listOf(TxType.EXPENSE to "De gasto", TxType.INCOME to "De ingreso").forEachIndexed { section, (kind, title) ->
      item { SectionTitle(title, "Nueva", { onEdit(Category(name = "", kind = kind)) }, Modifier.enterStagger(section * 2)) }
      item {
        GlassCard(Modifier.fillMaxWidth().enterStagger(section * 2 + 1)) {
          Column(Modifier.padding(vertical = 8.dp)) {
            state.categories.filter { it.kind == kind }.forEach { c ->
              Row(
                Modifier.fillMaxWidth().clickable { onEdit(c) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                IsoIcon(c.icon, paletteColor(c.colorIdx, dark), size = 40.dp)
                Text(c.name, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                GlyphIcon(Glyph.CHEVRON, scheme.onSurfaceVariant, size = 20.dp)
              }
            }
          }
        }
      }
    }
  }
}

/** Recordatorio diario de registros: interruptor, hora del aviso y un botón para probarlo. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DailyLogRow(on: Boolean, hour: Int, minute: Int, actions: MoreActions) {
  val scheme = MaterialTheme.colorScheme
  var picking by remember { mutableStateOf(false) }
  val time = java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale("es", "CO")))
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IsoIcon("RECEIPT", scheme.primary, size = 40.dp)
      Column(Modifier.padding(start = 12.dp).weight(1f)) {
        Text("Recordatorio diario de registros", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(
          if (on) "Si a las $time no has registrado nada ese día, te aviso para que anotes tus movimientos"
          else "Un aviso diario para no olvidar anotar tus gastos e ingresos",
          style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
        )
      }
      Switch(on, actions.dailyLog)
    }
    if (on) {
      Row(Modifier.padding(start = 52.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.FilledTonalButton({ picking = true }) { Text("Hora: $time") }
        androidx.compose.material3.TextButton(actions.dailyTest) { Text("Probar aviso") }
      }
    }
  }
  if (picking) {
    val state = androidx.compose.material3.rememberTimePickerState(hour, minute, is24Hour = false)
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { picking = false },
      title = { Text("Hora del recordatorio") },
      text = { androidx.compose.material3.TimePicker(state) },
      confirmButton = { androidx.compose.material3.TextButton({ picking = false; actions.dailyTime(state.hour, state.minute) }) { Text("Guardar") } },
      dismissButton = { androidx.compose.material3.TextButton({ picking = false }) { Text("Cancelar") } },
    )
  }
}
