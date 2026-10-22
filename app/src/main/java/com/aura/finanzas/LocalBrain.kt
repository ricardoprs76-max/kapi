package com.aura.finanzas

import com.aura.finanzas.data.Category
import com.aura.finanzas.data.Ocr
import com.aura.finanzas.data.OcrPiece
import com.aura.finanzas.data.Tx
import com.aura.finanzas.data.TxType
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.pow

/** Un producto leído de una factura, o un movimiento de un dictado. [doubt]: el valor no se leyó con claridad y hay que revisarlo. */
/**
 * Un producto leído de una factura. [guessed]: su categoría salió de una suposición general (no de un movimiento
 * anterior con el mismo producto), así que la IA puede afinarla. [byAi]: la categoría la eligió la IA.
 */
class ReceiptItem(
  val name: String, val amount: Double, val categoryId: Long? = null, val doubt: Boolean = false, val type: String = TxType.EXPENSE,
  val guessed: Boolean = false, val byAi: Boolean = false,
)

/**
 * Factura leída producto por producto ([total] es el total impreso, si se encontró), o varios movimientos
 * dictados en una sola frase ([voice]).
 */
class ReceiptScan(
  val merchant: String, val date: Long, val accountId: Long, val items: List<ReceiptItem>, val total: Double?, val voice: Boolean = false,
  /** Foto de la que salió (para volver a leerla) y si ya se leyó con el modo cuidadoso. */
  val photo: android.net.Uri? = null, val careful: Boolean = false,
)

/**
 * "Cerebro" local: interpreta frases y recibos, y arma análisis y consejos con las cifras guardadas, todo en
 * el teléfono. Es lo que hace funcionar las funciones inteligentes sin clave de IA o cuando la IA falla.
 */
object LocalBrain {
  private fun m(v: Double) = "$" + "%,.0f".format(java.util.Locale.US, v).replace(',', '.')

  fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

  private val numberWords = mapOf(
    "un" to "1", "una" to "1", "dos" to "2", "tres" to "3", "cuatro" to "4", "cinco" to "5", "seis" to "6", "siete" to "7",
    "ocho" to "8", "nueve" to "9", "diez" to "10", "quince" to "15", "veinte" to "20", "treinta" to "30", "cuarenta" to "40",
    "cincuenta" to "50", "cien" to "100", "medio" to "0.5",
  )
  private val wordBeforeUnit = Regex("\\b(${numberWords.keys.joinToString("|")})\\s+(millon|millones|mil|palo|palos|luca|lucas)\\b")
  private val amountRegex = Regex("(\\d++(?:[.,]\\d++)*+)\\s*(millones|millon|palos|palo|mil|lucas|luca|k\\b|m\\b)?(?!\\s*(?:cuotas|cuota|meses|mes|anos|ano|%|dias|veces|por ciento))")
  private val grouped = Regex("\\d{1,3}(?:[.,]\\d{3})+")

  /** Valores de dinero que aparecen en [text]: "25 mil", "8 millones", "1,5 millones", "26.000", "20 lucas"… */
  fun amounts(text: String): List<Pair<Double, IntRange>> {
    val t = wordBeforeUnit.replace(norm(text)) { numberWords.getValue(it.groupValues[1]) + " " + it.groupValues[2] }
    return amountRegex.findAll(t).mapNotNull { match ->
      val n = match.groupValues[1].trimEnd('.', ',')
      val unit = match.groupValues[2]
      val value = if (unit.isNotEmpty()) {
        val base = (if (grouped.matches(n)) n.replace(".", "").replace(",", "") else n.replace(',', '.')).toDoubleOrNull() ?: return@mapNotNull null
        base * (if (unit.startsWith("mil") && unit != "millon" && unit != "millones" || unit.startsWith("luca") || unit == "k") 1_000.0 else 1_000_000.0)
      } else plain(n) ?: return@mapNotNull null
      value to match.range
    }.toList()
  }

  /** Número escrito sin unidad: quita separadores de miles y decimales. */
  private fun plain(n: String): Double? {
    val withDecimals = Regex("(\\d{1,3}(?:[.,]\\d{3})+)[.,]\\d{1,2}").matchEntire(n)
    return when {
      withDecimals != null -> withDecimals.groupValues[1].filter(Char::isDigit).toDoubleOrNull()
      grouped.matches(n) -> n.filter(Char::isDigit).toDoubleOrNull()
      Regex("\\d+[.,]\\d{1,2}").matches(n) -> n.substringBefore('.').substringBefore(',').toDoubleOrNull()
      else -> n.filter(Char::isDigit).toDoubleOrNull()
    }
  }

  private val keywords = listOf(
    "alimenta" to listOf("almuerzo", "comida", "desayuno", "cena", "restaurante", "cafe", "pan", "hamburguesa", "pizza", "pollo", "empanada", "domicilio", "rappi", "helado", "corrientazo"),
    "mercado" to listOf("mercado", "supermercado", "exito", "d1", "ara", "olimpica", "jumbo", "tienda", "carulla", "fruver", "isimo", "makro"),
    "transporte" to listOf("taxi", "uber", "bus", "gasolina", "transmilenio", "pasaje", "peaje", "parqueadero", "didi", "moto", "metro", "sitp", "tanqueada"),
    "vivienda" to listOf("arriendo", "administracion", "hipoteca", "predial"),
    "servicio" to listOf("luz", "agua", "gas", "internet", "celular", "plan", "energia", "recibo", "claro", "movistar", "tigo", "acueducto"),
    "salud" to listOf("drogueria", "medicina", "medico", "eps", "farmacia", "cita", "odontolog", "optica"),
    "entreten" to listOf("cine", "netflix", "spotify", "fiesta", "bar", "cerveza", "concierto", "disney", "juego"),
    "educa" to listOf("colegio", "universidad", "curso", "libro", "matricula", "pension"),
    "viaje" to listOf("hotel", "vuelo", "tiquete", "avianca", "latam", "airbnb"),
    "regalo" to listOf("regalo", "cumpleanos"),
    "tecno" to listOf("computador", "audifonos", "cargador", "tablet", "telefono"),
    "salario" to listOf("salario", "sueldo", "nomina", "quincena", "pago"),
    "negocio" to listOf("venta", "vendi", "cliente", "negocio"),
    "inversion" to listOf("intereses", "dividendo", "rendimiento", "cdt"),
  )

