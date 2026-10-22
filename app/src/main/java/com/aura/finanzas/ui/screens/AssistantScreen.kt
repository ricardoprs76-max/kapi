package com.aura.finanzas.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.aura.finanzas.AiUi
import com.aura.finanzas.LocalBrain
import com.aura.finanzas.data.AiNote
import com.aura.finanzas.ui.components.AdBanner
import com.aura.finanzas.ui.components.rememberDictation
import com.aura.finanzas.ui.components.rememberBanner
import com.aura.finanzas.ui.components.shortMoney
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.aura.finanzas.FinanceState
import com.aura.finanzas.accountState
import com.aura.finanzas.antExpenses
import com.aura.finanzas.data.AiProvider
import com.aura.finanzas.data.Settings
import com.aura.finanzas.ui.components.GlassCard
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphButton
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.Pill
import com.aura.finanzas.ui.components.Segmented
import com.aura.finanzas.ui.components.enterStagger
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.paletteColor
import kotlin.math.sin

/** Lo que se ve de un vistazo arriba del chat; al tocarlo se le pregunta al asistente por ese tema. */
private class Insight(val emoji: String, val title: String, val value: String, val hint: String, val tone: Color, val ask: String, val intent: String?, val account: Long? = null)

/**
 * Tarjetas de arriba. Con «Cada cuenta» hay una tarjeta por cuenta (al tocarla se analiza esa cuenta); con una
 * cuenta elegida, todo se calcula solo con sus movimientos.
 */
@Composable
private fun insights(all: FinanceState, focus: Long?, hide: Boolean): List<Insight> {
  val fin = LocalFin.current
  val scheme = MaterialTheme.colorScheme
  fun v(x: Double) = if (hide) "••••" else if (kotlin.math.abs(x) >= 10_000_000) "$ " + shortMoney(x) else money(x)
  return remember(all, focus, hide, fin.isDark) {
    val out = ArrayList<Insight>()
    if (focus == null && all.accounts.size > 1) {
      all.accounts.forEach { a ->
        val one = accountState(all, a.id)
        val free = one.monthIncome - one.monthExpense
        out += Insight("💳", a.name, v(free), if (free >= 0) "le queda libre este mes" else "gasta más de lo que entra", if (free >= 0) fin.ok else fin.danger,
          "¿Cómo va la cuenta ${a.name} este mes y qué debería ajustar?", "ACCOUNTS", a.id)
      }
      if (all.assets.isNotEmpty() || all.debts.isNotEmpty()) out += Insight("🏦", "Patrimonio", v(all.netWorth), "activos − deudas", scheme.primary, "Analiza mi patrimonio", "WORTH")
      return@remember out
    }
    val state = focus?.let { accountState(all, it) } ?: all
    val free = state.monthIncome - state.monthExpense
    out += Insight("🧮", "Este mes", v(free), if (free >= 0) "te queda libre" else "gastas más de lo que entra", if (free >= 0) fin.ok else fin.danger, "¿Cómo voy este mes y qué debería ajustar?", "ACCOUNTS")
    state.monthByCategory.firstOrNull()?.let { top ->
      val share = if (state.monthExpense > 0) (top.amount / state.monthExpense * 100).toInt() else 0
      out += Insight("🔥", "Mayor gasto", top.category?.name ?: "Sin categoría", "${v(top.amount)} · $share % del mes", fin.expense, "¿Cómo puedo bajar mi gasto en ${top.category?.name ?: "mi mayor categoría"}?", null)
    }
    // La tarjeta resume los presupuestos de todas las cuentas juntas; los de cada cuenta se ven en Presupuesto.
    val global = state.budgets.filter { it.accountId == com.aura.finanzas.data.ALL_ACCOUNTS }
    val total = global.firstOrNull { it.isTotal && it.monthlyLimit > 0 }
    val limit = total?.monthlyLimit ?: global.filter { !it.isTotal }.sumOf { it.monthlyLimit }
    if (limit > 0) {
      val spent = if (total != null) state.monthExpense else global.filter { !it.isTotal }.sumOf { state.monthSpent[it.categoryId] ?: 0.0 }
      val pct = (spent / limit * 100).toInt()
      out += Insight("🎯", "Presupuesto", "$pct % usado", if (spent > limit) "te pasaste" else "quedan ${v(limit - spent)}", if (pct >= 100) fin.danger else if (pct >= 70) fin.warn else fin.ok, "¿Voy bien con mi presupuesto este mes?", "ACCOUNTS")
    }
    val ants = antExpenses(state)
    if (ants.isNotEmpty()) out += Insight("🐜", "Gastos hormiga", v(ants.sumOf { it.total }), "${ants.size} que se repiten", fin.warn, "Revisa mis gastos hormiga y dime cómo reducirlos", "ANTS")
    if (focus == null && (state.assets.isNotEmpty() || state.debts.isNotEmpty())) out += Insight("🏦", "Patrimonio", v(state.netWorth), "activos − deudas", scheme.primary, "Analiza mi patrimonio", "WORTH")
    out
  }
}

