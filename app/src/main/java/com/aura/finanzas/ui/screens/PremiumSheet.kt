package com.aura.finanzas.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.finanzas.BuildConfig
import com.aura.finanzas.data.PremiumOffer
import com.aura.finanzas.data.Settings
import com.aura.finanzas.ui.components.pressScale
import com.aura.finanzas.ui.theme.LocalFin

/**
 * Kapi Premium: qué incluye y los planes con el precio que devuelve Google Play. La app completa es gratis con
 * publicidad; Premium quita los anuncios y abre los fondos animados extra.
 */
@Composable
fun PremiumSheet(
  settings: Settings,
  offers: List<PremiumOffer>,
  message: String?,
  onOpen: () -> Unit,
  onBuy: (Activity, PremiumOffer) -> Unit,
  onRestore: () -> Unit,
  onDevPremium: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val activity = LocalContext.current as? Activity
  LaunchedEffect(Unit) { onOpen() }
  FormSheet(onDismiss) { _ ->
    SheetTitle(if (settings.isPremium) "Kapi Premium activo ✨" else "Kapi Premium ✨")
    Text(
      if (settings.isPremium) "Gracias por apoyar la app. Ya tienes todo lo de Premium."
      else "Todas las funciones de la app son gratis, con publicidad. Premium es para quien quiere la app limpia y con más estilo.",
      style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
    )
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
      Benefit("🚫", "Sin publicidad", "Desaparecen todos los anuncios de la app.")
      Benefit("🎨", "Fondos animados exclusivos", "Aurora, Ondas, Constelación y Burbujas, además de Aura y Bolitas.")
      Benefit("💜", "Apoyas el desarrollo", "Cada mejora nueva de Premium te llega sin pagar más.")
    }
    if (!settings.isPremium) {
      offers.forEach { o ->
        val interaction = remember { MutableInteractionSource() }
        val shape = RoundedCornerShape(18.dp)
        val best = o.title == "Anual"
        Row(
          Modifier.fillMaxWidth().pressScale(interaction).clip(shape).background(if (best) scheme.primary.copy(0.12f) else scheme.onSurface.copy(0.04f))
            .border(if (best) 2.dp else 1.dp, if (best) scheme.primary else scheme.outlineVariant, shape)
            .clickable(interaction, ripple(), enabled = activity != null) { activity?.let { onBuy(it, o) } }.padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(Modifier.weight(1f)) {
            Text(o.title + if (best) " · el más conveniente" else "", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(o.note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
          }
          Text(o.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.primary)
        }
      }
      (message ?: if (offers.isEmpty()) "Los planes y sus precios aparecen aquí cuando la app esté publicada en Google Play." else null)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
      }
      TextButton(onRestore, Modifier.fillMaxWidth()) { Text("Ya lo compré: restaurar compras") }
      Text(
        "El pago y la renovación los maneja Google Play con tu cuenta de Google; puedes cancelar la suscripción cuando quieras desde Play Store → Pagos y suscripciones.",
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
      )
    } else {
      message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalFin.current.ok) }
    }
    // Solo en las instalaciones de prueba (APK sin publicar): permite ver la app como la ve alguien con Premium.
    if (BuildConfig.DEBUG) {
      Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.onSurface.copy(0.05f)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text("Activar Premium en este teléfono", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
          Text("Solo aparece en la versión de prueba, para que uses todo sin comprar.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Switch(settings.devPremium, onDevPremium)
      }
    }
  }
}

@Composable
private fun Benefit(emoji: String, title: String, body: String) {
  val scheme = MaterialTheme.colorScheme
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(emoji, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
    Column {
      Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
      Text(body, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
  }
}
