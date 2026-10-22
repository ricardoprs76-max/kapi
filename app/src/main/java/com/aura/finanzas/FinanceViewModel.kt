package com.aura.finanzas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import com.aura.finanzas.data.Account
import com.aura.finanzas.data.AiNote
import com.aura.finanzas.data.Billing
import com.aura.finanzas.data.PremiumOffer
import com.aura.finanzas.data.SharedPerson
import com.aura.finanzas.data.Ai
import com.aura.finanzas.data.AiException
import com.aura.finanzas.data.AppDb
import com.aura.finanzas.data.CloudException
import com.aura.finanzas.data.FireSync
import com.aura.finanzas.data.DataTools
import com.aura.finanzas.data.Ocr
import com.aura.finanzas.data.PdfReport
import com.aura.finanzas.data.Keys
import com.aura.finanzas.data.loadJpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.aura.finanzas.data.Asset
import com.aura.finanzas.data.Budget
import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Debt
import com.aura.finanzas.data.FixedExpense
import com.aura.finanzas.data.Goal
import com.aura.finanzas.data.Prefs
import com.aura.finanzas.data.Settings
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import com.aura.finanzas.data.defaultCategories
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.pow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategorySpend(val category: Category?, val amount: Double)

private const val DAY_MS = 86_400_000.0

/** Valor de hoy. Un CDT vale su capital invertido: no se muestra lo que va rentando, solo el aviso de su vencimiento. */
@Suppress("UNUSED_PARAMETER")
fun Asset.currentValue(now: Long = System.currentTimeMillis()): Double {
  if (kind == "ACCIONES") return (units ?: return value) * (sellPrice ?: return value)
  return value
}

/** Rentabilidad de unas acciones en porcentaje: (precio de venta − precio de compra) / precio de compra. Null si no aplica. */
fun Asset.returnPct(): Double? {
  val buy = buyPrice?.takeIf { it > 0 } ?: return null
  val sell = sellPrice ?: return null
  return (sell - buy) / buy * 100
}

/** Ganancia (o pérdida, negativa) de unas acciones en pesos. */
fun Asset.gain(): Double? = if (buyPrice == null || sellPrice == null || units == null) null else (sellPrice - buyPrice) * units

/** Valor del CDT al vencimiento, o null si no aplica. */
fun Asset.maturityValue(): Double? {
  val rate = ratePct ?: return null
  val start = startDate ?: return null
  val end = endDate ?: return null
  if (kind != "CDT") return null
  return value * (1 + rate / 100).pow(((end - start) / DAY_MS) / 365.0)
}

data class FinanceState(
  val accounts: List<Account> = emptyList(),
  val categories: List<Category> = emptyList(),
  val txs: List<Tx> = emptyList(),
  val balances: Map<Long, Double> = emptyMap(),
  val liquidity: Double = 0.0,
  val monthIncome: Double = 0.0,
  val monthExpense: Double = 0.0,
  val monthByCategory: List<CategorySpend> = emptyList(),
  val assets: List<Asset> = emptyList(),
  val debts: List<Debt> = emptyList(),
  val budgets: List<Budget> = emptyList(),
  val fixed: List<FixedExpense> = emptyList(),
  val goals: List<Goal> = emptyList(),
  /** Gasto del mes por id de categoría. */
  val monthSpent: Map<Long?, Double> = emptyMap(),
  val accountsTotal: Double = 0.0,
  val assetsTotal: Double = 0.0,
  val debtsTotal: Double = 0.0,
  /** Promedio mensual de cada cuenta en los últimos 3 meses. */
  val avg3: Map<Long, Avg3> = emptyMap(),
  /** Gasto del mes en curso por cuenta. */
  val accountMonthSpent: Map<Long, Double> = emptyMap(),
) {
  val categoryById by lazy { categories.associateBy { it.id } }
  val accountById by lazy { accounts.associateBy { it.id } }

  /** Activos menos deudas. Las cuentas llevan su propio saldo y no entran aquí. */
  val netWorth get() = assetsTotal - debtsTotal
}

/**
 * Promedio mensual de gastos e ingresos de una cuenta en los [months] meses anteriores al actual (de 1 a 3), y lo
 * que lleva en el mes en curso para compararlo. Sin meses anteriores, [months] es 0 y el promedio es el propio mes.
 */
class Avg3(val expense: Double, val income: Double, val months: Int, val monthExpense: Double, val monthIncome: Double)

/**
 * La situación de UNA cuenta, como si fuera la única: sus movimientos, sus presupuestos (que pasan a ser los
 * "generales") y sus gastos fijos. El patrimonio no pertenece a ninguna cuenta y queda fuera.
 */
fun accountState(s: FinanceState, accountId: Long): FinanceState {
  val account = s.accountById[accountId] ?: return FinanceState(categories = s.categories)
  return computeState(
    listOf(account), s.categories,
    s.txs.filter { it.accountId == accountId || it.toAccountId == accountId },
    Extras(
      emptyList(), emptyList(),
      s.budgets.filter { it.accountId == accountId }.map { it.copy(accountId = com.aura.finanzas.data.ALL_ACCOUNTS) },
      s.fixed.filter { it.accountId == accountId }, emptyList(),
    ),
  )
}

class Extras(
  val assets: List<Asset>, val debts: List<Debt>, val budgets: List<Budget>, val fixed: List<FixedExpense>, val goals: List<Goal>,
)

class FinanceViewModel(app: Application) : AndroidViewModel(app) {
  private val dao = AppDb.get(app).dao()
  private val prefs = Prefs(app)

  val settings = prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

  private val extras = combine(dao.assets(), dao.debts(), dao.budgets(), dao.fixedExpenses(), dao.goals(), ::Extras)

  /** Cambia para volver a calcular el estado aunque la base no haya cambiado (gesto de actualizar, cambio de día). */
  private val tick = MutableStateFlow(0)

  val state = combine(dao.accounts(), dao.categories(), dao.transactions(), extras, tick) { a, c, t, x, _ -> computeState(a, c, t, x) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceState())

  init {
    viewModelScope.launch {
      if (dao.categoryCount() == 0) dao.upsertCategories(defaultCategories)
    }
    viewModelScope.launch { prefs.migrateSecrets() }
    // El widget de inicio se redibuja cada vez que cambian los datos.
    viewModelScope.launch { state.collect { AuraWidget.refresh(getApplication()) } }
    // Cada cambio en los datos se sube solo a la cuenta de Google, unos segundos después del último toque.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    viewModelScope.launch { state.drop(1).debounce(4_000).collect { syncNow(force = true) } }
  }

  // ---------- Kapi Premium (Google Play) ----------

  private val billing = Billing(app) { owned -> viewModelScope.launch { if (settings.value.premium != owned) prefs.set(Keys.premium, owned) } }
  val offers get() = billing.offers
  val billingMessage get() = billing.message

  fun connectBilling() = billing.connect()
  fun restorePurchases() = billing.restore()
  fun buy(activity: android.app.Activity, offer: PremiumOffer) = billing.buy(activity, offer)

  // ---------- Cuentas compartidas: con quién ----------

  /** Correos con acceso a cada cuenta compartida, por su shareId. */
  val sharedPeople = MutableStateFlow<Map<String, List<SharedPerson>>>(emptyMap())

