package com.aura.finanzas.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.aura.finanzas.ui.components.AdBanner
import com.aura.finanzas.ui.components.rememberBanner
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aura.finanzas.FinanceState
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.ui.components.GlassCard
import com.aura.finanzas.ui.components.Glyph
import com.aura.finanzas.ui.components.GlyphIcon
import com.aura.finanzas.ui.components.IsoIcon
import com.aura.finanzas.ui.components.enterStagger
import com.aura.finanzas.ui.components.money
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.components.shortMoney
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import com.aura.finanzas.ui.theme.accountBrush
import com.aura.finanzas.ui.theme.paletteColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Espacio inferior para que el contenido no quede bajo la barra flotante. */
val BottomBarSpace = 164.dp
private val es: Locale = Locale.forLanguageTag("es-CO")
private val violet = Color(0xFF6D4BD8)

@Composable
fun HomeScreen(
  state: FinanceState,
  hideAmounts: Boolean,
  onSettings: () -> Unit,
  onAddAccount: () -> Unit,
  onSeeAll: () -> Unit,
  onOpenAccount: (Long, Rect) -> Unit,
  onEditAccount: (Account) -> Unit,
  onEditTx: (Tx) -> Unit,
  onAssistant: () -> Unit,
  onVoice: () -> Unit,
  onScan: () -> Unit,
  onReorder: (List<Long>) -> Unit,
  refreshing: Boolean,
  onRefresh: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val banner = rememberBanner()
  // Deslizar hacia abajo actualiza saldos y trae lo nuevo de la cuenta de Google y de las cuentas compartidas.
  PullToRefreshBox(refreshing, onRefresh, Modifier.fillMaxSize().statusBarsPadding()) {
  LazyColumn(
    Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = BottomBarSpace),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Box(Modifier.fillMaxWidth().height(56.dp).enterStagger(0)) {
        Text(
          "MIS FINANZAS", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold, color = scheme.onSurface,
        )
        Box(
          Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, ripple(), onClick = onSettings),
          contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.GEAR, scheme.onSurface, size = 24.dp) }
      }
    }

    item { AssistantBanner(Modifier.enterStagger(1), onAssistant) }

    item {
      Row(Modifier.fillMaxWidth().enterStagger(2), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCard(Glyph.MIC, "Dictar por Voz\n🎙️", Modifier.weight(1f), onVoice)
        ActionCard(Glyph.CAMERA, "Escanear Factura\no Ticket 📸", Modifier.weight(1f), onScan)
      }
    }

    item {
      Row(Modifier.fillMaxWidth().padding(top = 14.dp).enterStagger(3), verticalAlignment = Alignment.CenterVertically) {
        Text("Mis cuentas", style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, color = scheme.onSurface)
        Text(
          "(Arrastra para reordenar)", Modifier.padding(start = 6.dp).weight(1f), style = MaterialTheme.typography.bodySmall,
          color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(
          Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(remember { MutableInteractionSource() }, ripple(), onClick = onAddAccount).padding(horizontal = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          GlyphIcon(Glyph.PLUS, scheme.primary, size = 18.dp)
          Text("Añadir", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        }
      }
    }
    item {
      if (state.accounts.isEmpty()) {
        EmptyCard("WALLET", "Crea tu primera cuenta", "Cada cuenta lleva sus propios gastos e ingresos.", Modifier.enterStagger(4), onAddAccount)
      } else {
        AccountGrid(state, hideAmounts, Modifier.enterStagger(4), onOpenAccount, onEditAccount, onReorder)
      }
    }

    item { AdBanner(banner, Modifier.enterStagger(5)) }

  }
  }
}

/** Franja degradada del asistente; un brillo la recorre de lado a lado cada pocos segundos. */
@Composable
private fun AssistantBanner(modifier: Modifier, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  // Un brillo cruza la tarjeta y luego descansa unos segundos sin pedir cuadros. Se lee solo al dibujar, así que
  // la tarjeta no se recompone en cada fotograma.
  val shine = remember { Animatable(-0.4f) }
  LaunchedEffect(Unit) {
    while (true) {
      shine.snapTo(-0.4f)
      shine.animateTo(1.3f, tween(2400, easing = LinearEasing))
      kotlinx.coroutines.delay(2600)
    }
  }
  val shape = RoundedCornerShape(30.dp)
  Column(
    modifier.fillMaxWidth().pressScale(interaction).shadow(10.dp, shape, spotColor = violet).clip(shape)
      .background(Brush.linearGradient(listOf(Color(0xFF8F86EE), Color(0xFF6B3FC0), Color(0xFF4458C9))))
      .drawWithContent {
        drawContent()
        val x = size.width * shine.value
        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(0.20f), Color.Transparent), Offset(x - size.width * 0.25f, 0f), Offset(x, size.height)))
      }
      .clickable(interaction, ripple(), onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
  ) {
    Text("Asistente IA Inteligente ✨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    Text(
      "Habla o digitaliza tus consumos con el poder de la Inteligencia Artificial en tiempo real",
      Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f),
    )
  }
}

