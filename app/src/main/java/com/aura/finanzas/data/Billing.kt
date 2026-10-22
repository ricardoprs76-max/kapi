package com.aura.finanzas.data

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Kapi Premium: una suscripción (mensual y anual) y un pago único de por vida, vendidos con Google Play.
 * Los identificadores son los que hay que crear en Play Console → Monetizar → Productos.
 */
object PremiumProducts {
  /** Suscripción; sus planes básicos (mensual, anual) se leen de la tienda. */
  const val SUBSCRIPTION = "aura_premium"
  /** Producto único (no consumible): Premium para siempre. */
  const val LIFETIME = "aura_premium_vitalicio"
}

/** Una forma de comprar Premium tal como la devuelve la tienda, con el precio en la moneda de la persona. */
class PremiumOffer(val title: String, val price: String, val note: String, val details: ProductDetails, val offerToken: String?)

/**
 * Conexión con Google Play para vender Premium. [onEntitlement] recibe true o false solo cuando la tienda
 * respondió: sin conexión no se llama y la app conserva lo último que supo.
 */
class Billing(context: Context, private val onEntitlement: (Boolean) -> Unit) : PurchasesUpdatedListener {
  val offers = MutableStateFlow<List<PremiumOffer>>(emptyList())

  /** Mensaje para la pantalla de Premium: por qué no hay precios o cómo terminó una compra. */
  val message = MutableStateFlow<String?>(null)

  private val client = BillingClient.newBuilder(context.applicationContext).setListener(this)
    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
    .enableAutoServiceReconnection().build()

  fun connect() {
    if (client.isReady) { refresh(); return }
    client.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingResponseCode.OK) {
          loadOffers()
          refresh()
        } else {
          message.value = "Google Play no está disponible en este teléfono para compras (código ${result.responseCode})."
        }
      }

      override fun onBillingServiceDisconnected() {}
    })
  }

  private fun loadOffers() {
    val found = ArrayList<PremiumOffer>()
    var pending = 2
    fun done() {
      if (--pending > 0) return
      offers.value = found.toList()
      if (found.isEmpty() && !restoring) message.value = "Los planes aún no están disponibles: aparecen cuando la app esté publicada en Google Play con sus productos creados."
    }
    fun query(id: String, type: String, read: (ProductDetails) -> Unit) {
      val product = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()
      client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, response ->
        if (result.responseCode == BillingResponseCode.OK) synchronized(found) { response.productDetailsList.forEach(read) }
        done()
      }
    }
    query(PremiumProducts.SUBSCRIPTION, ProductType.SUBS) { d ->
      d.subscriptionOfferDetails?.forEach { o ->
        val phase = o.pricingPhases.pricingPhaseList.lastOrNull() ?: return@forEach
        val yearly = phase.billingPeriod.endsWith("Y")
        found += PremiumOffer(if (yearly) "Anual" else "Mensual", phase.formattedPrice, if (yearly) "al año · se renueva solo" else "al mes · se renueva solo", d, o.offerToken)
      }
    }
    query(PremiumProducts.LIFETIME, ProductType.INAPP) { d ->
      d.oneTimePurchaseOfferDetails?.let { found += PremiumOffer("De por vida", it.formattedPrice, "un solo pago", d, null) }
    }
  }

  /** True mientras la persona espera el resultado de «restaurar compras». */
  private var restoring = false

  /** «Ya lo compré: restaurar compras»: consulta Google Play y siempre dice cómo terminó. */
  fun restore() {
    restoring = true
    message.value = "Buscando tus compras en Google Play…"
    if (client.isReady) refresh() else client.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingResponseCode.OK) { loadOffers(); refresh() }
        else { restoring = false; message.value = "No pude conectar con Google Play (código ${result.responseCode}). Revisa que tengas sesión en Play Store e intenta de nuevo." }
      }
      override fun onBillingServiceDisconnected() {}
    })
  }

  /** Consulta las compras vigentes de esta cuenta de Google Play (también sirve para "restaurar compras"). */
  fun refresh() {
    if (!client.isReady) { connect(); return }
    var pending = 2
    var failed = false
    var owned = false
    fun done() {
      if (--pending > 0) return
      if (!failed) onEntitlement(owned)
      if (restoring) {
        restoring = false
        message.value = when {
          failed -> "Google Play no respondió. Intenta de nuevo en un momento."
          owned -> "¡Listo! Encontramos tu compra y Kapi Premium quedó activo."
          else -> "No encontramos compras de Kapi Premium en la cuenta de Google de Play Store de este teléfono."
        }
      }
    }
    for (type in listOf(ProductType.SUBS, ProductType.INAPP)) {
      client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, purchases ->
        synchronized(this) {
          if (result.responseCode != BillingResponseCode.OK) failed = true
          else if (purchases.any(::settle)) owned = true
          done()
        }
      }
    }
  }

  /** True si la compra está pagada; de paso la confirma ante Google (si no, se reembolsa sola a los 3 días). */
  private fun settle(p: Purchase): Boolean {
    if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return false
    if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
    return true
  }

  fun buy(activity: Activity, offer: PremiumOffer) {
    val product = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(offer.details)
    offer.offerToken?.let(product::setOfferToken)
    val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product.build())).build())
    if (result.responseCode != BillingResponseCode.OK) message.value = "No se pudo abrir la compra (código ${result.responseCode})."
  }

  override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
    when (result.responseCode) {
      BillingResponseCode.OK -> {
        if (purchases.orEmpty().any(::settle)) { onEntitlement(true); message.value = "¡Gracias! Kapi Premium quedó activo." }
        else message.value = "La compra quedó pendiente; se activa cuando Google confirme el pago."
      }
      BillingResponseCode.USER_CANCELED -> {}
      BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
      else -> message.update { "La compra no se completó (código ${result.responseCode})." }
    }
  }
}
