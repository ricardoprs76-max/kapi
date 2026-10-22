package com.aura.finanzas.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.finanzas.data.Auth
import com.aura.finanzas.data.AuthException
import com.aura.finanzas.ui.components.IsoIcon
import kotlinx.coroutines.launch

/**
 * Entrada a la app: un solo botón que abre la pantalla de Google. Con la misma acción una cuenta nueva queda
 * registrada y una que ya existía inicia sesión. [onGuest] entra sin correo, con los datos solo en el teléfono.
 */
@Composable
fun LoginScreen(onGuest: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }

  Column(
    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
  ) {
    IsoIcon("WALLET", scheme.primary, size = 120.dp)
    Text("Kapi", style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface, modifier = Modifier.padding(top = 24.dp))
    Text(
      "Entra con tu cuenta de Google para empezar. Si es tu primera vez, quedas registrado con ese mismo correo.",
      style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
    )
    Row(
      Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)
        .border(1.dp, Color(0xFF747775), RoundedCornerShape(24.dp))
        .clickable(remember { MutableInteractionSource() }, ripple(), enabled = !busy && Auth.configured) {
          busy = true
          error = null
          scope.launch {
            try {
              Auth.signIn(context)
            } catch (e: AuthException) {
              error = e.message
            } finally {
              busy = false
            }
          }
        },
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
      if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
      else Text("G", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
      Box(Modifier.size(12.dp))
      Text(if (busy) "Conectando con Google…" else "Continuar con Google", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
    }
    if (!Auth.configured) {
      Text(
        "Esta instalación todavía no está conectada con Firebase, así que el ingreso con Google no funciona aún.",
        style = MaterialTheme.typography.bodySmall, color = scheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp),
      )
    }
    Row(
      Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp).clip(RoundedCornerShape(24.dp))
        .border(1.dp, scheme.outline, RoundedCornerShape(24.dp))
        .clickable(remember { MutableInteractionSource() }, ripple(), enabled = !busy, onClick = onGuest),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
      Text("👤  Entrar como invitado", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
    }
    Text(
      "Como invitado no necesitas correo: tus datos quedan solo en este teléfono (sin nube ni cuentas compartidas). Puedes entrar con Google cuando quieras desde Ajustes.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
    )
    error?.let {
      Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
    }
    Text(
      "La app nunca ve tu contraseña: el ingreso lo hace Google y solo recibe tu nombre y tu correo.",
      style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp),
    )
  }
}