  private fun loadSharedPeople() = viewModelScope.launch {
    sharedPeople.value = withContext(Dispatchers.IO) { FireSync.people(getApplication()) }
  }

  init {
    // El recordatorio diario viene activado: se (re)programa a su hora cada vez que se abre la app.
    viewModelScope.launch {
      val s = prefs.flow.first()
      if (s.dailyLog) DailyLog.schedule(app, s.dailyHour, s.dailyMinute)
    }
    loadSharedPeople()
    billing.connect()
    // Con sesión abierta, la nube de Kapi también se revisa cada pocas horas aunque no se abra la app.
    if (FireSync.signedIn(app)) SyncWork.schedule(app)
  }

  /** El widget pidió abrir la app directamente en "nuevo gasto". */
  val pendingAdd = MutableStateFlow(false)

  fun setAiKey(provider: String, key: String) = viewModelScope.launch { prefs.setSecret(provider, key) }

  fun exportPdf(uri: Uri) = dataJob("Informe PDF guardado") { PdfReport.write(getApplication(), uri, state.value) }

  /** Activa o apaga el recordatorio diario de registros y lo programa a su hora. */
  fun setDailyLog(on: Boolean, hour: Int = settings.value.dailyHour, minute: Int = settings.value.dailyMinute) = viewModelScope.launch {
    prefs.set(Keys.dailyLog, on)
    prefs.set(Keys.dailyHour, hour)
    prefs.set(Keys.dailyMinute, minute)
    if (on) DailyLog.schedule(getApplication(), hour, minute) else DailyLog.cancel(getApplication())
  }

  /** Muestra ya el recordatorio diario, para ver cómo llega. */
  fun testDailyLog() = viewModelScope.launch {
    if (!DailyLog.notify(getApplication(), force = true)) notice.value = "Activa las notificaciones de Kapi en los ajustes del teléfono"
  }

  fun setReminders(on: Boolean) {
    setPref(Keys.reminders, on)
    if (on) { Reminders.schedule(getApplication()); Reminders.runNow(getApplication()) } else Reminders.cancel(getApplication())
  }

  /** True mientras dura el gesto de deslizar hacia abajo para actualizar. */
  val refreshing = MutableStateFlow(false)

  /** Recalcula saldos y promedios y, si hay cuenta de Google conectada, trae lo nuevo (incluidas las cuentas compartidas). */
  fun refresh() {
    if (refreshing.value) return
    refreshing.value = true
    tick.update { it + 1 }
    syncNow(force = true)
    viewModelScope.launch {
      kotlinx.coroutines.delay(900)
      cloud.first { !it.busy }
      refreshing.value = false
    }
  }

  /** Comparte la cuenta con otra persona por su correo de Google; ambos pueden registrar gastos e ingresos en ella. */
  fun shareWith(accountId: Long, email: String) = viewModelScope.launch {
    cloud.update { it.copy(busy = true) }
    notice.value = try {
      FireSync.invite(getApplication(), accountId, email)
      "Cuenta compartida con $email. Le aparece al entrar a Kapi con ese correo."
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      "No se pudo compartir: ${e.message ?: e.javaClass.simpleName}"
    } finally {
      cloud.update { it.copy(busy = false) }
      loadSharedPeople()
    }
  }

  fun saveTx(tx: Tx) = viewModelScope.launch { dao.upsert(tx) }
  fun deleteTx(tx: Tx) = viewModelScope.launch { dao.delete(tx) }
  fun saveAccount(a: Account) = viewModelScope.launch { dao.upsert(a) }
  fun saveCategory(c: Category) = viewModelScope.launch { dao.upsert(c) }
  fun deleteCategory(c: Category) = viewModelScope.launch { dao.delete(c) }

  fun saveAsset(a: Asset) = viewModelScope.launch { dao.upsert(a) }
  fun deleteAsset(a: Asset) = viewModelScope.launch { dao.delete(a) }
  fun saveDebt(d: Debt) = viewModelScope.launch { dao.upsert(d) }
  fun deleteDebt(d: Debt) = viewModelScope.launch { dao.delete(d) }
  fun saveBudget(b: Budget) = viewModelScope.launch { dao.upsert(b) }
  fun deleteBudget(b: Budget) = viewModelScope.launch { dao.delete(b) }
  /**
   * Guarda un gasto fijo. Si en la hoja se cambió el estado del mes, se hace lo mismo que con el selector de la
   * lista: al pasar a "Pagado" se registra el gasto en su cuenta y al volver a "No pagado" se quita.
   */
  fun saveFixed(f: FixedExpense) = viewModelScope.launch {
    val month = YearMonth.now().toString()
    val before = state.value.fixed.firstOrNull { it.id == f.id && f.id != 0L }
    val wasPaid = before?.lastPaidMonth == month
    val nowPaid = f.lastPaidMonth == month
    val id = dao.upsert(f).takeIf { it > 0 } ?: f.id
    if (!wasPaid && nowPaid) registerFixedPayment(f.copy(id = id))
    if (wasPaid && !nowPaid) removeFixedPayment(id)
  }

  private suspend fun registerFixedPayment(f: FixedExpense) {
    val account = f.accountId ?: return
    if (state.value.accountById[account] == null) return
    dao.upsert(Tx(type = TxType.EXPENSE, amount = f.amount, accountId = account, categoryId = f.categoryId, note = f.name, fixedId = f.id))
  }

  /** Quita el gasto que se registró este mes al marcar como pagado el gasto fijo [fixedId]. */
  private suspend fun removeFixedPayment(fixedId: Long) {
    val start = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    state.value.txs.filter { it.fixedId == fixedId && it.date >= start }.forEach { dao.delete(it) }
  }

  /** Vuelve a dejar el gasto fijo como "No pagado" este mes y quita el gasto que se había registrado. */
  fun unpayFixed(f: FixedExpense) = viewModelScope.launch {
    removeFixedPayment(f.id)
    dao.upsert(f.copy(lastPaidMonth = ""))
    notice.value = "${f.name}: marcado como no pagado"
  }
  fun deleteFixed(f: FixedExpense) = viewModelScope.launch { dao.delete(f) }
  fun saveGoal(g: Goal) = viewModelScope.launch { dao.upsert(g) }
  fun deleteGoal(g: Goal) = viewModelScope.launch { dao.delete(g) }

  /** Marca el gasto fijo como pagado este mes y, si tiene cuenta asignada, registra el gasto. */
  fun payFixed(f: FixedExpense) = viewModelScope.launch {
    registerFixedPayment(f)
    dao.upsert(f.copy(lastPaidMonth = YearMonth.now().toString()))
  }

  fun contributeGoal(g: Goal, amount: Double) = viewModelScope.launch { dao.upsert(g.copy(saved = g.saved + amount)) }

  /** Elimina la cuenta junto con sus movimientos (incluidas las transferencias que la tocan). */
  fun deleteAccount(a: Account) = viewModelScope.launch {
    if (a.shareId.isNotEmpty()) withContext(Dispatchers.IO) { FireSync.leave(getApplication(), a.shareId) }
    dao.deleteTxsOfAccount(a.id)
    dao.delete(a)
  }

