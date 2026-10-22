package com.aura.finanzas.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.aura.finanzas.FinanceState
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Lado máximo de la foto que se envía a la IA: suficiente para leer un recibo y liviano de subir. */
private const val MAX_SIDE = 1568

/** Carga la imagen de [uri], la endereza según su EXIF, la reduce y la devuelve como JPEG; null si no se puede leer. */
fun loadJpeg(context: Context, uri: Uri): ByteArray? {
  val resolver = context.contentResolver
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  (resolver.openInputStream(uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
  if (bounds.outWidth <= 0) return null
  var sample = 1
  while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
  val decoded = resolver.openInputStream(uri)?.use {
    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
  } ?: return null
  val degrees = resolver.openInputStream(uri)?.use {
    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90f
      ExifInterface.ORIENTATION_ROTATE_180 -> 180f
      ExifInterface.ORIENTATION_ROTATE_270 -> 270f
      else -> 0f
    }
  } ?: 0f
  val scale = (MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
  val matrix = Matrix().apply { postScale(scale, scale); postRotate(degrees) }
  val bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
  return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}

object DataTools {
  private val tables = listOf("accounts", "categories", "transactions", "assets", "debts", "budgets", "fixed_expenses", "goals", "ai_notes")
  private val csvDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

  /** CSV con punto y coma y BOM, que es como Excel en español lo abre bien. */
  fun writeCsv(context: Context, uri: Uri, s: FinanceState) {
    fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
    val zone = ZoneId.systemDefault()
    val out = StringBuilder("﻿fecha;tipo;valor;cuenta;cuenta_destino;categoria;nota;etiquetas;origen\n")
    for (t in s.txs) {
      out.append(
        listOf(
          Instant.ofEpochMilli(t.date).atZone(zone).format(csvDate), t.type, t.amount.toLong().toString(),
          s.accountById[t.accountId]?.name ?: "", t.toAccountId?.let { s.accountById[it]?.name } ?: "",
          t.categoryId?.let { s.categoryById[it]?.name } ?: "", t.note, t.tags, t.source,
        ).joinToString(";") { cell(it) },
      ).append('\n')
    }
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.toString().toByteArray()) }
      ?: error("no se pudo abrir el archivo")
  }

  /**
   * Todas las tablas tal cual, en un JSON. Es el mismo formato para el respaldo en archivo y para la copia
   * en la cuenta de Google. Las filas van ordenadas para que dos copias iguales produzcan el mismo texto.
   * No incluye las claves de API.
   */
  fun snapshot(context: Context): String {
    val db = AppDb.get(context).openHelper.readableDatabase
    val root = JSONObject().put("app", "AuraFinanzas").put("version", 1)
    for (table in tables) {
      val rows = JSONArray()
      db.query("SELECT * FROM $table ORDER BY id").use { c ->
        while (c.moveToNext()) {
          val row = JSONObject()
          for (i in 0 until c.columnCount) {
            when (c.getType(i)) {
              Cursor.FIELD_TYPE_NULL -> row.put(c.getColumnName(i), JSONObject.NULL)
              Cursor.FIELD_TYPE_INTEGER -> row.put(c.getColumnName(i), c.getLong(i))
              Cursor.FIELD_TYPE_FLOAT -> row.put(c.getColumnName(i), c.getDouble(i))
              else -> row.put(c.getColumnName(i), c.getString(i))
            }
          }
          rows.put(row)
        }
      }
      root.put(table, rows)
    }
    return root.toString()
  }

  /** True si [snapshot] no tiene cuentas ni movimientos (instalación recién hecha). */
  fun isEmpty(snapshot: String): Boolean {
    val root = JSONObject(snapshot)
    return (root.optJSONArray("accounts")?.length() ?: 0) == 0 && (root.optJSONArray("transactions")?.length() ?: 0) == 0
  }

  /** Reemplaza TODOS los datos por los de [text]; si algo falla no se cambia nada. */
  suspend fun applySnapshot(context: Context, text: String) {
    val root = try { JSONObject(text) } catch (e: org.json.JSONException) { error("el archivo no es un respaldo válido") }
    if (root.optString("app") != "AuraFinanzas") error("el archivo no es un respaldo de Kapi")
    val room = AppDb.get(context)
    room.withTransaction {
      val db = room.openHelper.writableDatabase
      for (table in tables) {
        // Una copia hecha con una versión anterior no trae las tablas nuevas: esas se dejan como están.
        val rows = root.optJSONArray(table) ?: continue
        db.execSQL("DELETE FROM $table")
        for (i in 0 until rows.length()) {
          val row = rows.getJSONObject(i)
          val values = ContentValues()
          for (key in row.keys()) {
            when (val v = row.get(key)) {
              JSONObject.NULL -> values.putNull(key)
              is Int -> values.put(key, v.toLong())
              is Long -> values.put(key, v)
              is Double -> values.put(key, v)
              is Boolean -> values.put(key, if (v) 1L else 0L)
              else -> values.put(key, v.toString())
            }
          }
          db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
        }
      }
    }
  }

  fun writeBackup(context: Context, uri: Uri) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(snapshot(context).toByteArray()) }
      ?: error("no se pudo abrir el archivo")
  }

  suspend fun readBackup(context: Context, uri: Uri) {
    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
      ?: error("no se pudo abrir el archivo")
    applySnapshot(context, text)
  }
}
