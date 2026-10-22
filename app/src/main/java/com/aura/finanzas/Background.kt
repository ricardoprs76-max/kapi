package com.aura.finanzas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aura.finanzas.data.AppDb
import com.aura.finanzas.data.CloudException
import com.aura.finanzas.data.FireSync
import com.aura.finanzas.data.Prefs
import com.aura.finanzas.ui.components.money
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------- Recordatorios de pagos ----------

object Reminders {
  private const val CHANNEL = "pagos"
  private const val WORK = "recordatorios"

  /** Lo que vence pronto: gastos fijos sin pagar (o ya vencidos), cuotas de deudas y CDTs por vencer. */
  fun dueLines(s: FinanceState, today: LocalDate = LocalDate.now()): List<String> {
    val month = YearMonth.from(today)
    val lines = ArrayList<String>()
    fun gap(day: Int) = day.coerceIn(1, month.lengthOfMonth()) - today.dayOfMonth
    for (f in s.fixed.filter { it.active && it.lastPaidMonth != month.toString() }) {
      val d = gap(f.dayOfMonth)
      when {
        d < 0 -> lines += "${f.name}: vencido hace ${-d} días (${money(f.amount)})"
        d == 0 -> lines += "${f.name}: vence hoy (${money(f.amount)})"
        d <= 3 -> lines += "${f.name}: vence en $d días (${money(f.amount)})"
      }
    }
    for (debt in s.debts) {
      val d = gap(debt.dueDay ?: continue)
      val amount = debt.monthlyPayment?.let { " (${money(it)})" } ?: ""
      if (d == 0) lines += "Cuota de ${debt.name}: vence hoy$amount" else if (d in 1..3) lines += "Cuota de ${debt.name}: vence en $d días$amount"
    }
    val now = System.currentTimeMillis()
    for (a in s.assets) {
      val days = ((a.endDate ?: continue) - now) / 86_400_000L
      if (a.kind == "CDT" && days in 0..7) lines += "CDT ${a.name}: vence en $days días"
    }
    return lines
  }

  /** Publica la notificación si hay algo por vencer; devuelve cuántos avisos incluyó. */
  suspend fun notifyDue(context: Context): Int {
    val lines = dueLines(loadState(AppDb.get(context).dao()))
    val manager = NotificationManagerCompat.from(context)
    if (lines.isEmpty() || !manager.areNotificationsEnabled()) return 0
    context.getSystemService(NotificationManager::class.java)
      .createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios de pagos", NotificationManager.IMPORTANCE_DEFAULT))
    val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val style = NotificationCompat.InboxStyle().also { s -> lines.take(6).forEach(s::addLine) }
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_stat_aura)
      .setContentTitle(if (lines.size == 1) "Tienes un pago pendiente" else "Tienes ${lines.size} pagos pendientes")
      .setContentText(lines.first())
      .setStyle(style)
      .setContentIntent(open)
      .setAutoCancel(true)
      .build()
    try {
      manager.notify(1, notification)
    } catch (e: SecurityException) {
      return 0 // la persona quitó el permiso de notificaciones
    }
    return lines.size
  }

  /** Revisión diaria a las 8:00 a. m. */
  fun schedule(context: Context) {
    val now = LocalDateTime.now()
    var next = now.withHour(8).withMinute(0).withSecond(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
      .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  fun runNow(context: Context) {
    WorkManager.getInstance(context).enqueueUniqueWork("$WORK-ahora", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK)
  }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    Reminders.notifyDue(applicationContext)
    return Result.success()
  }
}

// ---------- Recordatorio diario de registros ----------

object DailyLog {
  const val NOTIFICATION_ID = 2
  private const val CHANNEL = "registro_diario"
  private const val WORK = "registro-diario"

  /** Cuántos movimientos (sin contar transferencias) se registraron hoy. */
  private suspend fun todayCount(context: Context): Int {
    val s = loadState(AppDb.get(context).dao())
    val today = LocalDate.now()
    val zone = java.time.ZoneId.systemDefault()
    return s.txs.count { it.type != com.aura.finanzas.data.TxType.TRANSFER && java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() == today }
  }