  /** Guarda el orden en que la persona dejó las cuentas al arrastrarlas. */
  fun reorderAccounts(ids: List<Long>) = viewModelScope.launch {
    val byId = state.value.accountById
    ids.forEachIndexed { i, id -> byId[id]?.takeIf { it.sort != i }?.let { dao.upsert(it.copy(sort = i)) } }
  }

  fun <T> setPref(key: Preferences.Key<T>, value: T) = viewModelScope.launch { prefs.set(key, value) }
  fun setPalette(v: Int) = setPref(Keys.palette, v)
  fun setThemeMode(v: Int) = setPref(Keys.themeMode, v)
  fun setAnimatedBg(v: Boolean) = setPref(Keys.animatedBg, v)
  fun setBgStyle(v: Int) = setPref(Keys.bgStyle, v)
  fun setHideAmounts(v: Boolean) = setPref(Keys.hideAmounts, v)

  /**
   * La app está por abrir la cámara, el dictado o el selector de archivos: esa salida momentánea
   * no debe activar el bloqueo. Se apaga sola cuando la app vuelve a primer plano.
   */
  var expectingResult = false

  // ---------- Inteligencia artificial ----------

  val ai = MutableStateFlow(AiUi())

  /** Movimiento interpretado por la IA (recibo o dictado) que espera revisión antes de guardarse. */
  val draft = MutableStateFlow<Tx?>(null)
  private var aiJob: Job? = null

  fun cancelAi() {
    aiJob?.cancel()
    ai.update { it.copy(busy = null) }
  }

  fun clearAiError() = ai.update { it.copy(error = null) }

