package com.aura.finanzas.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.finanzas.ui.theme.LocalFin
import com.aura.finanzas.ui.theme.Motion
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

class NavTab(val route: String, val label: String, val glyph: Glyph)

/** Barra inferior flotante en forma de píldora: la pestaña activa se resalta con una pastilla del color principal. */
@Composable
fun NavBar(tabs: List<NavTab>, current: String, onTab: (String) -> Unit, modifier: Modifier = Modifier, haze: HazeState? = null) {
  val scheme = MaterialTheme.colorScheme
  val dark = LocalFin.current.isDark
  val haptic = LocalHapticFeedback.current
  val barShape = RoundedCornerShape(32.dp)
  Row(
    modifier.shadow(if (dark) 0.dp else 20.dp, barShape, spotColor = scheme.primary.copy(0.30f))
      .clip(barShape)
      .then(
        // Con desenfoque real la barra deja ver, borroso, el contenido que pasa por debajo.
        if (haze != null) Modifier.hazeEffect(haze) {
          blurRadius = 24.dp
          // El contenido se desenfoca a menor resolución: se ve igual y cuesta mucho menos por fotograma.
          inputScale = HazeInputScale.Auto
          backgroundColor = scheme.surface
          tints = listOf(HazeTint(scheme.surfaceContainerLowest.copy(alpha = if (dark) 0.60f else 0.72f)))
        } else Modifier.background(scheme.surfaceContainerLowest.copy(alpha = if (dark) 0.92f else 0.96f)),
      )
      .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(if (dark) 0.22f else 0.95f), Color.White.copy(if (dark) 0.04f else 0.5f))), barShape)
      .fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    tabs.forEach { tab ->
      TabItem(tab, selected = tab.route == current, modifier = Modifier.weight(1f)) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onTab(tab.route)
      }
    }
  }
}

@Composable
private fun TabItem(tab: NavTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val p by animateFloatAsState(if (selected) 1f else 0f, Motion.spatialBouncy(), label = "tab")
  val color by animateColorAsState(if (selected) scheme.primary else scheme.onSurfaceVariant, Motion.effects(), label = "tabc")
  Column(
    modifier.height(56.dp).clip(RoundedCornerShape(22.dp))
      .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(Modifier.width(54.dp).height(28.dp), contentAlignment = Alignment.Center) {
      Box(
        Modifier.graphicsLayer { alpha = p.coerceIn(0f, 1f); scaleX = 0.4f + 0.6f * p; scaleY = 0.7f + 0.3f * p }
          .width(54.dp).height(28.dp).clip(CircleShape).background(scheme.primary.copy(0.13f)),
      )
      GlyphIcon(tab.glyph, color, Modifier.graphicsLayer { val s = 1f + 0.12f * p; scaleX = s; scaleY = s; translationY = -1.dp.toPx() * p }, size = 22.dp)
    }
    Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
  }
}

/** Botón flotante redondo "+": entra con un salto y se encoge al presionarlo. */
@Composable
fun Fab(description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val haptic = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val enter = remember { Animatable(0f) }
  LaunchedEffect(Unit) { enter.animateTo(1f, Motion.spatialBouncy()) }
  Box(
    modifier.graphicsLayer { scaleX = enter.value; scaleY = enter.value; rotationZ = (1f - enter.value) * -90f }
      .pressScale(interaction, to = 0.88f)
      .shadow(14.dp, CircleShape, spotColor = scheme.primary, ambientColor = scheme.primary)
      .size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(0.82f))))
      .semantics { contentDescription = description }
      .clickable(interaction, ripple(), role = Role.Button) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onClick()
      },
    contentAlignment = Alignment.Center,
  ) { GlyphIcon(Glyph.PLUS, scheme.onPrimary, size = 26.dp) }
}
