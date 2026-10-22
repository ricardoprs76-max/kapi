package com.aura.finanzas.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.aura.finanzas.FinanceState
import com.aura.finanzas.currentValue
import com.aura.finanzas.ui.components.money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Informe del mes en PDF tamaño A4: resumen, gasto por categoría, presupuestos, patrimonio y movimientos. */
object PdfReport {
  private const val W = 595
  private const val H = 842
  private const val M = 40f
  private val es: Locale = Locale.forLanguageTag("es-CO")

  fun write(context: Context, uri: Uri, s: FinanceState) {
    val doc = PdfDocument()
    val accent = 0xFF5B2FD6.toInt()
    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF16121F.toInt() }
    val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = accent }
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF16121F.toInt() }
    val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF6B6680.toInt() }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var canvas: Canvas? = null
    var y = M

    fun newPage() {
      page?.let { doc.finishPage(it) }
      pageNumber++
      page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNumber).create()).also { canvas = it.canvas }
      y = M
      canvas?.drawText("Kapi · página $pageNumber", M, H - 20f, muted)
    }
    fun need(height: Float) { if (y + height > H - M - 10f) newPage() }
    fun right(text: String, x: Float, paint: Paint) = canvas?.drawText(text, x - paint.measureText(text), y, paint)
    fun fit(text: String, width: Float, paint: Paint): String {
      if (paint.measureText(text) <= width) return text
      val n = paint.breakText(text, true, width - paint.measureText("…"), null)
      return text.take(n) + "…"
    }
    fun section(text: String) {
      need(40f)
      y += 22f
      canvas?.drawText(text, M, y, heading)
      y += 6f
      fill.color = 0x335B2FD6
      canvas?.drawRect(M, y, W - M, y + 1f, fill)
      y += 14f
    }
    fun row(label: String, value: String, valueColor: Int = body.color) {
      need(16f)
      canvas?.drawText(fit(label, 330f, body), M, y, body)
      val old = body.color
      body.color = valueColor
      right(value, W - M, body)
      body.color = old
      y += 16f
    }

    val month = LocalDate.now()
    val monthName = month.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", es))
    val green = 0xFF15803D.toInt()
    val red = 0xFFC62828.toInt()

    newPage()
    y += 14f
    canvas?.drawText("Informe financiero", M, y, title)
    y += 18f
    canvas?.drawText("${monthName.replaceFirstChar { it.uppercase() }} · generado el ${month.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", es))}", M, y, muted)

    section("Resumen")
    row("Patrimonio neto", money(s.netWorth))
    row("Ingresos del mes", money(s.monthIncome), green)
    row("Gastos del mes", money(s.monthExpense), red)
    val balance = s.monthIncome - s.monthExpense
    row("Balance del mes", money(balance, sign = true), if (balance < 0) red else green)

    if (s.monthByCategory.isNotEmpty()) {
      section("Gasto por categoría")
      val top = s.monthByCategory.first().amount
      for (c in s.monthByCategory) {
        need(18f)
        canvas?.drawText(fit(c.category?.name ?: "Sin categoría", 150f, body), M, y, body)
        fill.color = accent
        canvas?.drawRoundRect(M + 160f, y - 8f, M + 160f + (220f * (c.amount / top)).toFloat().coerceAtLeast(2f), y, 3f, 3f, fill)
        right("${money(c.amount)}  ·  ${(c.amount / s.monthExpense * 100).toInt()} %", W - M, body)
        y += 18f
      }
    }

    val budgets = s.budgets.filter { it.monthlyLimit > 0 && it.accountId == ALL_ACCOUNTS }
    if (budgets.isNotEmpty()) {
      section("Presupuestos")
      for (b in budgets) {
        val spent = if (b.isTotal) s.monthExpense else s.monthSpent[b.categoryId] ?: 0.0
        val pct = (spent / b.monthlyLimit * 100).toInt()
        row("${if (b.isTotal) "Todos los gastos" else s.categoryById[b.categoryId]?.name ?: "Categoría"} — ${money(spent)} de ${money(b.monthlyLimit)}", "$pct %", if (pct >= 100) red else if (pct >= 70) 0xFFB7791F.toInt() else green)
      }
    }

    section("Cuentas")
    for (a in s.accounts) row(a.name, money(s.balances[a.id] ?: 0.0))
    if (s.assets.isNotEmpty()) {
      section("Activos")
      for (a in s.assets) row("${a.name} (${if (a.kind == "CDT") "CDT" else a.kind.lowercase().replaceFirstChar { it.uppercase() }})", money(a.currentValue()))
    }
    if (s.debts.isNotEmpty()) {
      section("Deudas")
      for (d in s.debts) row(d.name, money(d.balance), red)
    }

    val zone = ZoneId.systemDefault()
    val monthStart = month.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val txs = s.txs.filter { it.date >= monthStart }
    if (txs.isNotEmpty()) {
      section("Movimientos del mes (${txs.size})")
      val day = DateTimeFormatter.ofPattern("d MMM", es)
      for (t in txs) {
        need(15f)
        val what = when (t.type) {
          TxType.TRANSFER -> "Transferencia → ${s.accountById[t.toAccountId]?.name ?: "?"}"
          else -> s.categoryById[t.categoryId]?.name ?: "Sin categoría"
        } + if (t.note.isNotBlank()) " · ${t.note}" else ""
        canvas?.drawText(Instant.ofEpochMilli(t.date).atZone(zone).format(day), M, y, muted)
        canvas?.drawText(fit(what, 250f, body), M + 50f, y, body)
        canvas?.drawText(fit(s.accountById[t.accountId]?.name ?: "", 100f, muted), M + 310f, y, muted)
        val old = body.color
        body.color = when (t.type) { TxType.INCOME -> green; TxType.EXPENSE -> red; else -> old }
        right(when (t.type) { TxType.INCOME -> money(t.amount, sign = true); TxType.EXPENSE -> money(-t.amount); else -> money(t.amount) }, W - M, body)
        body.color = old
        y += 15f
      }
    }

    page?.let { doc.finishPage(it) }
    try {
      context.contentResolver.openOutputStream(uri, "wt")?.use { doc.writeTo(it) } ?: error("no se pudo abrir el archivo")
    } finally {
      doc.close()
    }
  }
}