  private fun runAi(label: String, block: suspend (Settings) -> Unit) {
    aiJob?.cancel()
    val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
      ai.update { it.copy(busy = label, error = null) }
      try {
        block(settings.value)
      } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
      } catch (e: AiException) {
        ai.update { it.copy(error = e.message) }
      } catch (e: Throwable) {
        ai.update { it.copy(error = "Algo falló: ${e.message ?: e.javaClass.simpleName}") }
      } finally {
        // Si otra petición ya reemplazó a esta, el indicador de espera es de la nueva.
        if (aiJob === coroutineContext[Job]) ai.update { it.copy(busy = null) }
      }
    }
    aiJob = job
    job.start()
  }

  private val advisorSystem = "Eres un asesor de finanzas personales experto para una persona en Colombia; las cifras están en pesos colombianos (COP). " +
    "Responde en español, en texto plano sin markdown ni asteriscos, con frases cortas y viñetas que empiecen con •. " +
    "Usa solo las cifras reales del resumen, no inventes datos y di con claridad cuando falte información. " +
    "REGLA DE PATRIMONIO: el patrimonio neto es SOLO activos registrados en Patrimonio menos deudas. NUNCA sumes el saldo de las cuentas " +
    "(efectivo, bancos, billeteras, tarjetas) al patrimonio ni a los activos; si hablas del dinero en cuentas, preséntalo aparte como \"dinero en cuentas\". " +
    "MEMORIA: lo que la persona te ha contado (préstamos que le deben o que debe, planes, cambios de ingreso, compromisos) es tan importante como sus registros: " +
    "aplícalo en tus cálculos, menciónalo cuando cambie la respuesta y no lo contradigas. " +
    "SÉ BREVE Y PRECISO: máximo unas 120 palabras. Ve directo a lo que más pesa (la cifra, la tendencia o el riesgo principal) y da como mucho 3 recomendaciones " +
    "de una línea, concretas, con monto y plazo, ordenadas de mayor a menor impacto. Sin saludos, sin repetir la pregunta, sin resúmenes ni frases genéricas " +
    "(\"lleva un control\", \"revisa tus gastos\"). Escribe las cifras en pesos con punto de miles, como $393.514. Si algo no se puede calcular con los datos, dilo en una frase. Es orientación educativa, no asesoría financiera profesional."

  /** Resumen en texto de la situación financiera, que se envía como contexto a la IA. */
  private fun snapshot(s: FinanceState, worth: Boolean = true, date: Boolean = true): String = buildString {
    fun m(v: Double) = "$" + "%,.0f".format(java.util.Locale.GERMANY, v)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val elapsed = today.dayOfMonth.toDouble() / today.lengthOfMonth()
    if (date) appendLine("Fecha de hoy: $today (va el ${(elapsed * 100).toInt()} % del mes).")
    if (worth) {
      appendLine("Patrimonio neto (SOLO activos menos deudas, sin las cuentas): ${m(s.netWorth)} = activos ${m(s.assetsTotal)} − deudas ${m(s.debtsTotal)}.")
      appendLine("Dinero en cuentas (aparte, NO es patrimonio): ${m(s.accountsTotal)}.")
    }
    appendLine("Este mes: ingresos ${m(s.monthIncome)}, gastos ${m(s.monthExpense)}; al ritmo actual el gasto cerraría en unos ${m(if (elapsed > 0) s.monthExpense / elapsed else s.monthExpense)}.")
    val nonTransfer = s.txs.filter { it.type != TxType.TRANSFER }
    val byMonth = nonTransfer.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }
    val past = byMonth.keys.filter { it < YearMonth.now() }.sortedDescending().take(6)
    if (past.isNotEmpty()) {
      appendLine("Meses anteriores (ingresos / gastos / ahorro):")
      past.forEach { ym ->
        val list = byMonth.getValue(ym)
        val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amount }
        val exp = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        appendLine("- $ym: ${m(inc)} / ${m(exp)} / ${m(inc - exp)}" + if (inc > 0) " (${((inc - exp) / inc * 100).toInt()} % ahorrado)" else "")
      }
    }
    // Cada categoría del mes frente a su promedio de los 3 meses anteriores
    val prev3 = past.take(3)
    if (s.monthByCategory.isNotEmpty()) {
      appendLine("Gasto del mes por categoría (vs. promedio de los 3 meses anteriores):")
      s.monthByCategory.take(10).forEach { c ->
        val avg = if (prev3.isEmpty()) 0.0 else prev3.sumOf { ym -> byMonth.getValue(ym).filter { it.type == TxType.EXPENSE && it.categoryId == c.category?.id }.sumOf { it.amount } } / prev3.size
        appendLine("- ${c.category?.name ?: "Sin categoría"}: ${m(c.amount)}" + if (avg > 0) " (promedio ${m(avg)})" else "")
      }
    }
    if (s.accounts.isNotEmpty()) {
      appendLine("Cuentas (cada una independiente; su saldo no es patrimonio):")
      s.accounts.forEach { a ->
        val av = s.avg3[a.id]
        appendLine("- ${a.name}: saldo ${m(s.balances[a.id] ?: 0.0)}, este mes gastó ${m(av?.monthExpense ?: 0.0)} e ingresó ${m(av?.monthIncome ?: 0.0)}, promedio mensual de gasto ${m(av?.expense ?: 0.0)}" + if (a.shareId.isNotEmpty()) " (compartida)" else "")
      }
    }
    if (s.budgets.isNotEmpty()) {
      appendLine("Presupuestos del mes:")
      s.budgets.filter { it.monthlyLimit > 0 }.forEach { b ->
        val scope = s.accountById[b.accountId]?.name?.let { " de la cuenta $it" } ?: ""
        val spent = if (b.accountId == com.aura.finanzas.data.ALL_ACCOUNTS) (if (b.isTotal) s.monthExpense else s.monthSpent[b.categoryId] ?: 0.0)
        else s.txs.filter { it.accountId == b.accountId && it.type == TxType.EXPENSE && YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) == YearMonth.now() && (b.isTotal || it.categoryId == b.categoryId) }.sumOf { it.amount }
        appendLine("- " + (if (b.isTotal) "todos los gastos" else s.categoryById[b.categoryId]?.name ?: "categoría") + "$scope: límite ${m(b.monthlyLimit)}, gastado ${m(spent)}")
      }
    }
    if (worth && s.assets.isNotEmpty()) appendLine("Activos: " + s.assets.joinToString("; ") { "${it.name} (${it.kind}) ${m(it.currentValue())}" + (it.ratePct?.let { r -> " al $r % E.A." } ?: "") })
    if (worth && s.debts.isNotEmpty()) appendLine("Deudas: " + s.debts.joinToString("; ") { "${it.name} saldo ${m(it.balance)}" + (it.ratePct?.let { r -> ", tasa $r % E.A." } ?: "") + (it.monthlyPayment?.let { p -> ", cuota ${m(p)}" } ?: "") })
    val fixed = s.fixed.filter { it.active }
    if (fixed.isNotEmpty()) {
      val month = YearMonth.now().toString()
      appendLine("Gastos fijos mensuales (${m(fixed.sumOf { it.amount })}): " + fixed.joinToString("; ") { "${it.name} ${m(it.amount)} día ${it.dayOfMonth}" + (if (it.subscription) " (suscripción)" else "") + if (it.lastPaidMonth == month) " pagado" else " pendiente" })
    }
    if (worth && s.goals.isNotEmpty()) appendLine("Metas: " + s.goals.joinToString("; ") { "${it.name} ${m(it.saved)} de ${m(it.target)}" })
    val ants = antExpenses(s)
    if (ants.isNotEmpty()) appendLine("Gastos pequeños repetidos (últimos 30 días): " + ants.joinToString("; ") { "${it.label} ${it.count} veces, total ${m(it.total)}" })
    val recent = nonTransfer.take(25)
    if (recent.isNotEmpty()) {
      appendLine("Últimos movimientos:")
      recent.forEach { t ->
        appendLine("- ${Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate()} ${if (t.type == TxType.INCOME) "ingreso" else "gasto"} ${m(t.amount)} · ${s.categoryById[t.categoryId]?.name ?: "sin categoría"} · ${s.accountById[t.accountId]?.name ?: ""}" + if (t.note.isNotBlank()) " · ${t.note.take(40)}" else "")
      }
    }
  }

  // ---------- Asistente: conversación con memoria ----------

  /** Cuenta que se está analizando en el asistente; null = cada cuenta por separado. */
  val focusAccount = MutableStateFlow<Long?>(null)

  private val accountsRule = "REGLA DE CUENTAS: cada cuenta es independiente (puede ser de otra persona o tener otro propósito). " +
    "Analiza cada cuenta por separado con sus propios ingresos, gastos, categorías y presupuestos. NUNCA sumes cuentas entre sí, " +
    "no des totales combinados de varias cuentas y no propongas pasar dinero de una cuenta a otra."

  /** Las cifras que se envían a la IA: solo la cuenta elegida, o cada cuenta en su propio bloque. */
  private fun accountsSnapshot(s: FinanceState, focus: Long?, intent: String?): String = buildString {
    val one = focus?.let { s.accountById[it] }
    if (one != null) {
      appendLine("SE ANALIZA SOLO LA CUENTA \"${one.name}\". No hables de otras cuentas." + if (intent == "WORTH") "" else " No hables del patrimonio salvo que la pregunta lo pida.")
      append(snapshot(accountState(s, one.id), worth = intent == "WORTH"))
      return@buildString
    }
    val today = LocalDate.now()
    appendLine("Fecha de hoy: $today (va el ${(today.dayOfMonth * 100 / today.lengthOfMonth())} % del mes).")
    if (s.accounts.size > 1) appendLine("Hay ${s.accounts.size} cuentas independientes; cada una va en su propio bloque.")
    s.accounts.forEach { a ->
      appendLine()
      appendLine("=== CUENTA \"${a.name}\" ===")
      append(snapshot(accountState(s, a.id), worth = false, date = false))
    }
    appendLine()
    appendLine("=== PATRIMONIO (no pertenece a ninguna cuenta) ===")
    appendLine("Patrimonio neto (SOLO activos menos deudas): \$" + "%,.0f".format(java.util.Locale.GERMANY, s.netWorth))
    if (s.assets.isNotEmpty()) appendLine("Activos: " + s.assets.joinToString("; ") { "${it.name} (${it.kind}) \$" + "%,.0f".format(java.util.Locale.GERMANY, it.currentValue()) })
    if (s.debts.isNotEmpty()) appendLine("Deudas: " + s.debts.joinToString("; ") { "${it.name} saldo \$" + "%,.0f".format(java.util.Locale.GERMANY, it.balance) })
    if (s.goals.isNotEmpty()) appendLine("Metas: " + s.goals.joinToString("; ") { "${it.name} \$" + "%,.0f".format(java.util.Locale.GERMANY, it.saved) + " de \$" + "%,.0f".format(java.util.Locale.GERMANY, it.target) })
  }

  /** Respuesta calculada en el teléfono: con «cada cuenta», un bloque por cuenta. */
  private fun localReply(clean: String, s: FinanceState, memos: List<String>, intent: String?, focus: Long?): String {
    val one = focus?.let { s.accountById[it] }
    if (one != null) return if (intent == "WORTH") LocalBrain.reply(clean, s, memos, intent) else "${one.name}:\n" + LocalBrain.reply(clean, accountState(s, one.id), memos, intent ?: "ACCOUNTS".takeIf { LocalBrain.amounts(clean).isEmpty() })
    if (intent !in setOf(null, "ALL", "ACCOUNTS", "ANTS") || s.accounts.size < 2 || LocalBrain.amounts(clean).isNotEmpty()) return LocalBrain.reply(clean, s, memos, intent)
    val each = if (intent == "ANTS") "ANTS" else "ACCOUNTS"
    val blocks = s.accounts.joinToString("\n\n") { a -> "${a.name}:\n" + LocalBrain.reply(clean, accountState(s, a.id), emptyList(), each) }
    val worth = if (intent == "ALL") "\n\nPatrimonio:\n" + LocalBrain.analysis(s, 1) else ""
    val notes = memos.takeLast(5)
    return blocks + worth + if (notes.isEmpty()) "" else "\n\nTengo presente lo que me contaste:\n" + notes.joinToString("\n") { "• $it" }
  }

  /** Conversación completa, en orden. Los mensajes con memo = true son la memoria del asistente. */
  val notes = dao.aiNotes().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /**
   * Inicio de la conversación actual. Cada vez que se abre la app (este ViewModel nace con la actividad) el chat
   * empieza vacío; lo anterior queda en el historial. Se usa la fecha y no el id porque la copia de la nube trae
   * los mensajes con los ids de otro teléfono.
   */
  val chatSince = MutableStateFlow(System.currentTimeMillis())

  private fun memory(all: List<AiNote>) = all.filter { it.memo && it.role == "USER" }

  /** Lo que la persona ha contado y los últimos mensajes, para que la IA responda con ese contexto. */
  private fun context(all: List<AiNote>): String = buildString {
    val zone = ZoneId.systemDefault()
    val memos = memory(all).takeLast(60)
    if (memos.isNotEmpty()) {
      appendLine("MEMORIA — cosas que la persona me ha contado (no están en sus registros; aplícalas en el análisis):")
      memos.forEach { appendLine("- [${Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()}] ${it.text.take(300)}") }
      appendLine()
    }
    val recent = all.filter { !it.memo && it.date >= chatSince.value }.takeLast(16)
    if (recent.isNotEmpty()) {
      appendLine("Conversación reciente:")
      recent.forEach { appendLine((if (it.role == "USER") "Persona: " else "Asesor: ") + it.text.take(500)) }
      appendLine()
    }
  }

  /**
   * Mensaje al asistente. Si es algo que la persona cuenta (no una pregunta) queda guardado en la memoria;
   * [remember] lo fuerza en un sentido u otro. [intent] viene de los botones rápidos (ALL, WORTH, ACCOUNTS, ANTS, MEMORY).
   * Con clave de IA responde la IA; sin clave, o si falla, responde el análisis que se calcula en el teléfono.
   */
  fun chat(text: String, remember: Boolean? = null, intent: String? = null) {
    val clean = text.trim().take(600)
    if (clean.isEmpty()) return
    val memo = remember ?: (intent == null && !LocalBrain.isQuestion(clean))
    val focus = focusAccount.value
    runAi(if (memo) "Guardando en mi memoria…" else "Pensando…") { s ->
      val before = notes.value
      dao.upsert(AiNote(role = "USER", text = clean, memo = memo))
      val memos = memory(before).map { it.text }
      val local = { if (memo) LocalBrain.noted(clean) else localReply(clean, state.value, memos, intent, focus) }
      val answer = if (s.aiKey.isBlank()) {
        kotlinx.coroutines.delay(if (memo) 500 else 1100)
        local()
      } else try {
        val ask = if (memo) "La persona te cuenta esto para que lo tengas presente en adelante: \"$clean\".\nConfirma en una o dos frases que lo anotaste. Si para aconsejarla bien te falta un dato (un valor, una fecha, un plazo), pregúntaselo en una sola frase. No des un análisis completo."
        else "Pregunta de la persona: $clean\n\n" + (if (focus == null && state.value.accounts.size > 1 && intent != "WORTH" && intent != "MEMORY")
          "Responde por cuenta: para cada cuenta, una línea con su nombre y debajo máximo 2 viñetas de diagnóstico y 1 de \"Qué hacer\" (con monto o plazo). " +
          "Si la pregunta es sobre una sola cuenta, responde solo esa. Nada de introducciones, repeticiones ni cierres. "
        else "Responde corto y preciso, con cifras reales: máximo 3 viñetas de diagnóstico (lo más importante primero) " +
          "y luego \"Qué hacer:\" con máximo 3 recomendaciones de una sola línea, cada una con monto o plazo. Nada de introducciones, repeticiones ni cierres. ") +
          "Usa lo que está en la MEMORIA cuando cambie la respuesta (dilo en pocas palabras) y recuerda que el saldo de las cuentas no se suma al patrimonio."
        Ai.ask(s, advisorSystem + " " + accountsRule, context(before) + ask + "\n\nSituación financiera registrada:\n" + accountsSnapshot(state.value, focus, intent), deep = !memo)
          .replace("**", "").replace(Regex("(?m)^#+\\s*"), "").trim()
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        local() + "\n\n(La IA no respondió: ${e.message ?: e.javaClass.simpleName} Esto lo calculé en tu teléfono.)"
      }
      dao.upsert(AiNote(role = "AI", text = answer))
    }
  }

  /** Marca o desmarca un mensaje como parte de la memoria del asistente. */
  fun toggleMemo(n: AiNote) = viewModelScope.launch { dao.upsert(n.copy(memo = !n.memo)) }
  fun deleteNote(n: AiNote) = viewModelScope.launch { dao.delete(n) }

  /** Empieza una conversación nueva; la actual pasa al historial. */
  fun newChat() = chatSince.update { System.currentTimeMillis() }

  /** Borra del historial esos mensajes; lo guardado en la memoria se conserva. */
  fun deleteHistory(notes: List<AiNote>) = viewModelScope.launch { dao.deleteNotes(notes.map { it.id }) }

  fun testAi() = runAi("Probando la conexión…") { s ->
    ai.update { it.copy(test = "") }
    // Con Gemini primero se verifica la clave (no depende de que el modelo esté saturado) y luego se pide una respuesta.
    val verified = if (s.aiProvider == com.aura.finanzas.data.AiProvider.GEMINI) Ai.verifyGemini(s).also { v -> ai.update { it.copy(test = v) } } else null
    val text = try {
      Ai.ask(s, "Responde en español en una sola frase corta.", "Di que la conexión funciona y qué modelo eres.")
    } catch (e: AiException) {
      if (verified == null) throw e
      ai.update { it.copy(test = "$verified\n${e.message}") }
      return@runAi
    }
    ai.update { it.copy(test = listOfNotNull(verified, "Respuesta: $text").joinToString("\n")) }
  }

  private fun extractionPrompt(s: FinanceState, source: String) = "Extrae TODOS los movimientos de dinero de $source (puede traer uno o varios; cada compra, pago o ingreso con su propio valor es un movimiento aparte) " +
    "y responde SOLO con un objeto JSON {\"items\": [...]} donde cada elemento tiene estas claves: " +
    "\"type\" (\"GASTO\" o \"INGRESO\"), \"amount\" (número en pesos, sin puntos ni símbolos; el total pagado), " +
    "\"category\" (exactamente una de la lista que corresponda al tipo), \"account\" (una de mis cuentas si se menciona o se deduce; si no, cadena vacía), " +
    "\"note\" (comercio o descripción corta) y \"date\" (AAAA-MM-DD; si no aparece, ${LocalDate.now()}).\n" +
    "Categorías de gasto: ${s.categories.filter { it.kind == TxType.EXPENSE }.joinToString { it.name }}.\n" +
    "Categorías de ingreso: ${s.categories.filter { it.kind == TxType.INCOME }.joinToString { it.name }}.\n" +
    "Mis cuentas: ${s.accounts.joinToString { it.name }}.\n" +
    "En Colombia \"mil\" son 1000 y \"lucas\" son miles de pesos (\"20 lucas\" = 20000)."

  private fun toDraft(json: JSONObject, source: String): Tx {
    val s = state.value
    val account = s.accounts.firstOrNull { it.name.equals(json.optString("account").trim(), ignoreCase = true) } ?: s.accounts.firstOrNull()
      ?: throw AiException(NO_ACCOUNT)
    val type = if (json.optString("type").uppercase().startsWith("ING")) TxType.INCOME else TxType.EXPENSE
    val amount = json.optDouble("amount", 0.0).takeIf { !it.isNaN() && it > 0 } ?: throw AiException("No encontré el valor. Regístralo manualmente o intenta de nuevo.")
    val wanted = json.optString("category").trim()
    val category = s.categories.filter { it.kind == type }.let { list -> list.firstOrNull { it.name.equals(wanted, ignoreCase = true) } }
    val date = runCatching { LocalDate.parse(json.optString("date")).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
      .getOrDefault(System.currentTimeMillis())
    return Tx(type = type, amount = amount, accountId = account.id, categoryId = category?.id, note = json.optString("note").take(80), date = date, source = source)
  }

  /** Factura leída producto por producto, en espera de que la persona la revise. */
  val receipt = MutableStateFlow<ReceiptScan?>(null)

  /** Guarda los productos aceptados de una factura, cada uno como un gasto. */
  fun saveReceipt(txs: List<Tx>) = viewModelScope.launch {
    txs.forEach { dao.upsert(it) }
    notice.value = if (txs.size == 1) "Se registró 1 movimiento" else "Se registraron ${txs.size} movimientos, cada uno por separado"
  }

  private fun itemsPrompt(s: FinanceState, careful: Boolean) = (if (careful) "Una primera lectura de esta factura salió mal. Léela otra vez con mucho cuidado, renglón por renglón, " +
    "verificando cada valor contra la imagen y que la suma de los productos dé el total impreso.\n" else "") + "Lee esta factura o recibo y responde SOLO con un objeto JSON con estas claves: \"merchant\" (nombre del comercio, o cadena vacía si no se ve), " +
    "\"date\" (AAAA-MM-DD; si no aparece, ${LocalDate.now()}), \"total\" (total pagado en pesos, número sin puntos ni símbolos; 0 si no se ve) e \"items\": una lista con CADA producto o servicio cobrado, " +
    "cada uno con \"name\" (descripción legible, con la cantidad si es mayor que 1), \"amount\" (valor total cobrado por ese renglón, ya con descuento, número sin puntos ni símbolos) y " +
    "\"category\" (exactamente una de la lista). No incluyas subtotales, impuestos, medios de pago ni cambio. No inventes productos: si un renglón no se lee, omítelo.\n" +
    "IMPORTANTE: los precios ya incluyen el IVA. Si la factura tiene una columna de tarifa o porcentaje de IVA (0, 5, 19…) junto al total, IGNÓRALA: no la sumes ni la pegues al valor. " +
    "Usa el valor de la columna Total de cada renglón. Omite los renglones de descuento cuyo total es 0. La suma de los productos debe dar el total de la factura.\n" +
    "Categorías: ${s.categories.filter { it.kind == TxType.EXPENSE }.joinToString { it.name }}."

  /** Categoría de [options] que corresponde a la respuesta de la IA, tolerando tildes, emojis, mayúsculas y plurales. */
  private fun matchCategory(answer: String, options: List<Category>): Category? {
    fun n(x: String) = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
      .lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    val a = n(answer)
    if (a.isEmpty()) return null
    return options.firstOrNull { n(it.name) == a }
      ?: options.firstOrNull { n(it.name).removeSuffix("s") == a.removeSuffix("s") }
      ?: options.firstOrNull { c -> n(c.name).let { it.length >= 4 && (a.contains(it) || it.contains(a)) } }
  }

  /**
   * Clasifica con la IA los productos de la factura que no tienen una categoría segura (sin categoría, o con una
   * suposición general). Es una consulta corta de solo texto. Sin clave de IA, o si falla, la factura queda igual.
   */
  private suspend fun classifyWithAi(s: Settings, scan: ReceiptScan): ReceiptScan {
    if (s.aiKey.isBlank()) return scan
    val options = state.value.categories.filter { it.kind == TxType.EXPENSE }
    val pending = scan.items.withIndex().filter { (_, it) -> it.type == TxType.EXPENSE && (it.categoryId == null || it.guessed) }
    if (pending.isEmpty() || options.isEmpty()) return scan
    ai.update { it.copy(busy = "Clasificando los productos con IA…") }
    val prompt = "Clasifica cada producto de una factura" + (if (scan.merchant.isNotBlank()) " de \"${scan.merchant}\"" else "") +
      " (Colombia) en UNA de estas categorías de gasto: ${options.joinToString { it.name }}.\n" +
      "Responde SOLO con un objeto JSON {\"categorias\": [...]} con una categoría por producto, en el mismo orden, escrita exactamente como en la lista.\n" +
      "Productos:\n" + pending.mapIndexed { i, (_, it) -> "${i + 1}. ${it.name}" }.joinToString("\n")
    val answers = try {
      Ai.jsonFrom(Ai.ask(s, "Eres un clasificador de gastos personales. Respondes solo JSON válido.", prompt, json = true)).optJSONArray("categorias")
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      null
    } ?: return scan
    val items = scan.items.toMutableList()
    pending.forEachIndexed { i, (index, item) ->
      matchCategory(answers.optString(i), options)?.let { c ->
        items[index] = ReceiptItem(item.name, item.amount, c.id, item.doubt, item.type, guessed = false, byAi = true)
      }
    }
    return ReceiptScan(scan.merchant, scan.date, scan.accountId, items, scan.total, scan.voice, scan.photo, scan.careful)
  }

  private fun toScan(json: JSONObject): ReceiptScan {
    val s = state.value
    val account = s.accounts.firstOrNull() ?: throw AiException(NO_ACCOUNT)
    val options = s.categories.filter { it.kind == TxType.EXPENSE }
    val list = json.optJSONArray("items")
    val items = (0 until (list?.length() ?: 0)).mapNotNull { i ->
      val o = list!!.optJSONObject(i) ?: return@mapNotNull null
      val amount = o.optDouble("amount", 0.0).takeIf { !it.isNaN() && it > 0 } ?: return@mapNotNull null
      val name = o.optString("name").trim().take(80).ifEmpty { return@mapNotNull null }
      // Lo que la persona ya clasificó antes manda sobre lo que diga la IA.
      val known = LocalBrain.historyCategory(name, TxType.EXPENSE, s)
      val byAi = matchCategory(o.optString("category"), options)
      ReceiptItem(name, amount, (known ?: byAi)?.id, byAi = known == null && byAi != null)
    }
    val date = runCatching { LocalDate.parse(json.optString("date")).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrDefault(System.currentTimeMillis())
    return ReceiptScan(json.optString("merchant").trim(), date, account.id, items, json.optDouble("total", 0.0).takeIf { !it.isNaN() && it > 0 })
  }

  /**
   * Espera la respuesta de la IA como mucho [ms]; null si tarda más. La consulta corre aparte porque la conexión
   * no se puede interrumpir a mitad: si se pasa del tiempo, su respuesta simplemente se descarta.
   */
  private suspend fun within(ms: Long, ask: suspend () -> String): String? {
    val call = viewModelScope.async(Dispatchers.IO) { ask() }
    return kotlinx.coroutines.withTimeoutOrNull(ms) { call.await() } ?: run { call.cancel(); null }
  }

  /**
   * Lee un recibo desde la foto en [uri] y deja el resultado en [receipt] para revisarlo (producto por producto,
   * o un solo renglón por el total). Con clave de IA lo lee la IA; sin clave, o si no responde a tiempo, se lee
   * en el propio teléfono y se avisa. [careful] = la persona pidió leerla de nuevo: la IA razona más (más lento,
   * más preciso).
   */
  fun scanReceipt(uri: Uri, careful: Boolean = false) = runAi(if (careful) "Leyendo la factura con más cuidado…" else "Leyendo el recibo…") { s ->
    val app = getApplication<Application>()
    val account = state.value.accounts.firstOrNull() ?: throw AiException(NO_ACCOUNT)
    var aiProblem: String? = null
    if (s.aiKey.isNotBlank()) {
      try {
        val jpeg = withContext(Dispatchers.IO) { loadJpeg(app, uri) } ?: throw AiException("No pude abrir la imagen.")
        val answer = within(if (careful) 130_000L else 75_000L) {
          Ai.ask(s, "Eres un extractor de datos de recibos y facturas. Respondes solo JSON válido.", itemsPrompt(state.value, careful), imageJpeg = jpeg, deep = careful, json = true)
        } ?: throw AiException("La IA tardó demasiado.")
        val read = toScan(Ai.jsonFrom(answer))
        // Un recibo sin detalle de productos queda como un solo renglón por el total.
        val total = read.total
        val items = if (read.items.isEmpty() && total != null) listOf(ReceiptItem(read.merchant.ifBlank { "Compra" }, total)) else read.items
        if (items.isNotEmpty()) {
          receipt.value = ReceiptScan(read.merchant, read.date, read.accountId, items, total, photo = uri, careful = careful)
          return@runAi
        }
        aiProblem = "La IA no encontró productos ni total en la foto."
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        aiProblem = (e as? AiException)?.message ?: "La IA no respondió."
      }
    }
    val pieces = Ocr.pieces(app, uri)
    val local = LocalBrain.parseReceiptItems(pieces, state.value)?.let { classifyWithAi(s, it) }
      ?: LocalBrain.parseReceipt(Ocr.text(pieces), state.value)?.let { t ->
        ReceiptScan(t.note, t.date, t.accountId, listOf(ReceiptItem(t.note.ifBlank { "Compra" }, t.amount, t.categoryId)), t.amount.takeIf { it > 0 })
      }
      ?: ReceiptScan("", System.currentTimeMillis(), account.id, listOf(ReceiptItem("Compra", 0.0)), null)
    receipt.value = ReceiptScan(local.merchant, local.date, local.accountId, local.items, local.total, photo = uri, careful = careful)
    notice.value = (aiProblem?.let { "$it " } ?: "") + "Leí la factura en el teléfono: revisa los valores o toca «Leer de nuevo»."
  }

  /** Interpreta un gasto o ingreso dictado o escrito; el resultado queda en [draft] para revisión. */
  fun parseSpoken(text: String) = runAi("Interpretando…") { s ->
    if (state.value.accounts.isEmpty()) throw AiException(NO_ACCOUNT)
    if (s.aiKey.isNotBlank()) {
      try {
        // Si la IA tarda, se usa lo que se entiende en el teléfono para no dejar a la persona esperando.
        val out = within(12_000L) {
          Ai.ask(s, "Eres un extractor de datos de movimientos de dinero. Respondes solo JSON válido.", extractionPrompt(state.value, "esta frase") + "\n\nFrase: \"$text\"", json = true)
        } ?: throw AiException("La IA tardó demasiado.")
        val json = Ai.jsonFrom(out)
        val list = json.optJSONArray("items")
        val drafts = if (list == null) listOf(toDraft(json, "VOZ"))
        else (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let { runCatching { toDraft(it, "VOZ") }.getOrNull() } }
        if (drafts.size >= 2) {
          receipt.value = ReceiptScan("", drafts.first().date, drafts.first().accountId, drafts.map { ReceiptItem(it.note, it.amount, it.categoryId, type = it.type) }, null, voice = true)
          return@runAi
        }
        drafts.singleOrNull()?.let { draft.value = it; return@runAi }
      } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
      }
    }
    // Varios movimientos en una misma frase quedan como registros independientes, cada uno con su valor y categoría.
    LocalBrain.parsePhrases(text, state.value)?.let { receipt.value = it; return@runAi }
    draft.value = LocalBrain.parsePhrase(text, state.value) ?: throw AiException("No encontré el valor en la frase. Di, por ejemplo: \"almuerzo 25 mil\".")
  }

  // ---------- Nube de Kapi (Firebase): copia y cuentas compartidas ----------

  val cloud = MutableStateFlow(CloudUi())
  private var lastSyncAttempt = 0L

  /**
   * Sincroniza con la nube si hay sesión abierta. Sin [force] no repite antes de 30 segundos (para no hacerlo en
   * cada regreso a la app). [resolve] decide un conflicto: "LOCAL" o "REMOTE".
   */
  fun syncNow(resolve: String? = null, force: Boolean = false) {
    val now = System.currentTimeMillis()
    if (cloud.value.busy) { if (force || resolve != null) syncAgain = true; return }
    if (!force && resolve == null && now - lastSyncAttempt < 30_000) return
    if (!FireSync.signedIn(getApplication())) return
    watchShared()
    lastSyncAttempt = now
    viewModelScope.launch {
      cloud.update { it.copy(busy = true, message = null) }
      try {
        // Primero la copia completa de esta persona; luego las cuentas compartidas, que se crean solas si faltan
        // (así traer la copia de la nube nunca borra una cuenta que otra persona acaba de compartir).
        val outcome = FireSync.syncOwn(getApplication(), prefs, resolve)
        cloud.update { it.copy(conflict = outcome == FireSync.Outcome.CONFLICT) }
        val before = state.value.accounts.map { it.shareId }.toSet()
        val pulled = FireSync.syncShared(getApplication())
        val fresh = withContext(Dispatchers.IO) { dao.accounts().first() }.filter { it.shareId.isNotEmpty() && it.shareId !in before }
        if (fresh.isNotEmpty()) notice.value = "Te compartieron: " + fresh.joinToString { it.name } + ". Ya está en tus cuentas."
        else if (pulled > 0) notice.value = if (pulled == 1) "Cuentas compartidas: 1 cambio recibido" else "Cuentas compartidas: $pulled cambios recibidos"
      } catch (e: CloudException) {
        cloud.update { it.copy(message = e.message) }
      } catch (e: IllegalStateException) {
        cloud.update { it.copy(message = "No se pudieron leer los datos de la nube: ${e.message}") }
      } catch (e: org.json.JSONException) {
        cloud.update { it.copy(message = "No se pudieron leer los datos de la nube: ${e.message}") }
      } finally {
        cloud.update { it.copy(busy = false) }
        loadSharedPeople()
        if (syncAgain) { syncAgain = false; syncNow(force = true) }
      }
    }
  }

  private var syncAgain = false
  private var sharedWatch: com.google.firebase.firestore.ListenerRegistration? = null

  /** Escucha la nube mientras la app vive: una cuenta compartida nueva aparece sin tener que recargar. */
  private fun watchShared() {
    if (sharedWatch != null) return
    sharedWatch = FireSync.watchShared(getApplication()) { syncNow(force = true) }
  }

  override fun onCleared() {
    sharedWatch?.remove()
    super.onCleared()
  }

  fun dismissConflict() = cloud.update { it.copy(conflict = false) }

  // ---------- Datos: exportar y respaldar ----------

  /** Mensaje con el resultado de una operación de datos, para mostrar una sola vez. */
  val notice = MutableStateFlow<String?>(null)

  fun exportCsv(uri: Uri) = dataJob("Movimientos exportados") { DataTools.writeCsv(getApplication(), uri, state.value) }
  fun exportBackup(uri: Uri) = dataJob("Respaldo guardado") { DataTools.writeBackup(getApplication(), uri) }
  fun importBackup(uri: Uri) = dataJob("Respaldo restaurado") { DataTools.readBackup(getApplication(), uri) }

  private fun dataJob(done: String, block: suspend () -> Unit) = viewModelScope.launch {
    notice.value = try {
      withContext(Dispatchers.IO) { block() }
      done
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
      "No se pudo completar: ${e.message ?: e.javaClass.simpleName}"
    }
  }
}

