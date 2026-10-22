package com.aura.finanzas.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Un trozo de texto leído en la foto. [x] es su borde izquierdo y [y] la altura de su centro, ya corregidas
 * por la inclinación de la foto, de modo que los trozos de un mismo renglón tienen casi la misma [y].
 */
class OcrPiece(val text: String, val x: Float, val y: Float, val height: Float)

/** Lectura de texto en el propio teléfono (sin internet ni clave), para los recibos. */
object Ocr {
  suspend fun pieces(context: Context, uri: Uri): List<OcrPiece> = suspendCancellableCoroutine { cont ->
    val image = try {
      InputImage.fromFilePath(context, uri)
    } catch (e: Exception) {
      cont.resumeWithException(AiException("No pude abrir la imagen."))
      return@suspendCancellableCoroutine
    }
    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    client.process(image)
      .addOnSuccessListener { result ->
        class Raw(val text: String, val x0: Float, val y0: Float, val x1: Float, val y1: Float, val h: Float)
        val raw = result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
          val c = line.cornerPoints?.takeIf { it.size == 4 } ?: return@mapNotNull null
          // Esquinas: arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda.
          val h = kotlin.math.hypot((c[3].x - c[0].x).toFloat(), (c[3].y - c[0].y).toFloat())
          Raw(line.text, (c[0].x + c[3].x) / 2f, (c[0].y + c[3].y) / 2f, (c[1].x + c[2].x) / 2f, (c[1].y + c[2].y) / 2f, h)
        }
        // Inclinación de la foto: la mediana del ángulo de los renglones largos.
        val angles = raw.filter { it.text.length >= 8 }.map { atan2(it.y1 - it.y0, it.x1 - it.x0) }.sorted()
        val tilt = if (angles.isEmpty()) 0f else angles[angles.size / 2]
        val co = cos(-tilt)
        val si = sin(-tilt)
        cont.resume(raw.map { OcrPiece(it.text, it.x0 * co - it.y0 * si, it.x0 * si + it.y0 * co, it.h) }.sortedBy { it.y })
      }
      .addOnFailureListener { cont.resumeWithException(AiException("No pude leer el texto de la imagen.")) }
      .addOnCompleteListener { client.close() }
  }

  /**
   * Une en un mismo renglón los trozos que están a la misma altura. En un recibo el concepto y su valor suelen
   * quedar en columnas separadas.
   */
  fun rows(pieces: List<OcrPiece>): List<List<OcrPiece>> {
    val rows = ArrayList<MutableList<OcrPiece>>()
    for (p in pieces.sortedBy { it.y }) {
      val row = rows.lastOrNull()?.takeIf { r -> r.any { kotlin.math.abs(it.y - p.y) < maxOf(it.height, p.height) * 0.5f } }
      if (row != null) row += p else rows += mutableListOf(p)
    }
    return rows.map { r -> r.sortedBy { it.x } }
  }

  fun text(pieces: List<OcrPiece>): String = rows(pieces).joinToString("\n") { r -> r.joinToString("  ") { it.text } }
}