@Composable
private fun ActionCard(glyph: Glyph, label: String, modifier: Modifier, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  val tint = if (dark) Color(0xFFB9A8FF) else violet
  val shape = RoundedCornerShape(22.dp)
  GlassCard(modifier.border(1.5.dp, tint.copy(0.35f), shape), shape = shape, onClick = onClick) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(0.16f)), contentAlignment = Alignment.Center) { GlyphIcon(glyph, tint, size = 22.dp) }
      Text(label, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface, textAlign = TextAlign.Center)
    }
  }
}

/**
 * Rejilla de cuentas en 3 columnas. Al mantener presionada una tarjeta se puede arrastrar a otra casilla;
 * las demás se corren con un resorte y el orden queda guardado al soltar.
 */
@Composable
private fun AccountGrid(
  state: FinanceState, hide: Boolean, modifier: Modifier,
  onOpen: (Long, Rect) -> Unit, onEdit: (Account) -> Unit, onReorder: (List<Long>) -> Unit,
) {
  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  var order by remember(state.accounts.map { it.id }) { mutableStateOf(state.accounts.map { it.id }) }
  val currentOrder by rememberUpdatedState(order)
  var dragging by remember { mutableStateOf<Long?>(null) }
  var dragPos by remember { mutableStateOf(Offset.Zero) }
  BoxWithConstraints(modifier.fillMaxWidth()) {
    val gap = 10.dp
    val cellW = (maxWidth - gap * 2) / 3
    val cellH = 112.dp
    val rows = (order.size + 2) / 3
    val stepX = with(density) { (cellW + gap).toPx() }
    val stepY = with(density) { (cellH + gap).toPx() }
    Box(Modifier.fillMaxWidth().height(cellH * rows + gap * (rows - 1))) {
      order.forEachIndexed { index, id ->
        val account = state.accountById[id]
        if (account != null) key(id) {
          val x by animateDpAsState((cellW + gap) * (index % 3), Motion.spatial(), label = "gx")
          val y by animateDpAsState((cellH + gap) * (index / 3), Motion.spatial(), label = "gy")
          val lifted = dragging == id
          val lift by animateFloatAsState(if (lifted) 1f else 0f, Motion.spatialFast(), label = "lift")
          Box(
            Modifier.zIndex(if (lifted) 1f else 0f)
              .offset { if (lifted) IntOffset(dragPos.x.roundToInt(), dragPos.y.roundToInt()) else IntOffset(x.roundToPx(), y.roundToPx()) }
              .size(cellW, cellH)
              .graphicsLayer { val s = 1f + 0.08f * lift; scaleX = s; scaleY = s; rotationZ = 2.5f * lift }
              .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                  onDragStart = {
                    val i = currentOrder.indexOf(id)
                    dragPos = Offset(stepX * (i % 3), stepY * (i / 3))
                    dragging = id
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                  },
                  onDrag = { change, amount ->
                    change.consume()
                    dragPos += amount
                    // La casilla destino es la que contiene el centro de la tarjeta arrastrada.
                    val col = ((dragPos.x + stepX / 2) / stepX).toInt().coerceIn(0, 2)
                    val row = ((dragPos.y + stepY / 2) / stepY).toInt().coerceAtLeast(0)
                    val target = (row * 3 + col).coerceIn(0, currentOrder.lastIndex)
                    val from = currentOrder.indexOf(id)
                    if (target != from) {
                      order = currentOrder.toMutableList().apply { removeAt(from); add(target, id) }
                      haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                  },
                  onDragEnd = { dragging = null; onReorder(currentOrder) },
                  onDragCancel = { dragging = null },
                )
              },
          ) { AccountCard(account, state, hide, onOpen, onEdit) }
        }
      }
    }
  }
}