data class CloudUi(val busy: Boolean = false, val message: String? = null, val conflict: Boolean = false)

data class AiUi(
  val busy: String? = null,
  val error: String? = null,
  val test: String = "",
)

private const val NO_ACCOUNT = "Primero crea una cuenta para poder registrar el movimiento."

class Ant(val label: String, val icon: String, val colorIdx: Int, val count: Int, val total: Double)

/** Tope de un gasto para contarlo como "hormiga". */
const val ANT_LIMIT = 30_000.0

/**
 * Gastos hormiga: gastos pequeños (hasta [ANT_LIMIT]) que se repiten 3 o más veces en los últimos 30 días,
 * agrupados por categoría y nota. Se calcula en el teléfono, sin IA.
 */
fun antExpenses(s: FinanceState): List<Ant> {
  val since = System.currentTimeMillis() - 30L * 86_400_000L
  return s.txs.filter { it.type == TxType.EXPENSE && it.date >= since && it.amount <= ANT_LIMIT }
    .groupBy { it.categoryId to it.note.trim().lowercase() }
    .filter { it.value.size >= 3 }
    .map { (key, list) ->
      val cat = s.categoryById[key.first]
      val label = listOfNotNull(cat?.name ?: "Sin categoría", list.first().note.trim().ifEmpty { null }).joinToString(" · ")
      Ant(label, cat?.icon ?: "COIN", cat?.colorIdx ?: 5, list.size, list.sumOf { it.amount })
    }.sortedByDescending { it.total }
}