private class Quick(val label: String, val ask: String, val intent: String)

private val quicks = listOf(
  Quick("⚡ Analizar todo", "Analiza todas mis finanzas", "ALL"),
  Quick("💳 Mis cuentas", "Analiza los gastos de mis cuentas", "ACCOUNTS"),
  Quick("🏦 Patrimonio", "Analiza mi patrimonio", "WORTH"),
  Quick("🐜 Gastos hormiga", "Revisa mis gastos hormiga", "ANTS"),
  Quick("🧠 ¿Qué sabes de mí?", "¿Qué recuerdas de lo que te he contado?", "MEMORY"),
)

/**
 * Pestaña "Análisis IA": una conversación con el asistente. Se le pregunta (responde con las cifras registradas)
 * o se le cuentan cosas que no quedan en los registros; esas las guarda en su memoria y las usa después.
 */
@Composable
fun AnalysisScreen(
  state: FinanceState,
  settings: Settings,
  ai: AiUi,
  allNotes: List<AiNote>,
  chatSince: Long,
  hide: Boolean,
  onBack: () -> Unit,
  onConfigure: () -> Unit,
  onSend: (text: String, remember: Boolean?, intent: String?) -> Unit,
  onToggleMemo: (AiNote) -> Unit,
  onDelete: (AiNote) -> Unit,
  onNewChat: () -> Unit,
  onDeleteHistory: (List<AiNote>) -> Unit,
  onCancel: () -> Unit,
  onListening: () -> Unit,
  focusAccount: Long? = null,
  onFocus: (Long?) -> Unit = {},
) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val ready = settings.aiKey.isNotBlank()
  var text by rememberSaveable { mutableStateOf("") }
  // null = la app decide si es pregunta o algo para recordar; la persona puede fijarlo tocando la etiqueta.
  var forced by rememberSaveable { mutableStateOf<Boolean?>(null) }
  var memorySheet by remember { mutableStateOf(false) }
  var historySheet by remember { mutableStateOf(false) }
  // El chat muestra solo la conversación de esta vez que se abrió la app; las anteriores están en el historial.
  val notes = remember(allNotes, chatSince) { allNotes.filter { it.date >= chatSince } }
  val history = remember(allNotes, chatSince) { chatSessions(allNotes.filter { it.date < chatSince }) }
  var selected by remember { mutableStateOf<Long?>(null) }
  val list = rememberLazyListState()
  val focus = LocalFocusManager.current
  val memos = remember(allNotes) { allNotes.filter { it.memo && it.role == "USER" } }
  // Solo se escriben letra a letra las respuestas que llegan mientras la pantalla está abierta.
  val seenUpTo = remember { allNotes.maxOfOrNull { it.id } ?: 0L }
  val typed = remember { mutableSetOf<Long>() }
  // Si la cuenta elegida se borró, se vuelve a «Cada cuenta».
  val focused = focusAccount?.takeIf { id -> state.accounts.any { it.id == id } }
  val cards = insights(state, focused, hide)
  val willRemember = forced ?: (text.isNotBlank() && !LocalBrain.isQuestion(text))
  val banner = rememberBanner(36)

  val send = {
    if (text.isNotBlank() && ai.busy == null) {
      onSend(text, forced, null)
      text = ""; forced = null
    }
  }
  var voiceHint by remember { mutableStateOf<String?>(null) }
  var beforeVoice by remember { mutableStateOf("") }
  val dictation = rememberDictation { said -> text = (beforeVoice.trim() + " " + said).trim().take(600); voiceHint = null }
  // Lo que se va dictando aparece en el campo mientras se habla.
  LaunchedEffect(dictation.text, dictation.listening) {
    if (dictation.listening) text = (beforeVoice.trim() + " " + dictation.text).trim().take(600)
  }
  LaunchedEffect(dictation.error) { dictation.error?.let { voiceHint = it } }

  // Cuando llega una respuesta, la lista sube hasta la pregunta que la originó, para leerla desde el comienzo;
  // mientras se espera, baja hasta el final.
  val fixedItems = if (state.accounts.size > 1) 5 else 4
  LaunchedEffect(notes.size, ai.busy, ai.error) {
    if (notes.isEmpty()) return@LaunchedEffect
    val waiting = ai.busy != null || ai.error != null
    val target = if (waiting || notes.last().role != "AI") fixedItems + notes.size else fixedItems + (notes.size - 2).coerceAtLeast(0)
    list.animateScrollToItem(target)
  }

  val density = LocalDensity.current
  val ime = with(density) { WindowInsets.ime.getBottom(density).toDp() }
  val bars = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
  // Con el teclado abierto la barra de escribir va justo encima de él; cerrado, encima de la barra de pestañas.
  val bottom = maxOf(ime + 8.dp, bars + 86.dp)

  Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottom)) {
    Row(Modifier.padding(start = 10.dp, end = 12.dp).height(56.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, ripple(), onClick = onBack),
        contentAlignment = Alignment.Center,
      ) { GlyphIcon(Glyph.CHEVRON, scheme.onSurface, Modifier.graphicsLayer { rotationZ = 180f }, size = 26.dp) }
      GlyphIcon(Glyph.SPARK, scheme.primary, Modifier.padding(start = 2.dp), size = 26.dp)
      Column(Modifier.padding(start = 8.dp).weight(1f)) {
        Text("Asistente IA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1)
        Text(
          if (ready) "Con ${if (settings.aiProvider == AiProvider.CLAUDE) "Claude" else "Gemini"} · toca para cambiar" else "Modo local · toca para conectar una IA",
          Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onConfigure),
          style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
      }
      HeadChip("🧠 ${memos.size}") { memorySheet = true }
      if (history.isNotEmpty()) HeadChip("🕘") { historySheet = true }
      if (notes.isNotEmpty()) HeadChip("Nueva") { onNewChat() }
    }

    LazyColumn(
      Modifier.weight(1f).fillMaxWidth(), state = list,
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (state.accounts.size > 1) item(key = "focus") {
        // Qué se analiza: cada cuenta por separado (nunca sumadas) o solo una.
        LazyRow(Modifier.enterStagger(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item(key = "each") { Pill("Cada cuenta", focused == null) { onFocus(null) } }
          items(state.accounts, key = { it.id }) { a -> Pill(a.name, focused == a.id) { onFocus(a.id) } }
        }
      }
      item(key = "pulse") {
        LazyRow(Modifier.enterStagger(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          items(cards, key = { it.title + it.account }) { c ->
            InsightCard(c) {
              if (ai.busy == null) {
                c.account?.let(onFocus)
                onSend(c.ask, false, c.intent)
              }
            }
          }
        }
      }
      item(key = "quick") {
        LazyRow(Modifier.enterStagger(2), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(quicks, key = { it.intent }) { q -> Pill(q.label, false) { if (ai.busy == null) onSend(q.ask, false, q.intent) } }
        }
      }
      item(key = "ad") { AdBanner(banner) }
      item(key = "intro") {
        if (notes.isEmpty()) {
          GlassCard(Modifier.fillMaxWidth().enterStagger(3), level = 2, shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Pregúntame o cuéntame", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
              IntroLine("💬", "Pregunta lo que quieras", "\"¿Me alcanza para un viaje de 2 millones en diciembre?\" Te respondo con tus cifras reales.")
              IntroLine("📌", "Cuéntame lo que no anotas", "\"Le presté plata a mi hermano\", \"voy a cambiar de trabajo en enero\". Lo guardo en mi memoria, tenga o no valores.")
              IntroLine("🧠", "Luego pregúntame qué hacer", "Uso lo que me contaste, junto con tus cuentas y tu patrimonio, para aconsejarte.")
              Text(
                "Es orientación general y puede tener errores; no reemplaza a un asesor financiero." + if (ready) " Tus cifras y lo que me cuentas se envían al proveedor de IA que elegiste." else " Todo se calcula y se guarda en tu teléfono.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
              )
            }
          }
        }
      }
      items(notes, key = { it.id }) { n ->
        Bubble(
          n, selected == n.id, animate = n.role == "AI" && n.id > seenUpTo && n.id !in typed, onTyped = { typed += n.id },
          onClick = { selected = if (selected == n.id) null else n.id },
          onToggleMemo = { onToggleMemo(n) }, onDelete = { selected = null; onDelete(n) },
          Modifier.animateItem(),
        )
      }
      if (ai.busy != null) {
        item(key = "busy") {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
              Modifier.clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainerLowest.copy(0.85f)).padding(horizontal = 16.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              ThinkingDots(scheme.primary)
              Text(ai.busy, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            }
            TextButton(onCancel) { Text("Cancelar") }
          }
        }
      }
      ai.error?.let { msg ->
        item(key = "error") {
          GlassCard(Modifier.fillMaxWidth(), tint = fin.danger) { Text(msg, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface) }
        }
      }
    }

    // Barra para escribir: etiqueta de lo que pasará con el mensaje, campo, micrófono y enviar.
    Column(Modifier.padding(horizontal = 12.dp)) {
      if (text.isNotBlank() || voiceHint != null || dictation.listening) {
        Row(Modifier.padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          val tag = if (dictation.listening) "🎙️ Te escucho… toca el micrófono cuando termines" else voiceHint ?: if (willRemember) "📌 Lo guardaré en mi memoria · toca para que sea pregunta" else "💬 Te voy a responder · toca para solo guardarlo"
          Text(
            tag, Modifier.clip(RoundedCornerShape(12.dp)).background((if (willRemember) fin.warn else scheme.primary).copy(0.14f))
              .clickable { voiceHint = null; forced = !willRemember }.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, color = scheme.onSurface,
          )
        }
      }
      Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          text, { text = it.take(600); voiceHint = null }, Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4,
          textStyle = MaterialTheme.typography.bodyLarge,
          placeholder = { Text("Pregunta o cuéntame algo…", style = MaterialTheme.typography.bodyLarge, maxLines = 1) },
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = scheme.surfaceContainerLowest.copy(0.9f), focusedContainerColor = scheme.surfaceContainerLowest),
        )
        val listening = dictation.listening
        RoundAction(Glyph.MIC, if (listening) "Terminar dictado" else "Dictar", if (listening) fin.expense else scheme.onSurface.copy(0.08f), if (listening) Color.White else scheme.onSurface) {
          if (listening) dictation.stop() else { onListening(); beforeVoice = text; dictation.start() }
        }
        val can = text.isNotBlank() && ai.busy == null
        RoundAction(Glyph.ARROW_UP, "Enviar", if (can) scheme.primary else scheme.onSurface.copy(0.08f), if (can) scheme.onPrimary else scheme.onSurfaceVariant) { focus.clearFocus(); send() }
      }
    }
  }

  if (memorySheet) {
    MemorySheet(memos, onAdd = { onSend(it, true, null) }, onForget = onToggleMemo) { memorySheet = false }
  }
  if (historySheet) {
    HistorySheet(history, onDelete = onDeleteHistory) { historySheet = false }
  }
}