@Composable
private fun AccountCard(account: Account, state: FinanceState, hidden: Boolean, onOpen: (Long, Rect) -> Unit, onEdit: (Account) -> Unit) {
  val balance = state.balances[account.id] ?: 0.0
  var bounds by remember { mutableStateOf(Rect.Zero) }
  val interaction = remember { MutableInteractionSource() }
  val shape = RoundedCornerShape(24.dp)
  Box(
    Modifier.fillMaxSize().pressScale(interaction, to = 0.94f).onGloballyPositioned { bounds = it.boundsInRoot() }
      .shadow(8.dp, shape, spotColor = paletteColor(account.colorIdx, false)).clip(shape).background(accountBrush(account.colorIdx))
      .clickable(interaction, ripple()) { onOpen(account.id, bounds) },
  ) {
    // Reflejo suave en la esquina, para que la tarjeta no se vea plana.
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(0.28f), Color.Transparent), Offset(0f, 0f), 260f)))
    val name = account.name + if (account.shareId.isNotEmpty()) " 👥" else ""
    val longWord = account.name.split(' ').maxOf { it.length } > 9
    androidx.compose.foundation.text.BasicText(
      name, Modifier.padding(start = 12.dp, top = 10.dp, end = 34.dp),
      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 18.sp),
      maxLines = if (longWord) 1 else 2, overflow = TextOverflow.Ellipsis,
      autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.titleSmall.fontSize),
    )
    Box(
      Modifier.align(Alignment.TopEnd).size(40.dp).clip(CircleShape).clickable(remember { MutableInteractionSource() }, ripple()) { onEdit(account) },
      contentAlignment = Alignment.Center,
    ) { GlyphIcon(Glyph.EDIT, Color.White.copy(0.92f), size = 17.dp) }
    Column(Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 8.dp, bottom = 12.dp)) {
      val text = if (hidden) "$ ••••" else money(balance)
      Text(
        text, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1,
        // La letra se reduce con cifras largas para que el saldo siempre se vea completo.
        fontSize = when { text.length <= 10 -> 17.sp; text.length <= 12 -> 14.sp; text.length <= 14 -> 12.sp; else -> 10.sp },
      )
    }
  }
}

/** Título de cada pestaña: centrado, en mayúsculas y del mismo tamaño en todas. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
  val scheme = MaterialTheme.colorScheme
  Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = scheme.onSurface)
    subtitle?.let {
      Text(it, Modifier.padding(top = 2.dp, start = 12.dp, end = 12.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
  }
}

/** Texto de ayuda pequeño al lado o debajo de un título de sección. */
@Composable
fun SectionHint(text: String, modifier: Modifier = Modifier) =
  Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SectionTitle(title: String, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, color = scheme.onSurface)
    if (action != null) {
      Box(
        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).clickable(remember { MutableInteractionSource() }, ripple(), onClick = onAction)
          .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
      ) { Text(action, style = MaterialTheme.typography.labelLarge, color = scheme.primary) }
    }
  }
}

@Composable
fun EmptyCard(icon: String, title: String, body: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
  val scheme = MaterialTheme.colorScheme
  GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
      IsoIcon(icon, scheme.primary, size = 56.dp)
      Column(Modifier.padding(start = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
      }
    }
  }
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMM", es)

@Composable
fun TxRow(tx: Tx, state: FinanceState, hidden: Boolean, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val fin = LocalFin.current
  val category = tx.categoryId?.let { state.categoryById[it] }
  val account = state.accountById[tx.accountId]
  val transfer = tx.type == TxType.TRANSFER
  val title = when {
    transfer -> "Transferencia"
    tx.note.isNotBlank() -> tx.note
    else -> category?.name ?: "Sin categoría"
  }
  val subtitle = buildString {
    append(account?.name ?: "Cuenta eliminada")
    if (transfer) append(" → ").append(tx.toAccountId?.let { state.accountById[it]?.name } ?: "?")
    append(" · ").append(Instant.ofEpochMilli(tx.date).atZone(ZoneId.systemDefault()).format(dayFormat))
    if (!transfer && tx.note.isNotBlank()) append(" · ").append(category?.name ?: "Sin categoría")
    tx.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { append(" #").append(it) }
  }
  Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    IsoIcon(if (transfer) "CARD" else category?.icon ?: "COIN", paletteColor(category?.colorIdx ?: 2, fin.isDark), size = 42.dp)
    Column(Modifier.padding(start = 12.dp).weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val (text, color) = when (tx.type) {
      TxType.INCOME -> money(tx.amount, sign = true) to fin.income
      TxType.EXPENSE -> money(-tx.amount) to fin.expense
      else -> money(tx.amount) to scheme.onSurface
    }
    Text(if (hidden) "••••" else text, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.padding(start = 8.dp))
  }
}