  /** Avisa si hoy aún no hay registros (o siempre, con [force], para probar el aviso). Devuelve si se mostró. */
  suspend fun notify(context: Context, force: Boolean = false): Boolean {
    val manager = NotificationManagerCompat.from(context)
    if (!manager.areNotificationsEnabled()) return false
    if (!force && todayCount(context) > 0) return false
    context.getSystemService(NotificationManager::class.java)
      .createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorio diario de registros", NotificationManager.IMPORTANCE_DEFAULT))
    // Tocar la notificación abre directamente el formulario de un gasto nuevo (igual que el + del widget).
    val add = PendingIntent.getActivity(
      context, 2,
      Intent(context, MainActivity::class.java).setAction(AuraWidget.ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    // "Más tarde" solo cierra el aviso.
    val later = PendingIntent.getBroadcast(
      context, 3, Intent(context, DailyLogLater::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val text = "Toma unos segundos: anota lo que gastaste o recibiste hoy y tu presupuesto seguirá al día."
    val notification = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_stat_aura)
      .setContentTitle("¿Ya registraste tus movimientos de hoy? 📝")
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(add)
      .addAction(0, "Registrar ahora", add)
      .addAction(0, "Más tarde", later)
      .setAutoCancel(true)
      .build()
    return try {
      manager.notify(NOTIFICATION_ID, notification)
      true
    } catch (e: SecurityException) {
      false // la persona quitó el permiso de notificaciones
    }
  }

  /** Revisión diaria a la hora elegida. */
  fun schedule(context: Context, hour: Int, minute: Int) {
    val now = LocalDateTime.now()
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    val request = PeriodicWorkRequestBuilder<DailyLogWorker>(1, TimeUnit.DAYS)
      .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK)
  }
}

/** Botón "Más tarde" del recordatorio diario: cierra la notificación. */
class DailyLogLater : android.content.BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    NotificationManagerCompat.from(context).cancel(DailyLog.NOTIFICATION_ID)
  }
}

class DailyLogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val s = Prefs(applicationContext).flow.first()
    if (s.dailyLog) DailyLog.notify(applicationContext)
    return Result.success()
  }
}

// ---------- Sincronización en segundo plano con la cuenta de Google ----------

object SyncWork {
  private const val WORK = "sincronizar"

  /** Además de sincronizar al usar la app, se revisa la nube cada 6 horas cuando hay internet. */
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
  }

  fun cancel(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(WORK)
  }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val prefs = Prefs(applicationContext)
    if (!FireSync.signedIn(applicationContext)) return Result.success()
    return try {
      FireSync.syncShared(applicationContext)
      // Un conflicto no se resuelve en segundo plano: queda para cuando la persona abra la app.
      if (FireSync.syncOwn(applicationContext, prefs) == FireSync.Outcome.DOWNLOADED) AuraWidget.refresh(applicationContext)
      Result.success()
    } catch (e: CloudException) {
      Result.retry()
    }
  }
}

// ---------- Widget de la pantalla de inicio ----------

class AuraWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        render(context, manager, ids)
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION_ADD = "com.aura.finanzas.ADD"

    /** Pide redibujar los widgets que haya en la pantalla de inicio. */
    fun refresh(context: Context) {
      val manager = AppWidgetManager.getInstance(context) ?: return
      val ids = manager.getAppWidgetIds(ComponentName(context, AuraWidget::class.java))
      if (ids.isEmpty()) return
      context.sendBroadcast(
        Intent(context, AuraWidget::class.java).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
      )
    }

    private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
      val state = loadState(AppDb.get(context).dao())
      val settings = Prefs(context).flow.first()
      val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-CO"))
      val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      val open = PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), flags)
      val add = PendingIntent.getActivity(
        context, 11,
        Intent(context, MainActivity::class.java).setAction(ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        flags,
      )
      // Con "ocultar cifras" activo en la app, el widget tampoco las muestra.
      val hidden = settings.hideAmounts
      val views = RemoteViews(context.packageName, R.layout.widget_aura).apply {
        setTextViewText(R.id.w_amount, if (hidden) "$ ••••••" else money(state.monthExpense))
        setTextViewText(R.id.w_detail, if (hidden) "Ingresos de $month ocultos" else "Ingresos de $month ${money(state.monthIncome)}")
        setOnClickPendingIntent(R.id.w_root, open)
        setOnClickPendingIntent(R.id.w_add, add)
      }
      manager.updateAppWidget(ids, views)
    }
  }
}
