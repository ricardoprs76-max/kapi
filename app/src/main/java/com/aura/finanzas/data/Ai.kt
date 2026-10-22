package com.aura.finanzas.data

import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Error de IA con un mensaje ya listo para mostrar a la persona. */
class AiException(message: String) : Exception(message)

/** Cliente único para los dos proveedores: la clave y el modelo los pone la persona en Ajustes. */
object Ai {
  /**
   * Envía [system] + [user] (y opcionalmente una foto JPEG) y devuelve el texto de la respuesta.
   * [deep] pide más razonamiento (diagnósticos y asesoría); la extracción de datos va con el mínimo.
   * [json]: la respuesta debe ser un objeto JSON (Gemini lo entrega directo, sin texto alrededor).
   */
  suspend fun ask(s: Settings, system: String, user: String, imageJpeg: ByteArray? = null, deep: Boolean = false, json: Boolean = false): String =
    withContext(Dispatchers.IO) {
      if (s.aiKey.isBlank()) throw AiException("Configura tu clave de API en Ajustes → Inteligencia artificial.")
      val text = if (s.aiProvider == AiProvider.CLAUDE) claude(s, system, user, imageJpeg, deep) else gemini(s, system, user, imageJpeg, deep, json)
      text.trim().ifEmpty { throw AiException("El modelo respondió vacío. Intenta de nuevo.") }
    }

  // ---------- Claude (SDK oficial de Anthropic) ----------

  private fun claude(s: Settings, system: String, user: String, image: ByteArray?, deep: Boolean): String {
    val client = AnthropicOkHttpClient.builder().apiKey(s.claudeKey.trim()).build()
    val model = s.claudeModel.trim()

    fun params(full: Boolean): MessageCreateParams {
      val b = MessageCreateParams.builder().model(model).maxTokens(16000L).system(system)
      if (image != null) {
        val source = Base64ImageSource.builder()
          .data(Base64.encodeToString(image, Base64.NO_WRAP))
          .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
          .build()
        b.addUserMessageOfBlockParams(
          listOf(
            ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build()),
            ContentBlockParam.ofText(TextBlockParam.builder().text(user).build()),
          ),
        )
      } else {
        b.addUserMessage(user)
      }
      if (full) {
        b.outputConfig(OutputConfig.builder().effort(if (deep) OutputConfig.Effort.MEDIUM else OutputConfig.Effort.LOW).build())
        // Si el modelo rechaza la petición por sus filtros de seguridad, el servidor la reintenta en un modelo alterno.
        if (supportsServerFallback(model)) {
          b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
          b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
      }
      return b.build()
    }

    try {
      val message = try {
        client.messages().create(params(full = true))
      } catch (e: AnthropicServiceException) {
        // Un modelo antiguo puede no aceptar "effort" o "fallbacks": se repite una vez con la petición básica.
        if (e.statusCode() != 400) throw e
        client.messages().create(params(full = false))
      }
      if (message.stopReason().map { it.toString().contains("refusal", ignoreCase = true) }.orElse(false)) {
        throw AiException("El modelo no quiso responder esta petición. Reformúlala e intenta de nuevo.")
      }
      return message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
    } catch (e: AnthropicServiceException) {
      throw AiException(httpMessage("Claude", e.statusCode(), e.message))
    } catch (e: AnthropicIoException) {
      throw AiException("No hay conexión con Claude. Revisa tu internet.")
    } finally {
      client.close()
    }
  }

  private fun supportsServerFallback(model: String) =
    model.startsWith("claude-opus-5") || model.startsWith("claude-fable-5-1") || model.startsWith("claude-sonnet-5-5")

  // ---------- Gemini (API REST) ----------

  /**
   * Modelos de respaldo cuando el elegido está saturado (Google responde 429/5xx en horas pico). Son alias
   * «-latest» o recientes: Google retira los modelos con número (gemini-2.5-flash ya no se da a claves nuevas).
   */
  private val geminiBackups = listOf("gemini-flash-lite-latest", "gemini-3.8-flash", "gemini-3.5-flash-lite")