/** Conversaciones anteriores: mensajes seguidos sin una pausa de más de 30 minutos, de la más reciente a la más antigua. */
private fun chatSessions(old: List<AiNote>): List<List<AiNote>> {
  val out = ArrayList<MutableList<AiNote>>()
  for (n in old.sortedWith(compareBy({ it.date }, { it.id }))) {
    val last = out.lastOrNull()?.last()
    if (last == null || n.date - last.date > 30 * 60_000L) out += mutableListOf(n) else out.last() += n
  }
  return out.asReversed()
}

private val sessionDate = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM · h:mm a", java.util.Locale.forLanguageTag("es-CO"))

/** Historial de conversaciones: cada una se abre para leerla y se puede borrar (o todo el historial). */
@Composable
private fun HistorySheet(sessions: List<List<AiNote>>, onDelete: (List<AiNote>) -> Unit, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  var open by remember { mutableStateOf<Long?>(null) }
  var confirmAll by remember { mutableStateOf(false) }
  FormSheet(onDismiss) { _ ->
    SheetTitle("Historial de conversaciones 🕘")
    Text(
      "Cada vez que cierras la app la conversación se limpia y queda guardada aquí. Lo que está en la memoria 🧠 no se pierde al borrar.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    if (sessions.isEmpty()) {
      Text("Aún no hay conversaciones anteriores.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
    sessions.forEach { session ->
      val key = session.first().id
      val expanded = open == key
      val first = session.firstOrNull { it.role == "USER" }?.text ?: session.first().text
      Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(0.05f))
          .clickable { open = if (expanded) null else key }.padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Text(
          sessionDate.format(Instant.ofEpochMilli(session.first().date).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() },
          style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
        )
        Text(first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = if (expanded) 6 else 2, overflow = TextOverflow.Ellipsis)
        Text(
          "${session.size} ${if (session.size == 1) "mensaje" else "mensajes"} · ${if (expanded) "toca para cerrar" else "toca para leer"}",
          style = MaterialTheme.typography.labelSmall, color = scheme.primary,
        )
        if (expanded) {
          Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            session.forEach { n ->
              val mine = n.role == "USER"
              Text(
                n.text,
                Modifier.fillMaxWidth(if (mine) 0.9f else 1f).align(if (mine) Alignment.End else Alignment.Start)
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (mine) scheme.primary.copy(0.14f) else scheme.surfaceContainerLowest.copy(if (fin.isDark) 0.72f else 0.9f))
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface,
              )
            }
            TextButton({ open = null; onDelete(session) }, Modifier.align(Alignment.End)) { Text("Borrar esta conversación", color = scheme.error) }
          }
        }
      }
    }
    if (sessions.isNotEmpty()) {
      TextButton({ confirmAll = true }) { Text("Borrar todo el historial", color = scheme.error) }
    }
  }
  if (confirmAll) {
    AlertDialog(
      onDismissRequest = { confirmAll = false },
      title = { Text("¿Borrar todo el historial?") },
      text = { Text("Se borran las conversaciones anteriores. Lo que está guardado en la memoria del asistente se conserva.") },
      confirmButton = { TextButton({ confirmAll = false; onDelete(sessions.flatten()) }) { Text("Borrar") } },
      dismissButton = { TextButton({ confirmAll = false }) { Text("Cancelar") } },
    )
  }
}

