package com.aura.finanzas.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("ajustes")

object AiProvider {
  const val GEMINI = "GEMINI"
  const val CLAUDE = "CLAUDE"
  const val DEFAULT_GEMINI_MODEL = "gemini-flash-latest"
  const val DEFAULT_CLAUDE_MODEL = "claude-opus-5-5"
}

data class Settings(
  val palette: Int = 0,
  /** 0 = según el sistema, 1 = claro, 2 = oscuro. */
  val themeMode: Int = 1,
  val animatedBackground: Boolean = true,
  val hideAmounts: Boolean = false,
  val loaded: Boolean = false,
  val aiProvider: String = AiProvider.GEMINI,
  val geminiKey: String = "",
  val claudeKey: String = "",
  val geminiModel: String = AiProvider.DEFAULT_GEMINI_MODEL,
  val claudeModel: String = AiProvider.DEFAULT_CLAUDE_MODEL,
  val lockEnabled: Boolean = false,
  val onboarded: Boolean = false,
  /** Desenfoque real del contenido bajo la barra inferior. */
  val blur: Boolean = true,
  /** Notificación diaria con los pagos que vencen pronto. */
  val reminders: Boolean = false,
  /** Recordatorio diario para registrar los movimientos del día (solo avisa si ese día no se registró nada). */
  val dailyLog: Boolean = true,
  val dailyHour: Int = 20,
  val dailyMinute: Int = 0,
  /** Ya se pidió una vez el permiso de notificaciones para el recordatorio diario. */
  val dailyAsked: Boolean = false,
  /** Copia y sincronización con la cuenta de Google. */
  val cloudOn: Boolean = false,
  val cloudEmail: String = "",
  val cloudLastSync: Long = 0,
  /** Huellas del archivo remoto y de los datos locales en la última sincronización. */
  val cloudRemoteMd5: String = "",
  val cloudLocalHash: String = "",
  /** Fondo animado elegido; índice en la lista de fondos de la app. */
  val bgStyle: Int = 1,
  /** Kapi Premium comprado en Google Play (última respuesta conocida de la tienda). */
  val premium: Boolean = false,
  /** Premium activado a mano en una instalación de prueba. */
  val devPremium: Boolean = false,
  /** La persona entró sin cuenta en una instalación que aún no tiene Firebase. */
  val guest: Boolean = false,
  val cloudAsked: Boolean = false,
) {
  val isPremium get() = premium || devPremium
  val aiKey get() = if (aiProvider == AiProvider.CLAUDE) claudeKey else geminiKey
  val aiModel get() = if (aiProvider == AiProvider.CLAUDE) claudeModel else geminiModel
}

object Keys {
  val palette = intPreferencesKey("palette")
  val themeMode = intPreferencesKey("themeMode")
  val animatedBg = booleanPreferencesKey("animatedBg")
  val hideAmounts = booleanPreferencesKey("hideAmounts")
  val aiProvider = stringPreferencesKey("aiProvider")
  val geminiKey = stringPreferencesKey("geminiKey")
  val claudeKey = stringPreferencesKey("claudeKey")
  val geminiModel = stringPreferencesKey("geminiModel")
  val claudeModel = stringPreferencesKey("claudeModel")
  val lockEnabled = booleanPreferencesKey("lockEnabled")
  val onboarded = booleanPreferencesKey("onboarded")
  val blur = booleanPreferencesKey("blur")
  val reminders = booleanPreferencesKey("reminders")
  val dailyLog = booleanPreferencesKey("dailyLog")
  val dailyHour = intPreferencesKey("dailyHour")
  val dailyMinute = intPreferencesKey("dailyMinute")
  val dailyAsked = booleanPreferencesKey("dailyAsked")
  // El estado de la copia en Google Drive es de cada persona que entra (sufijo de UserSpace).
  val cloudOn get() = booleanPreferencesKey("cloudOn" + UserSpace.suffix)
  val cloudEmail get() = stringPreferencesKey("cloudEmail" + UserSpace.suffix)
  val cloudLastSync get() = longPreferencesKey("cloudLastSync" + UserSpace.suffix)
  val cloudRemoteMd5 get() = stringPreferencesKey("cloudRemoteMd5" + UserSpace.suffix)
  val cloudLocalHash get() = stringPreferencesKey("cloudLocalHash" + UserSpace.suffix)
  /** Ya se ofreció activar la copia en Drive al entrar; no se vuelve a abrir sola. */
  val cloudAsked get() = booleanPreferencesKey("cloudAsked" + UserSpace.suffix)
  val bgStyle = intPreferencesKey("bgStyle")
  val premium = booleanPreferencesKey("premium")
  val devPremium = booleanPreferencesKey("devPremium")
  val guest = booleanPreferencesKey("guest")
  // Las claves de API se guardan cifradas; geminiKey y claudeKey (texto plano) solo existen en instalaciones antiguas.
  val geminiKeyEnc = stringPreferencesKey("geminiKeyEnc")
  val claudeKeyEnc = stringPreferencesKey("claudeKeyEnc")
}

