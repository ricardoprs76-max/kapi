package com.aura.finanzas.ui.components

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Publicidad (AdMob). El identificador de la app va en el AndroidManifest y el del bloque de anuncios aquí.
 * En este repositorio son los identificadores de PRUEBA de Google; cámbialos por los de tu cuenta de AdMob.
 */
object Ads {
  const val BANNER_UNIT = "ca-app-pub-3940256099942544/6300978111"
}

/** True cuando la persona tiene Kapi Premium: no se pide ni se muestra publicidad. */
val LocalPremium = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Un banner ya pedido a AdMob. Se crea una sola vez por pantalla para no recargarlo al desplazar la lista. */
class BannerState(val view: AdView) {
  var loaded by mutableStateOf(false)
}

/** Banner adaptable al ancho disponible ([horizontalMargin] es el margen total a los lados). Null con Premium. */
@Composable
fun rememberBanner(horizontalMargin: Int = 32): BannerState? {
  if (LocalPremium.current) return null
  val context = LocalContext.current
  val width = LocalConfiguration.current.screenWidthDp - horizontalMargin
  val state = remember(width) {
    val view = AdView(context)
    BannerState(view).also { s ->
      view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
      view.adUnitId = Ads.BANNER_UNIT
      view.adListener = object : AdListener() {
        override fun onAdLoaded() { s.loaded = true }
      }
    }
  }
  // El anuncio se pide cuando la pantalla ya está dibujada, no durante la composición.
  LaunchedEffect(state) { state.view.loadAd(AdRequest.Builder().build()) }
  DisposableEffect(state) { onDispose { state.view.destroy() } }
  return state
}

/** Muestra el banner. Mientras no haya anuncio (sin internet, por ejemplo) no ocupa espacio. */
@Composable
fun AdBanner(state: BannerState?, modifier: Modifier = Modifier) {
  if (state == null) return
  AndroidView(
    factory = { state.view.also { (it.parent as? ViewGroup)?.removeView(it) } },
    modifier = if (state.loaded) modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)) else Modifier.height(0.dp),
  )
}