@Composable
private fun HeadChip(label: String, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Box(
    Modifier.padding(start = 6.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(scheme.onSurface.copy(0.08f))
      .clickable(remember { MutableInteractionSource() }, ripple(), onClick = onClick).padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) { Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1) }
}

@Composable
private fun RoundAction(glyph: Glyph, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    Modifier.padding(bottom = 4.dp).size(48.dp).pressScale(interaction, to = 0.9f).clip(CircleShape).background(bg)
      .clickable(interaction, ripple(), onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { GlyphIcon(glyph, fg, size = 22.dp) }
}

@Composable
private fun InsightCard(c: Insight, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  GlassCard(Modifier.width(176.dp), shape = RoundedCornerShape(20.dp), tint = c.tone, level = 2, onClick = onClick) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(c.emoji, style = MaterialTheme.typography.titleMedium)
        Text(c.title, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
      }
      Text(c.value, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(c.hint, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("Preguntar ›", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
  }
}

@Composable
private fun IntroLine(emoji: String, title: String, body: String) {
  val scheme = MaterialTheme.colorScheme
  Row {
    Text(emoji, Modifier.padding(end = 10.dp), style = MaterialTheme.typography.titleMedium)
    Text(
      buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.onSurface)) { append("$title. ") }
        append(body)
      },
      style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
    )
  }
}

