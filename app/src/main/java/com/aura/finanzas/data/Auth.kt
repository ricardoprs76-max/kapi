package com.aura.finanzas.data

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.aura.finanzas.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/** Persona que ingresó a la app con su cuenta de Google. */
data class AuthUser(val uid: String, val name: String, val email: String)

/** Error de ingreso con un mensaje listo para mostrar. */
class AuthException(message: String) : Exception(message)

/**
 * Registro e inicio de sesión con cualquier cuenta de Google, por el flujo estándar: el Administrador de
 * credenciales de Android muestra la pantalla "Acceder con Google", Google entrega un token de identidad y con
 * él se abre la sesión en Firebase Authentication. La primera vez que una cuenta entra, Firebase la registra;
 * las siguientes, la reconoce. La app nunca ve la contraseña.
 */
object Auth {
  /** Falso mientras no se haya puesto el archivo google-services.json del proyecto de Firebase al compilar. */
  val configured = BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
    BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty()

  /** Sesión abierta; null si nadie ha ingresado. Firebase la conserva entre aperturas de la app. */
  val user = MutableStateFlow<AuthUser?>(null)

  private var auth: FirebaseAuth? = null

  fun init(context: Context) {
    if (!configured || auth != null) return
    val app = context.applicationContext
    if (FirebaseApp.getApps(app).isEmpty()) {
      FirebaseApp.initializeApp(
        app,
        FirebaseOptions.Builder()
          .setApplicationId(BuildConfig.FIREBASE_APP_ID)
          .setApiKey(BuildConfig.FIREBASE_API_KEY)
          .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
          .build(),
      )
    }
    auth = FirebaseAuth.getInstance().also { fb ->
      user.value = fb.currentUser?.toUser()
      fb.addAuthStateListener { user.value = it.currentUser?.toUser() }
    }
  }

  /**
   * Abre la pantalla de Google para elegir la cuenta. Devuelve null si la persona la cerró sin elegir.
   * [activity] debe ser la actividad a la vista: sobre ella se dibuja la pantalla de Google.
   */
  suspend fun signIn(activity: Context): AuthUser? {
    val fb = auth ?: throw AuthException("Falta conectar la app con Firebase (archivo google-services.json).")
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
      .build()
    val idToken = try {
      val credential = CredentialManager.create(activity).getCredential(activity, request).credential
      if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        throw AuthException("Google devolvió una credencial que la app no reconoce.")
      }
      GoogleIdTokenCredential.createFrom(credential.data).idToken
    } catch (e: GetCredentialCancellationException) {
      return null
    } catch (e: NoCredentialException) {
      throw AuthException("No encontré una cuenta de Google en este teléfono. Agrégala en los ajustes del teléfono e inténtalo de nuevo.")
    } catch (e: GetCredentialException) {
      throw AuthException("Google no permitió el ingreso. ${e.message.orEmpty()}")
    } catch (e: GoogleIdTokenParsingException) {
      throw AuthException("No se pudo leer la respuesta de Google.")
    }
    val signed = suspendCancellableCoroutine<FirebaseUser?> { cont ->
      fb.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
        .addOnSuccessListener { cont.resume(it.user) }
        .addOnFailureListener {
          cont.resumeWithException(
            AuthException(if (it is FirebaseNetworkException) "Sin conexión a internet." else "Firebase no aceptó el ingreso. ${it.message.orEmpty()}"),
          )
        }
    }
    return signed?.toUser()?.also { user.value = it }
  }

  /** Cierra la sesión en Firebase y hace que Google vuelva a preguntar con qué cuenta entrar. */
  suspend fun signOut(context: Context) {
    try {
      kotlinx.coroutines.withTimeoutOrNull(4_000) {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }
    } catch (e: Exception) {
      if (e is kotlinx.coroutines.CancellationException) throw e
    }
    auth?.signOut()
    user.value = null
  }

  /** Identificador de la sesión abierta, o null; se puede llamar desde cualquier hilo después de [init]. */
  fun currentUid(): String? = auth?.currentUser?.uid

  /** Correo de la sesión abierta, o null. */
  fun currentEmail(): String? = auth?.currentUser?.email?.lowercase()

  private fun FirebaseUser.toUser() = AuthUser(uid, displayName.orEmpty(), email.orEmpty())
}
