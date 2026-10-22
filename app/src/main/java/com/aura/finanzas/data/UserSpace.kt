package com.aura.finanzas.data

import android.content.Context

/**
 * Cada persona que entra a la app con su cuenta de Google tiene su propio espacio en el teléfono: su base de
 * datos, su estado de sincronización y sus cuentas compartidas. Así, si en el mismo teléfono entra otra persona
 * (por ejemplo, quien colabora en una cuenta), solo ve sus propios datos y lo que le compartieron.
 *
 * La primera persona que entra se queda con los datos que ya había en el teléfono (los archivos de siempre, sin
 * sufijo); las demás usan archivos con su identificador. El espacio se decide una vez por proceso: si la sesión
 * cambia a otra persona, la app se reinicia para abrir el espacio correcto.
 */
object UserSpace {
  private const val FILE = "espacio_usuario"
  private const val OWNER = "legacyOwner"

  @Volatile private var decided: String? = null

  /** Sufijo de los archivos de la persona actual ("" para la primera); solo válido después de [ensure]. */
  val suffix: String get() = decided ?: ""

  /** Decide (una sola vez por proceso) el espacio de la sesión abierta y devuelve su sufijo. */
  fun ensure(context: Context): String = decided ?: synchronized(this) {
    decided ?: run {
      Auth.init(context)
      val uid = Auth.currentUid()
      if (uid == null) guestSuffix(context) else suffixFor(context, uid)
    }.also { decided = it }
  }

  /** True si [uid] (la sesión que se acaba de abrir) necesita otro espacio que el que tiene abierto este proceso. */
  fun needsRestart(context: Context, uid: String?): Boolean {
    val current = decided ?: return false
    return (if (uid == null) guestSuffix(context) else suffixFor(context, uid)) != current
  }

  /**
   * Quien entra como invitado (sin correo) usa los archivos de siempre solo si nadie ha entrado aún con Google en
   * este teléfono (y al entrar luego con Google conserva lo que registró); si ya hay una cuenta, tiene su propio
   * espacio y no ve los datos de esa persona.
   */
  private fun guestSuffix(context: Context): String =
    if (context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(OWNER, null) == null) "" else "-invitado"

  private fun suffixFor(context: Context, uid: String): String {
    val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    val owner = sp.getString(OWNER, null) ?: uid.also { sp.edit().putString(OWNER, it).commit() }
    return if (owner == uid) "" else "-$uid"
  }
}