/** Mensaje del chat. Al tocarlo aparecen sus acciones: guardarlo en la memoria (o sacarlo) y borrarlo. */
@Composable
private fun Bubble(
  n: AiNote, selected: Boolean, animate: Boolean, onTyped: () -> Unit,
  onClick: () -> Unit, onToggleMemo: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val mine = n.role == "USER"
  val appear = remember(n.id) { Animatable(if (animate) 0f else 1f) }
  LaunchedEffect(n.id) {
    if (animate) {
      appear.animateTo(1f, tween(650))
      onTyped()
    }
  }
  Column(
    modifier.fillMaxWidth().graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 18.dp.toPx() },
    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
  ) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 6.dp, bottomEnd = if (mine) 6.dp else 20.dp)
    val bg = when {
      !mine -> scheme.surfaceContainerLowest.copy(if (fin.isDark) 0.72f else 0.9f)
      n.memo -> fin.warn.copy(0.22f)
      else -> scheme.primary
    }
    Column(
      Modifier.fillMaxWidth(if (mine) 0.86f else 0.94f).wrapContentWidth(if (mine) Alignment.End else Alignment.Start)
        .clip(shape).background(bg).then(if (mine && n.memo) Modifier.border(1.dp, fin.warn.copy(0.6f), shape) else Modifier)
        .clickable(remember { MutableInteractionSource() }, null, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      if (mine && n.memo) Text("📌 En mi memoria · ${noteDate.format(Instant.ofEpochMilli(n.date).atZone(ZoneId.systemDefault()))}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
      Text(
        n.text, style = MaterialTheme.typography.bodyLarge,
        color = if (mine && !n.memo) scheme.onPrimary else scheme.onSurface,
      )
    }
    if (selected) {
      Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mine) TextButton(onToggleMemo) { Text(if (n.memo) "Quitar de la memoria" else "📌 Guardar en la memoria") }
        TextButton(onDelete) { Text("Borrar", color = scheme.error) }
      }
    }
  }
}