class Prefs(private val context: Context) {
  init { UserSpace.ensure(context) }

  val flow = context.store.data.map {
    Settings(
      palette = it[Keys.palette] ?: 0,
      themeMode = it[Keys.themeMode] ?: 1,
      animatedBackground = it[Keys.animatedBg] ?: true,
      hideAmounts = it[Keys.hideAmounts] ?: false,
      loaded = true,
      aiProvider = it[Keys.aiProvider] ?: AiProvider.GEMINI,
      geminiKey = it[Keys.geminiKeyEnc]?.let(Crypto::decrypt) ?: it[Keys.geminiKey] ?: "",
      claudeKey = it[Keys.claudeKeyEnc]?.let(Crypto::decrypt) ?: it[Keys.claudeKey] ?: "",
      geminiModel = it[Keys.geminiModel]?.ifBlank { null } ?: AiProvider.DEFAULT_GEMINI_MODEL,
      claudeModel = it[Keys.claudeModel]?.ifBlank { null } ?: AiProvider.DEFAULT_CLAUDE_MODEL,
      lockEnabled = it[Keys.lockEnabled] ?: false,
      onboarded = it[Keys.onboarded] ?: false,
      blur = it[Keys.blur] ?: true,
      reminders = it[Keys.reminders] ?: false,
      dailyLog = it[Keys.dailyLog] ?: true,
      dailyHour = it[Keys.dailyHour] ?: 20,
      dailyMinute = it[Keys.dailyMinute] ?: 0,
      dailyAsked = it[Keys.dailyAsked] ?: false,
      cloudOn = it[Keys.cloudOn] ?: false,
      cloudEmail = it[Keys.cloudEmail] ?: "",
      cloudLastSync = it[Keys.cloudLastSync] ?: 0,
      cloudRemoteMd5 = it[Keys.cloudRemoteMd5] ?: "",
      cloudLocalHash = it[Keys.cloudLocalHash] ?: "",
      bgStyle = it[Keys.bgStyle] ?: 1,
      premium = it[Keys.premium] ?: false,
      devPremium = it[Keys.devPremium] ?: false,
      guest = it[Keys.guest] ?: false,
      cloudAsked = it[Keys.cloudAsked] ?: false,
    )
  }

  /** Guarda la clave de API cifrada con una llave del Keystore de Android y borra cualquier copia en texto plano. */
  suspend fun setSecret(provider: String, value: String) {
    val claude = provider == AiProvider.CLAUDE
    context.store.edit {
      it[if (claude) Keys.claudeKeyEnc else Keys.geminiKeyEnc] = Crypto.encrypt(value)
      it.remove(if (claude) Keys.claudeKey else Keys.geminiKey)
    }
  }

  /** Cifra las claves que una versión anterior dejó en texto plano. */
  suspend fun migrateSecrets() {
    context.store.edit {
      for ((plain, enc) in listOf(Keys.geminiKey to Keys.geminiKeyEnc, Keys.claudeKey to Keys.claudeKeyEnc)) {
        val old = it[plain] ?: continue
        if (old.isNotEmpty() && it[enc] == null) it[enc] = Crypto.encrypt(old)
        it.remove(plain)
      }
    }
  }

  suspend fun <T> set(key: Preferences.Key<T>, value: T) {
    context.store.edit { it[key] = value }
  }
}