  /** Categoría de un movimiento anterior cuya nota comparte alguna palabra con [text]; null si no hay ninguno. */
  fun historyCategory(text: String, type: String, s: FinanceState): Category? {
    val wordsIn = norm(text).split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()
    if (wordsIn.isEmpty()) return null
    return s.txs.asSequence().filter { it.type == type && it.note.isNotBlank() && it.categoryId != null }.take(400)
      .firstOrNull { tx -> norm(tx.note).split(Regex("[^a-z0-9]+")).any { it in wordsIn } }
      ?.let { tx -> s.categories.firstOrNull { it.kind == type && it.id == tx.categoryId } }
  }

  /** Categoría más probable: primero por movimientos anteriores con palabras en común, luego por palabras clave. */
  fun guessCategory(text: String, type: String, s: FinanceState): Category? {
    val n = norm(text)
    val options = s.categories.filter { it.kind == type }
    historyCategory(text, type, s)?.let { return it }
    options.firstOrNull { c -> norm(c.name).let { it.length >= 4 && n.contains(it) } }?.let { return it }
    for ((key, list) in keywords) {
      if (list.any { Regex("\\b$it").containsMatchIn(n) }) options.firstOrNull { norm(it.name).contains(key) }?.let { return it }
    }
    return options.firstOrNull { norm(it.name).startsWith("otro") } ?: options.firstOrNull()
  }

  private val incomeWords = Regex("\\b(recibi|me pagaron|me pago|ingreso|salario|sueldo|gane|me consignaron|vendi|cobre|me dieron|quincena|nomina)\\b")

  /** Convierte una frase como "Almuerzo 25 mil con Nequi" en un movimiento; null si no trae valor o no hay cuentas. */
  fun parsePhrase(text: String, s: FinanceState): Tx? {
    val n = norm(text)
    val found = amounts(text).maxByOrNull { it.first } ?: return null
    // "almuerzo 25" casi siempre quiere decir 25 mil pesos.
    val amount = if (found.first < 1_000) found.first * 1_000 else found.first
    val type = if (incomeWords.containsMatchIn(n)) TxType.INCOME else TxType.EXPENSE
    val account = s.accounts.firstOrNull { a -> norm(a.name).let { it.length >= 3 && n.contains(it) } } ?: s.accounts.firstOrNull() ?: return null
    val note = text.trim().replaceFirstChar { it.uppercase() }.take(80)
    return Tx(type = type, amount = amount, accountId = account.id, categoryId = guessCategory(text, type, s)?.id, note = note, source = "VOZ")
  }

  private val connector = Regex("(?i)\\s*(?:[;]|,(?!\\d)|\\.(?=\\s)|\\s(?:y|e|más|mas|también|tambien|luego|después|despues|además|ademas)\\s)\\s*")

  /**
   * Separa un dictado con varios movimientos ("almuerzo 25 mil, taxi 12 mil y me pagaron 300 mil") en una frase
   * por movimiento. Primero corta por comas y conectores; un trozo sin valor se une a su vecino. Si no hay
   * conectores ("almuerzo 25000 taxi 12000"), corta en cada valor.
   */
  fun splitPhrases(text: String): List<String> {
    val parts = connector.split(text.trim()).map { it.trim() }.filter { it.isNotEmpty() }
    val merged = ArrayList<String>()
    var carry = ""
    for (p in parts) {
      val piece = if (carry.isEmpty()) p else "$carry y $p"
      if (amounts(piece).isEmpty()) {
        if (merged.isEmpty()) carry = piece else merged[merged.lastIndex] = merged.last() + " y " + p
      } else {
        merged += piece
        carry = ""
      }
    }
    if (merged.isEmpty()) return listOf(text.trim())
    return merged.flatMap(::splitByAmounts)
  }

  /** "almuerzo 25000 taxi 12000" → dos frases; también "25 mil de almuerzo 12 mil de taxi". */
  private fun splitByAmounts(piece: String): List<String> {
    val n = norm(piece)
    if (n.length != piece.length) return listOf(piece)
    val found = amountRegex.findAll(n).filter { m -> m.groupValues[2].isNotEmpty() || (plain(m.groupValues[1].trimEnd('.', ',')) ?: 0.0) >= 100 }.map { it.range }.toList()
    if (found.size < 2) return listOf(piece)
    // Si después del último valor aún hay texto, cada movimiento empieza con su valor; si no, termina con él.
    val valueFirst = piece.substring(found.last().last + 1).count(Char::isLetter) >= 3
    val cuts = if (valueFirst) listOf(0) + found.drop(1).map { it.first } + piece.length
    else listOf(0) + found.dropLast(1).map { it.last + 1 } + piece.length
    val out = cuts.zipWithNext { a, b -> piece.substring(a, b).trim() }.filter { it.isNotEmpty() }
    return if (out.all { it.count(Char::isLetter) >= 2 }) out else listOf(piece)
  }

  /** Varios movimientos dictados en una sola frase; null si la frase trae uno solo (o ninguno). */
  fun parsePhrases(text: String, s: FinanceState): ReceiptScan? {
    val parts = splitPhrases(text)
    if (parts.size < 2) return null
    // La cuenta nombrada en cualquier parte de la frase vale para todos los movimientos.
    val named = norm(text).let { n -> s.accounts.firstOrNull { a -> norm(a.name).let { it.length >= 3 && n.contains(it) } } }
    val txs = parts.mapNotNull { parsePhrase(it, s) }
    if (txs.size < 2) return null
    return ReceiptScan(
      "", System.currentTimeMillis(), (named ?: s.accounts.first()).id,
      txs.map { ReceiptItem(it.note, it.amount, it.categoryId, type = it.type) }, null, voice = true,
    )
  }

  private val receiptAmount = Regex("\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{2})?|\\d{4,7}")
  private val skipLine = Regex("nit|tel|cel\\b|factura|resoluc|autoriz|cufe|fecha|codigo|cajero|caja\\b|nro|numero|no\\.")
  private val totalLine = Regex("total|a pagar|valor pagado|neto a|vr\\.? ?pago")
  private val notTotal = Regex("sub ?total|items|articulos|ahorro|iva|impuesto|descuento|cambio|recibido|efectivo")