private val noteDate = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.forLanguageTag("es-CO"))

/** Lo que el asistente tiene guardado: se puede agregar algo directamente u olvidar lo que ya no aplica. */
@Composable
private fun MemorySheet(memos: List<AiNote>, onAdd: (String) -> Unit, onForget: (AiNote) -> Unit, onDismiss: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  var draft by rememberSaveable { mutableStateOf("") }
  FormSheet(onDismiss) { _ ->
    SheetTitle("Memoria del asistente 🧠")
    Text(
      "Lo que le has contado y no está en tus registros: préstamos a familiares, planes, acuerdos, ingresos que esperas… Lo tiene presente cada vez que le pides un consejo.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    OutlinedTextField(
      draft, { draft = it.take(600) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), minLines = 2,
      placeholder = { Text("Ej.: Mi hermano me debe 800 mil y me paga en marzo") },
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    PrimaryButton("Guardar en la memoria", draft.isNotBlank()) { onAdd(draft.trim()); draft = "" }
    if (memos.isEmpty()) {
      Text("Aún no hay nada guardado.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    } else {
      Label("Guardado (${memos.size})")
      memos.asReversed().forEach { m ->
        Row(
          Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(0.05f)).padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(m.text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
            Text(noteDate.format(Instant.ofEpochMilli(m.date).atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
          }
          TextButton({ onForget(m) }) { Text("Olvidar") }
        }
      }
    }
  }
}

/** Tres puntos que suben y bajan en ola mientras se espera una respuesta. */
@Composable
fun ThinkingDots(color: Color) {
  val t by rememberInfiniteTransition(label = "puntos").animateFloat(0f, 6.283f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "fase")
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(3) { i ->
      Box(
        Modifier.size(10.dp).graphicsLayer {
          val wave = sin(t - i * 0.9f)
          translationY = -5.dp.toPx() * wave
          alpha = 0.55f + 0.45f * ((wave + 1f) / 2f)
        }.clip(CircleShape).background(color),
      )
    }
  }
}

/** Proveedor, clave y modelo de IA. Cada cambio se guarda al instante. */
@Composable
fun AiSheet(
  settings: Settings,
  ai: AiUi,
  onProvider: (String) -> Unit,
  onKey: (provider: String, key: String) -> Unit,
  onModel: (provider: String, model: String) -> Unit,
  onTest: () -> Unit,
  onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val claude = settings.aiProvider == AiProvider.CLAUDE
  // El texto vive aquí y se escribe a los ajustes; se recarga solo al cambiar de proveedor.
  var key by remember(settings.aiProvider) { mutableStateOf(settings.aiKey) }
  var model by remember(settings.aiProvider) { mutableStateOf(settings.aiModel) }
  var show by remember { mutableStateOf(false) }

  FormSheet(onDismiss) { _ ->
    SheetTitle("Inteligencia artificial")
    Text(
      "Opcional: la app ya analiza, lee recibos y entiende frases en tu teléfono. Con tu propia clave de API las respuestas son más completas. " +
        "La clave se guarda solo en este teléfono y no se incluye en los respaldos.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    Segmented(listOf("Gemini", "Claude"), if (claude) 1 else 0, { onProvider(if (it == 1) AiProvider.CLAUDE else AiProvider.GEMINI) })
    OutlinedTextField(
      key, { key = it.trim(); onKey(settings.aiProvider, key) }, Modifier.fillMaxWidth(),
      label = { Text(if (claude) "Clave de Anthropic" else "Clave de Gemini") }, singleLine = true, shape = RoundedCornerShape(22.dp),
      visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      trailingIcon = { TextButton({ show = !show }) { Text(if (show) "Ocultar" else "Ver") } },
    )
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Pill(if (claude) "Abrir Anthropic" else "Crear mi clave gratis", true, Modifier.weight(1f)) {
        val url = if (claude) "https://console.anthropic.com/settings/keys" else "https://aistudio.google.com/apikey"
        try {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
          // Sin navegador instalado no hay nada que abrir; la dirección queda escrita abajo.
        }
      }
      Pill("Pegar clave", false, Modifier.weight(1f)) {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
        if (clip.isNotEmpty()) { key = clip.take(200); onKey(settings.aiProvider, key) }
      }
    }
    val keyUrl = if (claude) "https://console.anthropic.com/settings/keys" else "https://aistudio.google.com/apikey"
    Text(
      if (claude) "1. Toca \"Abrir Anthropic\" o el enlace de abajo y crea una clave en API keys. 2. Cópiala. 3. Vuelve aquí y toca \"Pegar clave\"."
      else "1. Toca \"Crear mi clave gratis\" o el enlace de abajo (entra con tu cuenta de Google). 2. Toca \"Crear clave de API\" y cópiala. 3. Vuelve aquí y toca \"Pegar clave\".",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    Text(
      "🔗 $keyUrl", style = MaterialTheme.typography.bodyMedium, color = scheme.primary, textDecoration = TextDecoration.Underline,
      modifier = Modifier.clickable {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(keyUrl))) } catch (e: ActivityNotFoundException) { }
      },
    )
    OutlinedTextField(
      model, { model = it.trim(); onModel(settings.aiProvider, model) }, Modifier.fillMaxWidth(),
      label = { Text("Modelo") }, singleLine = true, shape = RoundedCornerShape(22.dp),
    )
    Text(
      if (claude) "Por defecto ${AiProvider.DEFAULT_CLAUDE_MODEL} (el más capaz). Para gastar menos por consulta puedes escribir claude-haiku-5-5."
      else "Por defecto ${AiProvider.DEFAULT_GEMINI_MODEL}. Puedes escribir otro modelo de Gemini que acepte imágenes.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
    )
    PrimaryButton("Probar conexión", key.isNotBlank() && ai.busy == null, onClick = onTest)
    ai.busy?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
    ai.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = scheme.error) }
    if (ai.test.isNotBlank() && ai.error == null) Text(ai.test, style = MaterialTheme.typography.bodyMedium, color = LocalFin.current.ok)
  }
}

