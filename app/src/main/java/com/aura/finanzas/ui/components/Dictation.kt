package com.aura.finanzas.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Silencio seguido tras el que el dictado termina solo (dictado largo, para pensar en voz alta). */
private const val SILENCE_STOP_MS = 7_000L

/** En el dictado rápido (registrar un gasto): silencio tras haber hablado con el que se da por terminado. */
private const val QUICK_STOP_MS = 1_500L

/**
 * Dictado que no se corta a la primera pausa. El reconocedor del sistema termina cada frase tras un silencio
 * corto; aquí, mientras la persona no toque "Listo" y no lleve [SILENCE_STOP_MS] callada, se vuelve a escuchar
 * enseguida y se van juntando las frases. Así se puede pensar en voz alta sin que se corte.
 */
@Stable
class DictationState internal constructor(private val context: Context, private val quick: Boolean) {
  /** True mientras está escuchando. */
  var listening by mutableStateOf(false)
    private set

  /** Lo que se ha dictado hasta ahora (frases terminadas más la que va en curso). */
  var text by mutableStateOf("")
    private set

  var error by mutableStateOf<String?>(null)
    internal set

  internal var onDone: (String) -> Unit = {}
  internal var askPermission: () -> Unit = {}
  internal var fallback: () -> Unit = {}

  private val main = Handler(Looper.getMainLooper())
  private var recognizer: SpeechRecognizer? = null
  private val heard = StringBuilder()
  private var partial = ""
  private var lastSpeech = 0L

  /** Silencio permitido: largo mientras no se ha dicho nada; en el modo rápido, corto en cuanto ya se habló. */
  private val silenceLimit get() = if (quick && heard.isNotEmpty() || quick && partial.isNotEmpty()) QUICK_STOP_MS else SILENCE_STOP_MS

  private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO")
    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    .apply {
      // Dictado largo: pausas largas antes de dar la frase por terminada (algunos teléfonos respetan estos valores).
      if (!quick) {
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6_000L)
      }
    }

  /** Empieza a escuchar; si falta el permiso del micrófono, lo pide primero. */
  fun start() {
    if (listening) return
    error = null
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { askPermission(); return }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) { fallback(); return }
    heard.clear(); partial = ""; text = ""
    listening = true
    lastSpeech = SystemClock.elapsedRealtime()
    listen()
    main.postDelayed(watchdog, if (quick) 300L else 1_000L)
  }

  /**
   * Vigilante: algunos teléfonos dejan de escuchar sin avisar (sin resultado ni error). Si pasa más tiempo que
   * el silencio permitido sin oír nada, se termina igual y se entrega lo que haya.
   */
  private val watchdog: Runnable = object : Runnable {
    override fun run() {
      if (!listening) return
      val quiet = SystemClock.elapsedRealtime() - lastSpeech
      // En el modo rápido, en cuanto ya se dijo algo y hay un silencio corto, se entrega sin esperar al reconocedor.
      if (quick && (heard.isNotEmpty() || partial.isNotEmpty()) && quiet > QUICK_STOP_MS + 300) stop()
      else if (quiet > SILENCE_STOP_MS + 3_000) stop()
      else main.postDelayed(this, if (quick) 300L else 1_000L)
    }
  }

  /** La persona tocó "Listo": se entrega lo dictado, incluida la frase que iba en curso. */
  fun stop() {
    if (!listening) return
    append(partial)
    finish()
  }

  /** Cierra sin entregar nada (al salir de la pantalla). */
  fun cancel() {
    listening = false
    main.removeCallbacksAndMessages(null)
    recognizer?.destroy()
    recognizer = null
  }

  private fun append(s: String) {
    val clean = s.trim()
    if (clean.isNotEmpty()) { if (heard.isNotEmpty()) heard.append(' '); heard.append(clean) }
    partial = ""
    text = heard.toString()
  }

  private fun finish() {
    cancel()
    val out = heard.toString().trim()
    if (out.isNotEmpty()) onDone(out)
  }

  /** Sigue escuchando salvo que ya lleve demasiado tiempo en silencio. */
  private fun again() {
    if (!listening) return
    if (SystemClock.elapsedRealtime() - lastSpeech > silenceLimit) { finish(); return }
    main.postDelayed({ if (listening) listen() }, 120)
  }

  private fun listen() {
    val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
    r.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() { lastSpeech = SystemClock.elapsedRealtime() }
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
      override fun onPartialResults(partialResults: Bundle?) {
        val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (p.isNotBlank() && p != partial) {
          partial = p
          lastSpeech = SystemClock.elapsedRealtime()
          text = (heard.toString() + " " + p).trim()
        }
      }
      override fun onResults(results: Bundle?) {
        val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (said.isNotBlank()) lastSpeech = SystemClock.elapsedRealtime()
        append(said.ifBlank { partial })
        again()
      }
      override fun onError(code: Int) {
        when (code) {
          // Silencio o nada entendible: se vuelve a escuchar hasta completar el tiempo de silencio.
          SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> { append(partial); again() }
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
            recognizer?.destroy(); recognizer = null
            if (listening) main.postDelayed({ if (listening) listen() }, 400)
          }
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { error = "Permite el micrófono para dictar."; finish() }
          SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
            error = "El dictado necesita internet en este teléfono. Escribe el texto o inténtalo de nuevo."
            append(partial); finish()
          }
          else -> { append(partial); finish() }
        }
      }
    })
    r.startListening(intent)
  }
}

/**
 * Dictado listo para usar en una pantalla. [onText] recibe todo lo dictado al terminar. [quick]: termina poco
 * después de que la persona deja de hablar (para registrar un gasto); si no, aguanta pausas largas. Si el teléfono no tiene
 * reconocimiento integrado se abre la ventana de dictado del sistema como antes.
 */
@Composable
fun rememberDictation(quick: Boolean = false, onText: (String) -> Unit): DictationState {
  val context = LocalContext.current
  val latest by rememberUpdatedState(onText)
  val state = remember { DictationState(context, quick) }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) state.start() else state.error = "Sin permiso del micrófono no puedo escuchar. Puedes escribir el texto."
  }
  val system = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { latest(it) }
  }
  state.onDone = { latest(it) }
  state.askPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) }
  state.fallback = {
    try {
      system.launch(
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
          .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CO"),
      )
    } catch (e: android.content.ActivityNotFoundException) {
      state.error = "Este teléfono no tiene dictado por voz. Escribe el texto."
    }
  }
  DisposableEffect(Unit) { onDispose { state.cancel() } }
  return state
}