  /** Modelos que hace poco respondieron «saturado» o tardaron demasiado: se dejan de últimos por unos minutos. */
  private val busyUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

  private fun gemini(s: Settings, system: String, user: String, image: ByteArray?, deep: Boolean, json: Boolean): String {
    val chosen = s.geminiModel.trim()
    var first: GeminiBusy? = null
    val now = System.currentTimeMillis()
    val all = (listOf(chosen) + geminiBackups).distinct()
    val (resting, ready) = all.partition { (busyUntil[it] ?: 0L) > now }
    for (model in ready + resting) {
      try {
        return geminiFast(s, model, system, user, image, deep, json).also { busyUntil.remove(model) }
      } catch (e: GeminiBusy) {
        if (first == null) first = e
        busyUntil[model] = System.currentTimeMillis() + 5 * 60_000L
      } catch (e: AiException) {
        // Un modelo de respaldo que ya no existe se salta; el error del elegido sí se muestra.
        if (model == chosen) throw e
      }
    }
    if (first?.code == 0) throw AiException("Gemini está tardando demasiado en responder en este momento. Tu clave está bien; intenta de nuevo en unos minutos.")
    throw AiException(httpMessage("Gemini", first?.code ?: 503, first?.detail))
  }

  /** El modelo no puede atender ahora: 429/5xx, o [code] 0 = no respondió a tiempo. */
  private class GeminiBusy(val code: Int, val detail: String?) : Exception()

  /**
   * Verifica la clave sin gastar consultas: pide a Google la ficha del modelo. Devuelve el texto para mostrar o
   * lanza [AiException] si la clave o el modelo no sirven.
   */
  suspend fun verifyGemini(s: Settings): String = withContext(Dispatchers.IO) {
    val key = s.geminiKey.trim()
    if (key.isBlank()) throw AiException("Pega primero tu clave de Gemini.")
    val model = s.geminiModel.trim()
    val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$model").openConnection() as HttpURLConnection
    try {
      conn.connectTimeout = 20_000
      conn.readTimeout = 30_000
      conn.setRequestProperty("x-goog-api-key", key)
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
      if (code in 200..299) return@withContext "Clave verificada ✓ · modelo $model disponible"
      val detail = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
      if (detail?.contains("API key", ignoreCase = true) == true || code == 401 || code == 403) throw AiException("La clave de Gemini no es válida o no tiene permiso. Revisa que la copiaste completa.")
      throw AiException(httpMessage("Gemini", code, detail))
    } catch (e: IOException) {
      throw AiException("No hay conexión con Gemini. Revisa tu internet.")
    } finally {
      conn.disconnect()
    }
  }

  /**
   * Formas de pedir a Gemini que responda sin pensar de más. Los modelos 3.x usan «thinkingLevel», los 2.5
   * «thinkingBudget»; el que no entienda una forma responde 400 al instante y se prueba la siguiente. La que
   * funcionó se recuerda por modelo para no repetir los intentos.
   */
  private val quickThinking = listOf(
    JSONObject().put("thinkingLevel", "minimal"),
    JSONObject().put("thinkingLevel", "low"),
    JSONObject().put("thinkingBudget", 0),
    null,
  )
  private val quickChoice = java.util.concurrent.ConcurrentHashMap<String, Int>()

  /** Para análisis y facturas difíciles: algo de razonamiento, pero acotado. */
  private val deepThinking = listOf(
    JSONObject().put("thinkingLevel", "low"),
    JSONObject().put("thinkingBudget", 2048),
    null,
  )
  private val deepChoice = java.util.concurrent.ConcurrentHashMap<String, Int>()

