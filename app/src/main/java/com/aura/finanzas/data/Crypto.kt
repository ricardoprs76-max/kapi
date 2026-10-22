package com.aura.finanzas.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de las claves de API con una llave AES que vive en el Keystore de Android: la llave no
 * sale del teléfono ni puede leerse desde fuera de la app.
 */
object Crypto {
  private const val ALIAS = "aura_api_keys"
  private const val STORE = "AndroidKeyStore"

  private fun key(): SecretKey {
    val store = KeyStore.getInstance(STORE).apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
    val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE).apply { init(spec) }.generateKey()
  }

  /** Devuelve "iv:texto cifrado" en Base64; una cadena vacía se guarda vacía. */
  fun encrypt(plain: String): String {
    if (plain.isEmpty()) return ""
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    val data = cipher.doFinal(plain.toByteArray())
    return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP)
  }

  /** Texto original, o null si no se puede descifrar (por ejemplo, tras restaurar la app en otro teléfono). */
  fun decrypt(stored: String): String? {
    if (stored.isEmpty()) return ""
    val parts = stored.split(":")
    if (parts.size != 2) return null
    return try {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
      String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    } catch (e: GeneralSecurityException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
  }
}