  /** Arma un gasto a partir del texto leído de un recibo: total, fecha y comercio. Valor 0 si no encontró el total. */
  fun parseReceipt(ocr: String, s: FinanceState): Tx? {
    val account = s.accounts.firstOrNull() ?: return null
    val lines = ocr.lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun values(line: String) = receiptAmount.findAll(line).mapNotNull { plain(it.value) }.filter { it in 100.0..50_000_000.0 }.toList()
    val fromTotal = lines.withIndex().filter { (_, l) -> norm(l).let { totalLine.containsMatchIn(it) && !notTotal.containsMatchIn(it) } }
      .flatMap { (i, l) -> values(l).ifEmpty { lines.getOrNull(i + 1)?.let(::values) ?: emptyList() } }
    val amount = fromTotal.maxOrNull()
      ?: lines.filter { !skipLine.containsMatchIn(norm(it)) }.flatMap(::values).maxOrNull() ?: 0.0
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val date = Regex("(\\d{1,2}) ?[/.-] ?(\\d{1,2}) ?[/.-] ?(\\d{4}|\\d{2})(?!\\d)|(\\d{4})-(\\d{2})-(\\d{2})").findAll(ocr.replace('O', '0').replace('o', '0')).mapNotNull { match ->
      runCatching {
        val g = match.groupValues
        if (g[4].isNotEmpty()) LocalDate.of(g[4].toInt(), g[5].toInt(), g[6].toInt())
        else LocalDate.of(g[3].toInt().let { if (it < 100) 2000 + it else it }, g[2].toInt(), g[1].toInt())
      }.getOrNull()
    }.firstOrNull { !it.isAfter(today) && it.isAfter(today.minusYears(2)) }
    val merchant = lines.firstOrNull { l -> l.count(Char::isLetter) >= 3 && l.count(Char::isLetter) > l.count(Char::isDigit) }?.take(40) ?: "Recibo"
    return Tx(
      type = TxType.EXPENSE, amount = amount, accountId = account.id, categoryId = guessCategory(ocr.take(600), TxType.EXPENSE, s)?.id,
      note = merchant, date = (date ?: today).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), source = "RECIBO",
    )
  }

  // Renglón de producto de un supermercado: "3 7704269113381 DYPACK BROWN" (consecutivo, código de barras, nombre).
  private val itemHeader = Regex("^(?:\\d{1,3}\\s+)?\\d{10,14}\\s*(\\D.*)$")
  // Valor del renglón seguido de la letra del impuesto: "11.230 G".
  private val taxTotal = Regex("^(\\d[\\d.,/ ]*?)\\s?[A-Z]$")
  // Cantidad y unidad, a veces con el precio unitario al final: "3 UN X", "0,476 KGM", "1 UN X 21.990".
  private val qtyLine = Regex("^(\\d+(?:[.,]\\s?\\d+)?)\\s*(KGM|KG|UND|UN|R1|EA|GR|LT|ML|U)\\s*X?(?:\\s+(.*))?$", RegexOption.IGNORE_CASE)
  private val bareNumber = Regex("^\\d[\\d.,/ ]*$")
  // Recibo sencillo: nombre y valor en el mismo renglón, "Pollo entero 18.500".
  private val rowItem = Regex("^(.*?\\p{L}{3,}.*?)\\s+\\$?\\s?(\\d{1,3}(?:[.,]\\s?\\d{3})+(?:[.,]\\d{2})?|\\d{3,7}|\\d{1,3}[.,]\\d{2})(?:\\s?[A-Z])?$")
  private val notItem = Regex("total|\\biva\\b|impuesto|impoconsumo|descuento|ahorro|cambio|recibido|efectivo|tarjeta|debito|credito|propina|\\bbase\\b|\\bpago|saldo|puntos|vuelto|redondeo|\\bnit\\b|\\btel\\b|fecha|factura|cufe|resoluc|cajero|autoriz")

  /** Valor leído con ruido ("9, 990", "4/710", "9,,130"): se queda con los dígitos y descarta los centavos. */
  private fun looseAmount(raw: String): Double? {
    val t = raw.replace(" ", "")
    val whole = Regex("^(\\d{1,3}(?:[.,]\\d{3})+)[.,]\\d{2}$").matchEntire(t)?.groupValues?.get(1) ?: t
    return whole.filter(Char::isDigit).toDoubleOrNull()
  }

  // Factura por columnas "Cant.  Vr.Und.  Total  %": cada producto empieza con consecutivo, código y descripción,
  // y sus cifras son "1.00 7500 7500 19": cantidad con decimales, valor unitario, total y tarifa de IVA.
  private val numberToken = Regex("^[\\dOo][\\dOo.,]*$")
  private val decimalQty = Regex("^\\d+[.,]\\d{2,3}$")
  // Cantidad con una letra colada al leer ("0.c0").
  private val noisyQty = Regex("^\\d[.,][\\dA-Za-z]{2}$")
  private val taxRates = setOf(0.0, 5.0, 8.0, 19.0)

  private class Columns(val lead: String, val amount: Double, val qty: Double?, val doubt: Boolean)

  /**
   * Descripción de un renglón que empieza un producto ("2 7622201717568 GALLETAS CLUB…"), o null si no lo es.
   * Tolera lo que suele pasar al leer la foto: el consecutivo pegado al código, el código partido en dos o con
   * un carácter mal leído.
   */
  private fun startName(line: String): String? {
    val tokens = line.trim().split(Regex("\\s+"))
    var digits = 0
    var i = 0
    while (i < tokens.size && tokens[i].count(Char::isDigit) >= maxOf(1, tokens[i].length - 2) && !tokens[i].contains('.') && !tokens[i].contains(',')) {
      digits += tokens[i].count(Char::isDigit)
      i++
    }
    if (i == 0 || digits < 5 || i >= tokens.size || !tokens[i].first().isLetter()) return null
    return tokens.drop(i).joinToString(" ")
  }

  /**
   * Separa un renglón en su texto y sus cifras finales (cantidad, valor unitario, total y % de IVA).
   * El total ya incluye el IVA: la tarifa de la última columna se descarta, no se suma. Null si el renglón no trae cifras.
   */
  private fun columns(line: String): Columns? {
    val tokens = line.trim().split(Regex("\\s+")).map { it.trimStart('/', '|') }.filter { it.isNotEmpty() }
    val nums = tokens.takeLastWhile { numberToken.matches(it) && it.any(Char::isDigit) }.map { it.replace('O', '0').replace('o', '0') }.toMutableList()
    // La cantidad puede haber quedado con una letra ("0.c0"): cuenta como cantidad, pero no como cifra.
    val before = tokens.getOrNull(tokens.size - nums.size - 1)
    val noisy = before != null && noisyQty.matches(before) && !decimalQty.matches(before)
    val strict = nums.size >= 3 && nums.take(nums.size - 2).any { decimalQty.matches(it) }
    val loose = nums.size >= 3 || (nums.size == 2 && (noisy || (nums.last().length <= 2 && nums.last().toDoubleOrNull() in taxRates)))
    if (!strict && !loose) return null
    // Del texto se quitan las cifras mal leídas que quedaron pegadas al final ("1.00 143C0").
    val lead = tokens.dropLast(nums.size + if (noisy) 1 else 0).dropLastWhile { decimalQty.matches(it) || Regex("^[0-9]+[A-Za-z][0-9]+$").matches(it) }.joinToString(" ")
    // Última columna: la tarifa de IVA (0, 5, 19…). Solo se quita si aún queda un total antes de ella.
    val hadRate = nums.size >= 2 && nums.last().length <= 2 && nums.last().toDoubleOrNull() in taxRates && nums.last() != "00"
    if (hadRate) nums.removeAt(nums.lastIndex)
    var total = looseAmount(nums.last()) ?: return null
    val unit = nums.getOrNull(nums.size - 2)?.takeIf { !decimalQty.matches(it) }?.let(::looseAmount)
    val qty = if (noisy) before!!.first().digitToInt().toDouble() else nums.firstOrNull { decimalQty.matches(it) }?.replace(',', '.')?.toDoubleOrNull()
    var doubt = false
    val expected = if (unit != null) Math.round((qty ?: 1.0) * unit).toDouble() else null
    if (expected != null && abs(expected - total) > maxOf(1.0, expected * 0.01)) {
      // "835019": el total y la tarifa de IVA quedaron pegados al leer. Se le quita la tarifa del final si así
      // coincide (con poca diferencia) con cantidad × valor unitario.
      val text = total.toLong().toString()
      val stripped = if (hadRate) null else listOf("19", "5", "8", "0").filter { text.endsWith(it) && text.length > it.length }
        .mapNotNull { text.dropLast(it.length).toDoubleOrNull() }.filter { expected > 0 && abs(it - expected) <= expected * 0.05 }.minByOrNull { abs(it - expected) }
      if (stripped != null) total = stripped else doubt = true
    }
    return Columns(lead, total, qty, doubt)
  }

  /** Productos de una factura por columnas; lista vacía si la factura no tiene ese formato. */
  private fun columnItems(lines: List<String>): List<Pair<String, Columns>> {
    if (lines.count { l -> columns(l)?.qty != null } < 2 || lines.count { startName(it) != null } < 2) return emptyList()
    val out = ArrayList<Pair<String, Columns>>()
    var name: StringBuilder? = null
    for (l in lines) {
      val start = startName(l)
      val text = start ?: l
      if (start != null) {
        // El producto anterior se quedó sin cifras (no se leyeron): se muestra para que la persona escriba el valor.
        name?.takeIf { it.count(Char::isLetter) >= 3 && !isDiscount(it) }?.let { out += it.toString() to Columns("", 0.0, null, true) }
        name = StringBuilder()
      }
      val current = name ?: continue
      if (start == null && norm(l).let { totalLine.containsMatchIn(it) || it.contains("tems") }) { name = null; continue }
      val c = columns(text)
      val lead = (c?.lead ?: text).trim()
      if (lead.isNotEmpty()) current.append(if (current.isEmpty()) "" else " ").append(lead)
      if (c != null) {
        // Un renglón de descuento ("Dcto. 10 %", cantidad 0) no es un producto: el descuento ya viene aplicado en el siguiente.
        if (c.amount > 0 && c.qty != 0.0 && !isDiscount(current) && current.count(Char::isLetter) >= 3) out += current.toString() to c
        name = null
      }
    }
    return out
  }

  private fun isDiscount(name: CharSequence) = norm(name.toString()).let { it.startsWith("dcto") || it.startsWith("descuento") || it.startsWith("dto") }

  private fun titleCase(s: String) = s.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

  /**
   * Lee una factura producto por producto a partir de los trozos de texto de la foto. Entiende dos formatos:
   * el de supermercado (código de barras y nombre en un renglón, cantidad × precio en el siguiente y el valor
   * en una columna a la derecha) y el recibo sencillo (nombre y valor en el mismo renglón).
   * Devuelve null si no hay cuentas o si encontró menos de dos productos.
   */
  fun parseReceiptItems(pieces: List<OcrPiece>, s: FinanceState): ReceiptScan? {
    val account = s.accounts.firstOrNull() ?: return null
    val text = Ocr.text(pieces)
    val base = parseReceipt(text, s) ?: return null
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val market = s.categories.firstOrNull { it.kind == TxType.EXPENSE && norm(it.name).contains("mercado") }
    val general = guessCategory(text.take(600), TxType.EXPENSE, s)
    fun item(name: String, amount: Double, doubt: Boolean, fallback: Category?): ReceiptItem {
      // Lo que la persona ya clasificó antes manda; lo demás es una suposición que la IA puede mejorar.
      val known = historyCategory(name, TxType.EXPENSE, s)
      return ReceiptItem(name.take(80), amount, (known ?: fallback)?.id, doubt, guessed = known == null)
    }

    class Head(val p: OcrPiece, val name: String)
    val heads = pieces.mapNotNull { p -> itemHeader.matchEntire(p.text.trim())?.let { Head(p, it.groupValues[1].trim()) } }.sortedBy { it.p.y }
    var items = emptyList<ReceiptItem>()
    var merchant = base.note
    val byColumns = columnItems(lines)
    if (byColumns.size >= 2) {
      items = byColumns.map { (name, c) ->
        val count = c.qty?.takeIf { it > 1.0 }?.let { " x" + if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') } ?: ""
        item(titleCase(name) + count, c.amount, c.doubt, market ?: general)
      }
      merchant = lines.takeWhile { startName(it) == null }
        .firstOrNull { l -> l.count(Char::isLetter) >= 4 && l.count(Char::isLetter) > l.count(Char::isDigit) && !skipLine.containsMatchIn(norm(l)) && !norm(l).contains("cant") }?.take(40) ?: ""
    } else if (heads.size >= 2) {
      // Distancia vertical típica entre un producto y el siguiente.
      val step = heads.zipWithNext { a, b -> b.p.y - a.p.y }.sorted().let { it[it.size / 2] }
      val headPieces = heads.map { it.p }.toSet()
      val rest = pieces.filter { it !in headPieces }
      val totals = rest.mapNotNull { p -> taxTotal.matchEntire(p.text.trim())?.let { m -> looseAmount(m.groupValues[1])?.let { p to it } } }
      val totalPieces = totals.map { it.first }.toSet()
      // Cada valor de la columna derecha va con el producto que tiene a su misma altura, empezando por los más claros.
      val totalOf = HashMap<Head, Double>()
      totals.sortedBy { (p, _) -> heads.minOf { abs(it.p.y - p.y) } }.forEach { (p, v) ->
        val h = heads.filter { it !in totalOf && p.x > it.p.x }.minByOrNull { abs(it.p.y - p.y) }
        if (h != null && abs(h.p.y - p.y) < step * 0.45f) totalOf[h] = v
      }
      items = heads.map { h ->
        val near = rest.filter { it !in totalPieces && it.x > h.p.x }
        // Pedazos del nombre que quedaron sueltos en el mismo renglón ("CER", "X6").
        val extra = near.filter { p ->
          val t = p.text.trim()
          abs(p.y - h.p.y) < step * 0.25f && !bareNumber.matches(t) && qtyLine.matchEntire(t) == null && !t.equals("X", true) && t.any(Char::isLetterOrDigit)
        }.sortedBy { it.x }.joinToString(" ") { it.text.trim() }
        // El renglón de cantidad × precio queda a media distancia del siguiente producto.
        val below = near.filter { p -> abs(p.y - (h.p.y + step / 2)) < step * 0.4f }
        val q = below.firstNotNullOfOrNull { qtyLine.matchEntire(it.text.trim()) }
        val qty = q?.groupValues?.get(1)?.replace(" ", "")?.replace(',', '.')?.toDoubleOrNull()
        val unit = q?.groupValues?.get(3)?.takeIf { bareNumber.matches(it.trim()) }?.let(::looseAmount)
          ?: below.filter { bareNumber.matches(it.text.trim()) && it.x > h.p.x + step }.maxByOrNull { it.x }?.let { looseAmount(it.text) }
        val expected = if (qty != null && unit != null) Math.round(qty * unit).toDouble() else unit
        val total = totalOf[h]
        val amount: Double
        val doubt: Boolean
        when {
          // Al valor leído le falta más de la mitad frente a cantidad × precio: casi seguro se perdió un dígito.
          total != null && expected != null && total < expected * 0.5 -> { amount = expected; doubt = true }
          total != null -> { amount = total; doubt = expected != null && total > expected * 1.05 }
          expected != null -> { amount = expected; doubt = false }
          else -> { amount = 0.0; doubt = true }
        }
        val count = qty?.takeIf { it != 1.0 }?.let { " x" + if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',') + " " + q!!.groupValues[2].lowercase() } ?: ""
        item(titleCase(listOf(h.name, extra).filter { it.isNotBlank() }.joinToString(" ")) + count, amount, doubt, market ?: general)
      }
      // El comercio solo se toma si aparece arriba del primer producto; en una foto parcial no se ve.
      merchant = Ocr.rows(pieces).filter { r -> r.first().y < heads.first().p.y - step * 0.3f }.map { r -> r.joinToString(" ") { it.text } }
        .firstOrNull { l -> l.count(Char::isLetter) >= 4 && l.count(Char::isLetter) > l.count(Char::isDigit) && qtyLine.matchEntire(l.trim()) == null }?.take(40) ?: ""
    }
    if (items.size < 2) {
      items = lines.mapNotNull { l ->
        if (notItem.containsMatchIn(norm(l).replace('0', 'o'))) return@mapNotNull null
        val m = rowItem.matchEntire(l) ?: return@mapNotNull null
        // "18.50" en un recibo en pesos casi siempre es "18.500" con un dígito sin leer: se propone y se marca para revisar.
        val cut = Regex("\\d{1,3}[.,]\\d{2}").matches(m.groupValues[2])
        val v = (if (cut) m.groupValues[2].filter(Char::isDigit).toDoubleOrNull()?.times(10) else looseAmount(m.groupValues[2]))?.takeIf { it in 50.0..50_000_000.0 } ?: return@mapNotNull null
        val name = m.groupValues[1].replace(Regex("^[\\d\\s.,*#-]+"), "").trim()
        if (name.count(Char::isLetter) < 3) null else item(name.replaceFirstChar { it.uppercase() }, v, cut, general)
      }
    }
    if (items.size < 2) return null
    // "ITEMS 18  TOTAL 99,253": el conteo de productos no impide leer el total del mismo renglón.
    val printed = lines.map { Regex("(?i)items\\s*\\d*").replace(it, "") }.withIndex().filter { (_, l) -> norm(l).let { totalLine.containsMatchIn(it) && !notTotal.containsMatchIn(it) } }
      .flatMap { (i, l) -> receiptAmount.findAll(l).mapNotNull { plain(it.value) }.toList().ifEmpty { lines.getOrNull(i + 1)?.let { n -> receiptAmount.findAll(n).mapNotNull { plain(it.value) }.toList() } ?: emptyList() } }
      .filter { it in 100.0..50_000_000.0 }.maxOrNull()
    return ReceiptScan(merchant, base.date, account.id, items, printed)
  }

  // ---------- Análisis y consejos ----------

  private class Stats(val avgIncome: Double, val avgExpense: Double, val months: Int)

  /** Promedio mensual de ingresos y gastos en los últimos 3 meses calendario que tengan registros. */
  private fun stats(s: FinanceState): Stats {
    val zone = ZoneId.systemDefault()
    val now = YearMonth.now()
    val window = s.txs.filter { it.type != TxType.TRANSFER }.groupBy { YearMonth.from(Instant.ofEpochMilli(it.date).atZone(zone)) }
      .filterKeys { !it.isBefore(now.minusMonths(2)) && !it.isAfter(now) }
    val months = window.size.coerceAtLeast(1)
    return Stats(
      window.values.sumOf { l -> l.filter { it.type == TxType.INCOME }.sumOf { it.amount } } / months,
      window.values.sumOf { l -> l.filter { it.type == TxType.EXPENSE }.sumOf { it.amount } } / months, window.size,
    )
  }

  private fun tips(s: FinanceState, scope: Int): List<String> {
    val st = stats(s)
    val out = ArrayList<String>()
    val top = s.monthByCategory.firstOrNull()
    val spending = ArrayList<String>()
    val worth = ArrayList<String>()

    // Gasto e ingreso
    if (st.months == 0) {
      spending += "Aún no hay movimientos recientes: registra gastos e ingresos 2 semanas y verás recomendaciones con tus cifras."
    } else {
      if (st.avgIncome > 0 && st.avgExpense > st.avgIncome) {
        spending += "Gastas más de lo que entra (${m(st.avgExpense)} vs. ${m(st.avgIncome)} al mes): recorta ${m(st.avgExpense - st.avgIncome)} al mes" + (top?.let { ", empezando por ${it.category?.name ?: "tu mayor categoría"}." } ?: ".")
      } else if (st.avgIncome > 0) {
        val rate = (st.avgIncome - st.avgExpense) / st.avgIncome
        spending += if (rate < 0.2) "Ahorras el ${(rate * 100).toInt()} % (${m(st.avgIncome - st.avgExpense)} al mes); para llegar al 20 % separa ${m(st.avgIncome * 0.2 - (st.avgIncome - st.avgExpense))} más el día de pago."
        else "Vas bien: ahorras el ${(rate * 100).toInt()} % (${m(st.avgIncome - st.avgExpense)} al mes). Pásalo a una meta o CDT el día de pago."
      } else {
        spending += "Gastas ${m(st.avgExpense)} al mes pero no hay ingresos registrados: anótalos para saber cuánto te queda libre."
      }
      if (top != null && s.monthExpense > 0) {
        spending += "${top.category?.name ?: "Sin categoría"} es tu mayor gasto (${m(top.amount)}, ${(top.amount / s.monthExpense * 100).toInt()} %): ponle un tope de ${m(top.amount * 0.9)} en Presupuesto."
      }
      s.accounts.mapNotNull { a ->
        val avg = s.avg3[a.id]?.expense ?: 0.0
        val month = s.accountMonthSpent[a.id] ?: 0.0
        if (avg > 0 && month > avg * 1.15) Triple(a.name, month, avg) else null
      }.maxByOrNull { it.second / it.third }?.let { (name, month, avg) ->
        spending += "$name va ${((month / avg - 1) * 100).toInt()} % sobre su promedio (${m(month)} vs. ${m(avg)}): frena esa cuenta el resto del mes."
      }
      antExpenses(s).firstOrNull()?.let { a ->
        spending += "Gasto hormiga: ${a.label} (${a.count} veces, ${m(a.total)} en 30 días). A la mitad liberas ${m(a.total * 6)} al año."
      }
      s.budgets.firstOrNull { b -> b.isTotal && b.monthlyLimit > 0 && s.accountById[b.accountId] != null && (s.accountMonthSpent[b.accountId] ?: 0.0) > b.monthlyLimit }?.let { b ->
        spending += "${s.accountById[b.accountId]?.name} superó su tope de ${m(b.monthlyLimit)} (lleva ${m(s.accountMonthSpent[b.accountId] ?: 0.0)}): no más gastos ahí este mes."
      }
      val fixed = s.fixed.filter { it.active }.sumOf { it.amount }
      if (fixed > 0 && st.avgIncome > 0 && fixed / st.avgIncome > 0.5) {
        spending += "Tus fijos (${m(fixed)}) son el ${(fixed / st.avgIncome * 100).toInt()} % de tus ingresos: bájalos del 50 % recortando suscripciones o planes."
      }
    }

    // Patrimonio
    if (s.assets.isEmpty() && s.debts.isEmpty()) {
      worth += "Registra activos y deudas en Patrimonio para medir tu patrimonio neto."
    } else {
      s.debts.filter { it.ratePct != null }.maxByOrNull { it.ratePct!! }?.let { d ->
        worth += "Abona extra a ${d.name} (${d.ratePct} % E.A., saldo ${m(d.balance)}): es tu deuda más cara."
      }
      if (s.assetsTotal > 0 && s.debtsTotal / s.assetsTotal > 0.5) {
        worth += "Tus deudas son el ${(s.debtsTotal / s.assetsTotal * 100).toInt()} % de tus activos: no tomes créditos nuevos hasta bajar del 50 %."
      }
      val byKind = s.assets.groupBy { it.kind }.mapValues { e -> e.value.sumOf { it.currentValue() } }
      byKind.maxByOrNull { it.value }?.let { (kind, value) ->
        if (s.assetsTotal > 0 && value / s.assetsTotal > 0.6 && byKind.size > 1) {
          worth += "El ${(value / s.assetsTotal * 100).toInt()} % de tus activos está en ${kindName(kind)}: lleva los próximos ahorros a otro tipo."
        }
      }
      val liquid = s.assets.filter { it.kind == "AHORRO" || it.kind == "CDT" || it.kind == "INVERSION" || it.kind == "ACCIONES" }.sumOf { it.currentValue() }
      if (st.avgExpense > 0) {
        val monthsCovered = liquid / st.avgExpense
        worth += if (monthsCovered < 3) "Tu colchón (${m(liquid)}) cubre ${"%.1f".format(monthsCovered).replace('.', ',')} meses: súbelo a ${m(st.avgExpense * 3)} (3 meses de gasto)."
        else "Colchón sano: ${m(liquid)} cubren ${monthsCovered.toInt()} meses. Lo que pase de 6 meses ponlo a rendir a más plazo."
      }
      s.assets.filter { it.kind == "CDT" && it.endDate != null }.minByOrNull { it.endDate!! }?.let { c ->
        val days = (c.endDate!! - System.currentTimeMillis()) / 86_400_000L
        if (days in 0..45) worth += "El CDT ${c.name} vence en $days días: compara tasas ya para renovarlo."
      }
      s.goals.firstOrNull { it.target > 0 && it.saved < it.target }?.let { g ->
        worth += "Meta ${g.name} al ${(g.saved / g.target * 100).toInt()} % (${m(g.saved)} de ${m(g.target)}): fija un aporte mensual."
      }
    }

    when (scope) {
      1 -> { out += worth; out += spending }
      2 -> { out += spending; out += worth }
      else -> {
        // Consolidado: se alternan para que salgan de los dos temas.
        val a = spending.iterator()
        val b = worth.iterator()
        while (a.hasNext() || b.hasNext()) { if (a.hasNext()) out += a.next(); if (b.hasNext()) out += b.next() }
      }
    }
    out += "Revisa esto una vez por semana."
    return out.take(3)
  }

  private fun kindName(k: String) = when (k) {
    "INMUEBLE" -> "inmuebles"; "INVERSION" -> "inversiones"; "VEHICULO" -> "vehículos"; "AHORRO" -> "ahorros"; "CDT" -> "CDT"; "ACCIONES" -> "acciones"; else -> "otros activos"
  }

  /** Resumen y 3 recomendaciones. [scope]: 0 consolidado, 1 patrimonio, 2 gastos de cuentas. */
  fun analysis(s: FinanceState, scope: Int): String = buildString {
    val st = stats(s)
    when (scope) {
      1 -> appendLine("Patrimonio neto: ${m(s.netWorth)} (activos ${m(s.assetsTotal)} − deudas ${m(s.debtsTotal)}). El dinero de tus cuentas (${m(s.accountsTotal)}) va aparte y no se suma.")
      2 -> {
        appendLine("Promedio de los últimos 3 meses: gastos ${m(st.avgExpense)} e ingresos ${m(st.avgIncome)} al mes.")
        s.accounts.forEach { a -> appendLine("• ${a.name}: saldo ${m(s.balances[a.id] ?: 0.0)}, gasto promedio ${m(s.avg3[a.id]?.expense ?: 0.0)} al mes.") }
      }
      else -> {
        appendLine("Promedio de los últimos 3 meses: gastos ${m(st.avgExpense)} e ingresos ${m(st.avgIncome)} al mes.")
        appendLine("Patrimonio neto: ${m(s.netWorth)} (activos ${m(s.assetsTotal)} − deudas ${m(s.debtsTotal)}).")
        appendLine("Dinero en tus cuentas: ${m(s.accountsTotal)} (aparte del patrimonio).")
      }
    }
    appendLine()
    appendLine("Qué hacer:")
    tips(s, scope).forEach { tip -> appendLine("• $tip") }
  }.trim()

  /** Consejo sobre los gastos hormiga detectados. */
  fun ants(s: FinanceState): String {
    val list = antExpenses(s)
    if (list.isEmpty()) return "No hay gastos pequeños repetidos en los últimos 30 días."
    val total = list.sumOf { it.total }
    return buildString {
      appendLine("Tus gastos hormiga suman ${m(total)} al mes, unos ${m(total * 12)} al año.")
      list.take(3).forEach { a -> appendLine("• ${a.label}: ${a.count} veces, ${m(a.total)}. Si lo reduces a la mitad ahorras ${m(a.total * 6)} al año.") }
      append("• Truco: fija un valor semanal en efectivo para estos gustos; cuando se acaba, se acaba.")
    }
  }

  /** Evalúa una compra o decisión comparándola con el margen mensual, las deudas y los ahorros registrados. */
  fun advise(question: String, s: FinanceState): String {
    val st = stats(s)
    val n = norm(question)
    val amount = amounts(question).maxOfOrNull { it.first }?.let { if (it < 1_000) it * 1_000 else it }
    val installments = Regex("(\\d+)\\s*(cuotas|cuota|meses|mes)").find(n)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 2..360 }
      ?: Regex("(\\d+)\\s*(anos|ano)").find(n)?.groupValues?.get(1)?.toIntOrNull()?.let { it * 12 }
    val rate = Regex("(\\d+(?:[.,]\\d+)?)\\s*(%|por ciento)").find(n)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    val margin = st.avgIncome - st.avgExpense
    val debtPayments = s.debts.sumOf { it.monthlyPayment ?: 0.0 }
    val savings = s.assets.filter { it.kind == "AHORRO" || it.kind == "CDT" || it.kind == "INVERSION" || it.kind == "ACCIONES" }.sumOf { it.currentValue() }
    return buildString {
      if (amount == null) {
        appendLine("• Para evaluarla necesito el valor. Escríbela con cifras, por ejemplo: \"comprar una moto de 8 millones a 24 cuotas\".")
        if (st.months > 0) appendLine("• Como referencia, hoy te quedan libres ${m(margin)} al mes en promedio (ingresos ${m(st.avgIncome)} − gastos ${m(st.avgExpense)}).")
        return@buildString
      }
      val monthly = installments?.let { k ->
        val i = rate?.let { (1 + it / 100).pow(1.0 / 12) - 1 } ?: 0.0
        if (i > 0) amount * i / (1 - (1 + i).pow(-k.toDouble())) else amount / k
      }
      appendLine("• Costo: ${m(amount)}" + (monthly?.let { " en $installments cuotas de unos ${m(it)}" + (if (rate != null) " (con $rate % E.A.; pagarías ${m(it * installments!!)} en total)." else " (sin contar intereses; pregunta la tasa).") } ?: "."))
      if (st.months == 0 || st.avgIncome <= 0) {
        appendLine("• No tengo ingresos registrados, así que no puedo medir si cabe en tu mes. Registra tus ingresos y vuelve a preguntar.")
      } else {
        appendLine("• Tu margen: ingresos ${m(st.avgIncome)} − gastos ${m(st.avgExpense)} = ${m(margin)} libres al mes (promedio de 3 meses).")
        if (monthly != null) {
          val share = if (margin > 0) monthly / margin else Double.MAX_VALUE
          appendLine(
            when {
              margin <= 0 -> "• Veredicto: NO por ahora. No te queda dinero libre al mes; una cuota nueva te obligaría a endeudarte más."
              share <= 0.35 -> "• Veredicto: SÍ es viable. La cuota usa el ${(share * 100).toInt()} % de tu margen y te deja ${m(margin - monthly)} libres."
              share <= 0.7 -> "• Veredicto: AJUSTADO. La cuota se lleva el ${(share * 100).toInt()} % de tu margen; cualquier imprevisto te descuadra. Mejor con cuota inicial más alta o menos valor."
              else -> "• Veredicto: NO recomendable. La cuota (${m(monthly)}) consume casi todo o más que tu margen (${m(margin)})."
            },
          )
          val load = (debtPayments + monthly) / st.avgIncome
          appendLine("• Endeudamiento: con esta cuota tus pagos de deuda serían el ${(load * 100).toInt()} % de tus ingresos" + if (load > 0.4) " — por encima del 40 % que se considera riesgoso." else " (sano si se mantiene bajo 40 %).")
        } else {
          appendLine(
            when {
              savings >= amount * 2 -> "• Veredicto: SÍ puedes. Tus ahorros e inversiones (${m(savings)}) lo cubren y te queda colchón."
              savings >= amount -> "• Veredicto: POSIBLE pero te deja sin colchón: usarías ${(amount / savings * 100).toInt()} % de tus ahorros (${m(savings)})."
              margin > 0 -> "• Veredicto: todavía NO de contado. Ahorrando tu margen completo lo reúnes en ${kotlin.math.ceil((amount - savings) / margin).toInt()} meses."
              else -> "• Veredicto: NO por ahora. No tienes ahorros suficientes ni margen mensual para reunirlo."
            },
          )
        }
      }
      val emergency = st.avgExpense * 3
      if (st.avgExpense > 0 && savings < emergency) appendLine("• Riesgo: tu fondo de emergencia (${m(savings)}) está por debajo de 3 meses de gasto (${m(emergency)}). Complétalo antes de compromisos grandes.")
      if (margin > 0) append("• Alternativa: aparta ${m(margin * 0.5)} al mes durante ${kotlin.math.ceil(amount * 0.3 / (margin * 0.5)).toInt().coerceAtLeast(1)} meses para una cuota inicial del 30 % y financia menos.")
      else append("• Alternativa: primero libera margen bajando tu mayor categoría de gasto y vuelve a evaluarlo en 2 o 3 meses.")
    }.trim()
  }

  // ---------- Conversación con el asistente ----------

  private val askWords = listOf(
    "que ", "como ", "cuanto", "cuando ", "cual", "puedo ", "debo ", "deberia", "me conviene", "conviene ", "analiza", "dime ", "recomiend", "aconsej",
    "evalua", "ayudame", "quiero saber", "necesito saber", "vale la pena", "es buena idea", "sera que", "donde ", "por que", "explica", "muestra", "revisa", "resume", "resumen",
  )
  private val wordSplit = Regex("[^a-z0-9]+")

  /** True si el texto pide una respuesta; false si la persona solo está contando algo para que quede anotado. */
  fun isQuestion(text: String): Boolean {
    if ('?' in text || '¿' in text) return true
    val n = norm(text.trim()) + " "
    return askWords.any { n.startsWith(it) }
  }

  /** Notas guardadas que tienen que ver con [text]: comparten alguna palabra de 4 letras o más. */
  fun related(text: String, memos: List<String>): List<String> {
    val words = norm(text).split(wordSplit).filter { it.length >= 4 }.toSet()
    return memos.filter { m -> norm(m).split(wordSplit).any { it in words } }.takeLast(3)
  }

  /**
   * Respuesta calculada en el teléfono a un mensaje del chat. [intent] viene de los botones rápidos
   * (ALL, WORTH, ACCOUNTS, ANTS, MEMORY); sin él se deduce del texto. [memos] es lo que la persona ha contado.
   */
  fun reply(text: String, s: FinanceState, memos: List<String>, intent: String?): String {
    val n = norm(text)
    val body = when {
      intent == "MEMORY" || "recuerdas" in n || "sabes de mi" in n || "te he contado" in n || "te conte" in n ->
        return if (memos.isEmpty()) "Todavía no me has contado nada para recordar. Escríbeme cosas como \"le presté plata a mi hermano\" o \"en diciembre recibo la prima\" y las tendré presentes."
        else "Esto es lo que tengo anotado de lo que me has contado:\n" + memos.joinToString("\n") { "• $it" }
      intent == "ANTS" || "hormiga" in n -> ants(s)
      intent == "WORTH" -> analysis(s, 1)
      intent == "ACCOUNTS" -> analysis(s, 2)
      intent == "ALL" -> analysis(s, 0)
      amounts(text).isNotEmpty() || listOf("comprar", "compro", "conviene", "credito", "prestamo", "cuotas", "invertir", "vender").any { it in n } -> advise(text, s)
      "patrimonio" in n || "deuda" in n || "activo" in n || "cdt" in n || "ahorro" in n -> analysis(s, 1)
      "cuenta" in n || "gasto" in n || "gaste" in n || "presupuesto" in n -> analysis(s, 2)
      else -> analysis(s, 0)
    }
    // En un análisis se recuerda todo lo que la persona contó (lo más reciente primero); en otras respuestas, lo relacionado.
    val notes = related(text, memos).ifEmpty { if (intent != null || "analiza" in n) memos.takeLast(5) else emptyList() }
    return if (notes.isEmpty()) body else body + "\n\nTengo presente lo que me contaste:\n" + notes.joinToString("\n") { "• $it" }
  }

  /** Acuse de recibo de algo que la persona contó para que quede en la memoria. */
  fun noted(text: String): String = "Anotado. Lo tendré presente cuando me pidas un análisis o un consejo." +
    if (amounts(text).isNotEmpty()) " Si ese valor debe mover el saldo de una cuenta, regístralo también con el botón + de Inicio."
    else " Si más adelante sabes el valor, cuéntamelo y lo sumo a lo que sé."
}