/** Dinero libre del mes: liquidez menos los gastos fijos activos que aún no se han pagado. */
class SafeToSpend(val available: Double, val perDay: Double, val pendingFixed: Double, val pendingCount: Int, val daysLeft: Int)

fun safeToSpend(s: FinanceState): SafeToSpend {
  val month = YearMonth.now()
  val pending = s.fixed.filter { it.active && it.lastPaidMonth != month.toString() }
  val pendingTotal = pending.sumOf { it.amount }
  val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
  val available = s.liquidity - pendingTotal
  return SafeToSpend(available, available.coerceAtLeast(0.0) / daysLeft, pendingTotal, pending.size, daysLeft)
}


/** Calcula saldos y totales del mes a partir de los datos crudos. */
fun computeState(accounts: List<Account>, categories: List<Category>, txs: List<Tx>, x: Extras): FinanceState {
  val balances = accounts.associate { it.id to it.initialBalance }.toMutableMap()
  val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
  var income = 0.0
  var expense = 0.0
  val byCategory = HashMap<Long?, Double>()
  val zone = ZoneId.systemDefault()
  val thisMonth = YearMonth.now()
  val windowStart = thisMonth.minusMonths(3).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
  val nowMs = System.currentTimeMillis()
  val windowExpense = HashMap<Long, Double>()
  val windowIncome = HashMap<Long, Double>()
  val firstDate = HashMap<Long, Long>()
  val accountMonth = HashMap<Long, Double>()
  val accountMonthIncome = HashMap<Long, Double>()
  for (t in txs) {
    if (t.type != TxType.TRANSFER) {
      firstDate.merge(t.accountId, t.date, ::minOf)
      if (t.date in windowStart until monthStart) (if (t.type == TxType.EXPENSE) windowExpense else windowIncome).merge(t.accountId, t.amount, Double::plus)
      if (t.date in monthStart..nowMs) (if (t.type == TxType.EXPENSE) accountMonth else accountMonthIncome).merge(t.accountId, t.amount, Double::plus)
    }
    when (t.type) {
      TxType.INCOME -> balances.computeIfPresent(t.accountId) { _, b -> b + t.amount }
      TxType.EXPENSE -> balances.computeIfPresent(t.accountId) { _, b -> b - t.amount }
      else -> {
        balances.computeIfPresent(t.accountId) { _, b -> b - t.amount }
        t.toAccountId?.let { to -> balances.computeIfPresent(to) { _, b -> b + t.amount } }
      }
    }
    if (t.date >= monthStart) {
      if (t.type == TxType.INCOME) income += t.amount
      if (t.type == TxType.EXPENSE) {
        expense += t.amount
        byCategory.merge(t.categoryId, t.amount, Double::plus)
      }
    }
  }
  val catById = categories.associateBy { it.id }
  val now = System.currentTimeMillis()
  return FinanceState(
    accounts = accounts,
    categories = categories,
    txs = txs,
    balances = balances,
    liquidity = accounts.filter { it.liquid }.sumOf { balances[it.id] ?: 0.0 },
    monthIncome = income,
    monthExpense = expense,
    monthByCategory = byCategory.map { CategorySpend(catById[it.key], it.value) }.sortedByDescending { it.amount },
    assets = x.assets,
    debts = x.debts,
    budgets = x.budgets,
    fixed = x.fixed,
    goals = x.goals,
    monthSpent = byCategory,
    accountsTotal = balances.values.sum(),
    assetsTotal = x.assets.sumOf { it.currentValue(now) },
    debtsTotal = x.debts.sumOf { it.balance },
    avg3 = accounts.associate { a ->
      // Se promedian los meses completos anteriores al actual; una cuenta con menos de 3 meses de historia, sobre los que lleva.
      val months = firstDate[a.id]?.let { first ->
        java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(Instant.ofEpochMilli(first).atZone(zone)), thisMonth).toInt().coerceIn(0, 3)
      } ?: 0
      val monthOut = accountMonth[a.id] ?: 0.0
      val monthIn = accountMonthIncome[a.id] ?: 0.0
      a.id to if (months == 0) Avg3(monthOut, monthIn, 0, monthOut, monthIn)
      else Avg3((windowExpense[a.id] ?: 0.0) / months, (windowIncome[a.id] ?: 0.0) / months, months, monthOut, monthIn)
    },
    accountMonthSpent = accountMonth,
  )
}

/** Lee la base una sola vez y arma el estado; lo usan el widget y los recordatorios, fuera de la pantalla. */
suspend fun loadState(dao: com.aura.finanzas.data.FinanceDao): FinanceState = computeState(
  dao.accounts().first(), dao.categories().first(), dao.transactions().first(),
  Extras(dao.assets().first(), dao.debts().first(), dao.budgets().first(), dao.fixedExpenses().first(), dao.goals().first()),
)