/**
 * Dictar un gasto o escribirlo. Al dejar de hablar (o tocar "Listo") lo dictado se interpreta enseguida, sin
 * otro toque; lo escrito a mano se interpreta con "Interpretar".
 */
@Composable
fun VoiceDialog(onInterpret: (String) -> Unit, onDismiss: () -> Unit) {
  var text by rememberSaveable { mutableStateOf("") }
  var hint by remember { mutableStateOf<String?>(null) }
  val dictation = rememberDictation(quick = true) { said ->
    val clean = said.trim().take(300)
    if (clean.isNotEmpty()) { onInterpret(clean); onDismiss() } else text = clean
  }
  LaunchedEffect(dictation.text) { if (dictation.listening) text = dictation.text.take(300) }
  LaunchedEffect(dictation.error) { dictation.error?.let { hint = it } }
  LaunchedEffect(Unit) { dictation.start() }
  val pulse by rememberInfiniteTransition(label = "mic").animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulso")
  AlertDialog(
    onDismissRequest = { dictation.cancel(); onDismiss() },
    title = { Text(if (dictation.listening) "Te escucho… 🎙️" else "Dicta o escribe") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            text, { text = it.take(300) }, Modifier.weight(1f), shape = RoundedCornerShape(20.dp), minLines = 2,
            placeholder = { Text("Almuerzo 25 mil") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
          )
          Box(Modifier.graphicsLayer { val s = if (dictation.listening) pulse else 1f; scaleX = s; scaleY = s }) {
            GlyphButton(Glyph.MIC, if (dictation.listening) "Terminar" else "Dictar") { if (dictation.listening) dictation.stop() else dictation.start() }
          }
        }
        Text(
          hint ?: if (dictation.listening) "Di, por ejemplo: \"almuerzo 25 mil\". Al terminar de hablar lo registro enseguida." else "Se convierte en un movimiento y lo revisas antes de registrar.",
          style = MaterialTheme.typography.bodySmall,
        )
      }
    },
    confirmButton = {
      if (dictation.listening) TextButton({ dictation.stop() }) { Text("Listo") }
      else TextButton({ onInterpret(text.trim()); onDismiss() }, enabled = text.isNotBlank()) { Text("Interpretar") }
    },
    dismissButton = { TextButton({ dictation.cancel(); onDismiss() }) { Text("Cancelar") } },
  )
}

/** Elegir de dónde sale la foto del recibo: cámara o galería. */
@Composable
fun ReceiptDialog(hint: String?, onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Escanear factura o ticket") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(hint ?: "Leo cada producto con su valor, o el total si la factura no trae detalle. Revisas todo antes de registrar.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Pill("Cámara", true, Modifier.weight(1f), leading = { GlyphIcon(Glyph.CAMERA, MaterialTheme.colorScheme.onPrimary, size = 20.dp) }, onClick = onCamera)
          Pill("Galería", false, Modifier.weight(1f), onClick = onGallery)
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
  )
}

/** Espera de una lectura o interpretación, con opción de cancelarla. */
@Composable
fun BusyDialog(label: String, onCancel: () -> Unit) {
  AlertDialog(
    onDismissRequest = {},
    text = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ThinkingDots(MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.titleMedium)
      }
    },
    confirmButton = { TextButton(onCancel) { Text("Cancelar") } },
  )
}