  private fun geminiFast(s: Settings, model: String, system: String, user: String, image: ByteArray?, deep: Boolean, json: Boolean): String {
    val forms = if (deep) deepThinking else quickThinking
    val memory = if (deep) deepChoice else quickChoice
    var i = memory[model] ?: 0
    // Tiempo máximo por modelo: si no contesta, se pasa al siguiente en vez de esperar dos minutos.
    val wait = when {
      image != null && deep -> 60_000
      image != null -> 35_000
      deep -> 30_000
      else -> 20_000
    }
    while (true) {
      try {
        return geminiOnce(s, model, system, user, image, forms[i], json, wait).also { memory[model] = i }
      } catch (e: GeminiRejected) {
        if (i >= forms.lastIndex) throw AiException(httpMessage("Gemini", 400, e.detail))
        i++
      }
    }
  }

  /** Gemini no aceptó la forma de la petición (400 que no es de la clave). */
  private class GeminiRejected(val detail: String?) : Exception()

  private fun geminiOnce(s: Settings, model: String, system: String, user: String, image: ByteArray?, thinking: JSONObject?, json: Boolean, wait: Int = 60_000): String {
    val parts = JSONArray().put(JSONObject().put("text", user))
    if (image != null) {
      parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(image, Base64.NO_WRAP))))
    }
    val body = JSONObject()
      .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
      .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
    val config = JSONObject()
    if (thinking != null) config.put("thinkingConfig", thinking)
    if (json && thinking != null) config.put("responseMimeType", "application/json")
    if (config.length() > 0) body.put("generationConfig", config)
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
    val conn = url.openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.connectTimeout = 20_000
      conn.readTimeout = wait
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/json")
      conn.setRequestProperty("x-goog-api-key", s.geminiKey.trim())
      conn.outputStream.use { it.write(body.toString().toByteArray()) }
      val code = conn.responseCode
      val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
      if (code !in 200..299) {
        val detail = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrNull()
        // Gemini responde 400 (no 401) cuando la clave está mal escrita.
        if (detail?.contains("API key", ignoreCase = true) == true) throw AiException("La clave de Gemini no es válida o no tiene permiso.")
        if (code == 429 || code in 500..599) throw GeminiBusy(code, detail)
        if (code == 400 && (thinking != null || json)) throw GeminiRejected(detail)
        if (code == 404) throw AiException(httpMessage("Gemini", code, detail))
        throw AiException(httpMessage("Gemini", code, detail))
      }
      val candidate = JSONObject(raw).optJSONArray("candidates")?.optJSONObject(0)
        ?: throw AiException("Gemini no devolvió respuesta (puede haberla bloqueado). Intenta de nuevo.")
      val out = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
      return (0 until out.length()).joinToString("") { out.getJSONObject(it).optString("text") }
    } catch (e: java.net.SocketTimeoutException) {
      throw GeminiBusy(0, null)
    } catch (e: IOException) {
      throw AiException("No hay conexión con Gemini. Revisa tu internet.")
    } finally {
      conn.disconnect()
    }
  }

  private fun httpMessage(provider: String, code: Int, detail: String?): String = when (code) {
    400 -> "$provider rechazó la petición. Revisa el nombre del modelo en Ajustes." + (detail?.let { "\n$it" } ?: "")
    401, 403 -> "La clave de $provider no es válida o no tiene permiso."
    404 -> "$provider no encontró el modelo. Revisa su nombre en Ajustes."
    429 -> "Llegaste al límite de uso de $provider. Espera un momento e intenta de nuevo."
    in 500..599 -> "$provider está saturado en este momento (error $code). Tu clave no tiene problema; intenta en unos minutos."
    else -> "$provider respondió con el error $code." + (detail?.let { "\n$it" } ?: "")
  }

  /** Extrae el primer objeto JSON de una respuesta que puede venir envuelta en texto o en bloques de código. */
  fun jsonFrom(text: String): JSONObject {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) throw AiException("No pude entender la respuesta del modelo. Intenta de nuevo.")
    return try {
      JSONObject(text.substring(start, end + 1))
    } catch (e: org.json.JSONException) {
      throw AiException("No pude entender la respuesta del modelo. Intenta de nuevo.")
    }
  }
}
