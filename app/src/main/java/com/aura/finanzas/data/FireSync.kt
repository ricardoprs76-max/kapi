package com.aura.finanzas.data

import android.content.Context
import androidx.room.withTransaction
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Error de sincronización con un mensaje listo para mostrar. */
class CloudException(message: String) : Exception(message)

/** Persona con acceso a una cuenta compartida. */
class SharedPerson(val email: String, val owner: Boolean)

/**
 * Datos en la nube de Kapi (Firebase Firestore, el mismo proyecto con el que se entra con Google). Nada va a
 * Google Drive ni se manda por archivos.
 *
 * - users/{uid}: la copia completa de los datos de cada persona (solo esa persona puede leerla o escribirla).
 *   El texto se parte en trozos (users/{uid}/parts/{n}) porque un documento admite como máximo 1 MB.
 * - shared/{shareId}: cada cuenta compartida, con quién la creó ("owner") y quiénes la pueden ver ("members",
 *   por correo). Solo esas personas pueden leerla o escribirla; el resto de los datos de cada una no se comparte.
 *
 * Las reglas de seguridad que imponen esto están en web/firestore.rules.
 */
object FireSync {
  private const val PART = 700_000
  private val ownMutex = Mutex()
  private val sharedMutex = Mutex()

  enum class Outcome { UPLOADED, DOWNLOADED, UP_TO_DATE, CONFLICT }

  private fun db() = FirebaseFirestore.getInstance()

  private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
  }

  /** Correo y uid de la sesión; sin sesión no se sincroniza nada. */
  private fun session(context: Context): Pair<String, String>? {
    Auth.init(context)
    val uid = Auth.currentUid() ?: return null
    if (UserSpace.needsRestart(context, uid)) return null
    return uid to (Auth.currentEmail() ?: "")
  }

  fun signedIn(context: Context) = session(context) != null

  private fun describe(e: Exception): String = when {
    e is CloudException -> e.message ?: "No se pudo sincronizar."
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE -> "Sin conexión. Se reintentará sola."
    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> "La nube de Kapi negó el acceso. Cierra sesión y vuelve a entrar."
    else -> "No se pudo sincronizar: ${e.message ?: e.javaClass.simpleName}"
  }

  private inline fun <T> guard(block: () -> T): T = try {
    block()
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: CloudException) {
    throw e
  } catch (e: Exception) {
    throw CloudException(describe(e))
  }

  private fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

  // ---------- Copia de los datos de la persona ----------

  private class Remote(val md5: String, val parts: Int)

  private suspend fun readRemote(uid: String): Remote? {
    val doc = db().collection("users").document(uid).get().await()
    if (!doc.exists()) return null
    return Remote(doc.getString("md5") ?: "", (doc.getLong("parts") ?: 0L).toInt())
  }

  private suspend fun download(uid: String, remote: Remote): String {
    val parts = db().collection("users").document(uid).collection("parts").get().await()
    val byIndex = parts.documents.associate { it.id.toIntOrNull() to (it.getString("t") ?: "") }
    if ((0 until remote.parts).any { it !in byIndex }) throw CloudException("La copia en la nube está incompleta; se reintentará.")
    return (0 until remote.parts).joinToString("") { byIndex.getValue(it) }
  }

  private suspend fun upload(uid: String, text: String, hash: String) {
    val user = db().collection("users").document(uid)
    val chunks = text.chunked(PART)
    // Primero los trozos y al final el documento principal: si algo falla a medias, la copia anterior sigue entera.
    val batch = db().batch()
    chunks.forEachIndexed { i, c -> batch.set(user.collection("parts").document(i.toString()), mapOf("t" to c)) }
    batch.commit().await()
    user.set(mapOf("md5" to hash, "parts" to chunks.size, "email" to (Auth.currentEmail() ?: ""), "updatedAt" to FieldValue.serverTimestamp())).await()
  }

  /**
   * Sincroniza la copia de la persona una vez. Se recuerdan la huella de la nube y la de los datos locales en la
   * última sincronización: así se sabe si cambió solo el teléfono (se sube), solo la nube (se baja) o ambos
   * (conflicto, que decide la persona con [resolve] = "LOCAL" o "REMOTE").
   */
  suspend fun syncOwn(context: Context, prefs: Prefs, resolve: String? = null): Outcome = withContext(Dispatchers.IO) {
    ownMutex.withLock {
      guard {
        val (uid, _) = session(context) ?: throw CloudException("Entra con tu cuenta de Google para guardar en la nube.")
        val settings = prefs.flow.first()
        val local = DataTools.snapshot(context)
        val localHash = md5(local.toByteArray())
        val remote = readRemote(uid)
        val localChanged = localHash != settings.cloudLocalHash
        val remoteChanged = remote != null && remote.md5 != settings.cloudRemoteMd5

        suspend fun done(remoteMd5: String, hash: String, outcome: Outcome): Outcome {
          prefs.set(Keys.cloudRemoteMd5, remoteMd5)
          prefs.set(Keys.cloudLocalHash, hash)
          prefs.set(Keys.cloudLastSync, System.currentTimeMillis())
          return outcome
        }
        suspend fun pull(r: Remote): Outcome {
          DataTools.applySnapshot(context, download(uid, r))
          return done(r.md5, md5(DataTools.snapshot(context).toByteArray()), Outcome.DOWNLOADED)
        }
        suspend fun push(): Outcome { upload(uid, local, localHash); return done(localHash, localHash, Outcome.UPLOADED) }

        when {
          remote == null -> push()
          remote.md5 == localHash -> done(localHash, localHash, Outcome.UP_TO_DATE)
          resolve == "REMOTE" -> pull(remote)
          resolve == "LOCAL" -> push()
          !localChanged && !remoteChanged -> Outcome.UP_TO_DATE
          !localChanged -> pull(remote)
          !remoteChanged -> push()
          // Teléfono recién instalado y sin datos: lo natural es traer lo que hay en la nube.
          DataTools.isEmpty(local) -> pull(remote)
          else -> Outcome.CONFLICT
        }
      }
    }
  }

  // ---------- Cuentas compartidas ----------

  private fun baseFile(context: Context) = File(context.filesDir, "compartidas${UserSpace.ensure(context)}.json")
  private fun loadBase(context: Context): JSONObject = runCatching { JSONObject(baseFile(context).readText()) }.getOrDefault(JSONObject())

  private fun hash(type: String, amount: Double, date: Long, note: String, tags: String, category: String): String =
    md5("$type|${amount.toLong()}|$date|$note|$tags|$category".toByteArray())

  /** Con quién está cada cuenta compartida (por su shareId), según la última sincronización. */
  fun people(context: Context): Map<String, List<SharedPerson>> {
    val base = loadBase(context)
    val out = HashMap<String, List<SharedPerson>>()
    for (key in base.keys()) {
      val entry = base.optJSONObject(key) ?: continue
      val owner = entry.optString("owner")
      val members = entry.optJSONArray("members") ?: continue
      out[key] = (0 until members.length()).map { members.getString(it) }.map { SharedPerson(it, it == owner) }
    }
    return out
  }

  /** Comparte la cuenta [accountId] con [email]: la sube a la nube (si aún no estaba) y le da acceso a esa persona. */
  suspend fun invite(context: Context, accountId: Long, email: String) = withContext(Dispatchers.IO) {
    sharedMutex.withLock {
      guard {
        val (_, me) = session(context) ?: throw CloudException("Entra con tu cuenta de Google para compartir.")
        if (email.equals(me, ignoreCase = true)) throw CloudException("Ese es tu propio correo; escribe el de la otra persona.")
        val dao = AppDb.get(context).dao()
        var account = dao.accounts().first().firstOrNull { it.id == accountId } ?: throw CloudException("La cuenta ya no existe.")
        if (account.shareId.isEmpty()) {
          account = account.copy(shareId = UUID.randomUUID().toString())
          dao.upsert(account)
        }
        val base = loadBase(context)
        val doc = db().collection("shared").document(account.shareId)
        val snap = doc.get().await()
        syncOne(context, me, account, if (snap.exists()) snap.data else null, base, account.shareId)
        doc.update("members", FieldValue.arrayUnion(email.lowercase())).await()
        base.optJSONObject(account.shareId)?.let { entry ->
          val members = entry.optJSONArray("members") ?: JSONArray().also { entry.put("members", it) }
          if ((0 until members.length()).none { members.getString(it) == email.lowercase() }) members.put(email.lowercase())
        }
        baseFile(context).writeText(base.toString())
      }
    }
  }

  /** La persona eliminó la cuenta compartida [shareId] de este teléfono: no se vuelve a crear sola. */
  fun leave(context: Context, shareId: String) {
    if (shareId.isEmpty()) return
    val base = loadBase(context)
    val left = base.optJSONArray("_left") ?: JSONArray().also { base.put("_left", it) }
    if ((0 until left.length()).none { left.getString(it) == shareId }) left.put(shareId)
    base.remove(shareId)
    baseFile(context).writeText(base.toString())
  }

  /**
   * Avisa con [onChange] cada vez que en la nube aparece o cambia una cuenta compartida con esta persona, para que
   * la cuenta se cree sola en el teléfono del colaborador apenas se la comparten (mientras la app está abierta).
   */
  fun watchShared(context: Context, onChange: () -> Unit): ListenerRegistration? {
    val (_, me) = session(context) ?: return null
    if (me.isEmpty()) return null
    return db().collection("shared").whereArrayContains("members", me).addSnapshotListener { snap, _ ->
      if (snap != null && !snap.metadata.hasPendingWrites() && snap.documentChanges.isNotEmpty()) onChange()
    }
  }

  /**
   * Pone al día todas las cuentas compartidas (las propias y las que otras personas compartieron con este correo).
   * Devuelve cuántos movimientos cambiaron en este teléfono.
   */
  suspend fun syncShared(context: Context): Int = withContext(Dispatchers.IO) {
    sharedMutex.withLock {
      guard {
        val (_, me) = session(context) ?: return@guard 0
        if (me.isEmpty()) return@guard 0
        val dao = AppDb.get(context).dao()
        val base = loadBase(context)
        val left = base.optJSONArray("_left") ?: JSONArray().also { base.put("_left", it) }
        val leftIds = (0 until left.length()).map { left.getString(it) }.toMutableSet()
        val accounts = dao.accounts().first()
        val remote = db().collection("shared").whereArrayContains("members", me).get().await()
        val seen = HashSet<String>()
        var changed = 0
        for (d in remote.documents) {
          seen += d.id
          if (d.id in leftIds) continue
          val account = accounts.firstOrNull { it.shareId == d.id }
          changed += IntArray(1).also { syncOne(context, me, account, d.data, base, d.id, it) }[0]
        }
        // Cuentas compartidas propias que aún no están en la nube (por ejemplo, de una versión anterior): se suben.
        for (a in accounts) {
          if (a.shareId.isNotEmpty() && a.shareId !in seen && a.shareId !in leftIds && base.optJSONObject(a.shareId)?.optString("owner").let { it == null || it == me }) {
            syncOne(context, me, a, null, base, a.shareId)
          }
        }
        baseFile(context).writeText(base.toString())
        changed
      }
    }
  }

  /**
   * Fusiona una cuenta con su documento en la nube. [local] null = la cuenta aún no existe aquí (invitación
   * recién recibida); [remote] null = aún no está en la nube (se crea con esta persona como dueña).
   */
  private suspend fun syncOne(
    context: Context, me: String, local: Account?, remote: Map<String, Any?>?, base: JSONObject, shareId: String, count: IntArray = IntArray(1),
  ) {
    val room = AppDb.get(context)
    val dao = room.dao()
    val remoteText = remote?.get("data") as? String
    val data = remoteText?.let { runCatching { JSONObject(it) }.getOrNull() }
    val remoteRows = HashMap<String, JSONObject>()
    data?.optJSONArray("txs")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { remoteRows[it.getString("uid")] = it } }
    val deleted = HashSet<String>()
    data?.optJSONArray("deleted")?.let { a -> for (i in 0 until a.length()) deleted += a.getString(i) }

    // Si la cuenta no está en el teléfono (invitación nueva o copia restaurada sin ella) se crea con todo lo de la nube.
    val known = if (local == null) JSONObject() else base.optJSONObject(shareId)?.optJSONObject("uids") ?: JSONObject()
    val nextKnown = JSONObject()
    val rows = ArrayList<JSONObject>()
    var account = local
    room.withTransaction {
      if (account == null) {
        val shared = data?.optJSONObject("account") ?: throw CloudException("La cuenta compartida está dañada en la nube.")
        val fresh = Account(
          name = shared.optString("name", "Cuenta compartida"), type = shared.optString("type", AccountType.CASH),
          initialBalance = shared.optDouble("initialBalance", 0.0), icon = shared.optString("icon", "WALLET"),
          colorIdx = shared.optInt("colorIdx", 0), shareId = shareId, sort = 999,
        )
        account = fresh.copy(id = dao.upsert(fresh))
        count[0]++
      }
      val accountId = account!!.id
      val categories = dao.categories().first().toMutableList()
      val byId = categories.associateBy { it.id }
      val now = System.currentTimeMillis()
      val mine = dao.transactions().first().filter { it.accountId == accountId && it.type != TxType.TRANSFER }.map { t ->
        if (t.uid.isNotEmpty()) t else t.copy(uid = UUID.randomUUID().toString()).also { dao.upsert(it) }
      }.associateBy { it.uid }
      // Lo que estaba en la última sincronización y ya no está en el teléfono se borró aquí.
      for (uid in known.keys()) if (uid !in mine) deleted += uid

      fun row(t: Tx, h: String, m: Long): JSONObject {
        val row = JSONObject().put("uid", t.uid).put("type", t.type).put("amount", t.amount).put("date", t.date).put("note", t.note).put("tags", t.tags).put("m", m).put("h", h)
        byId[t.categoryId]?.let { c -> row.put("category", JSONObject().put("name", c.name).put("kind", c.kind).put("icon", c.icon).put("colorIdx", c.colorIdx)) }
        return row
      }
      suspend fun categoryOf(r: JSONObject): Long? = r.optJSONObject("category")?.let { c ->
        val kind = c.optString("kind", TxType.EXPENSE)
        categories.firstOrNull { it.kind == kind && it.name.equals(c.getString("name"), ignoreCase = true) }?.id ?: run {
          val created = Category(name = c.getString("name"), kind = kind, icon = c.optString("icon", "COIN"), colorIdx = c.optInt("colorIdx", 0), sort = 999)
          dao.upsert(created).also { id -> categories += created.copy(id = id) }
        }
      }

      for (uid in mine.keys + remoteRows.keys) {
        val l = mine[uid]
        val r = remoteRows[uid]
        if (uid in deleted) {
          if (l != null) { dao.deleteTxById(l.id); count[0]++ }
          continue
        }
        val lh = l?.let { hash(it.type, it.amount, it.date, it.note, it.tags, byId[it.categoryId]?.name ?: "") }
        val lm = if (l == null) 0L else known.optJSONObject(uid)?.takeIf { it.optString("h") == lh }?.optLong("m") ?: now
        val takeRemote = r != null && (l == null || (r.optString("h") != lh && r.optLong("m") >= lm))
        if (takeRemote) {
          val tx = Tx(
            id = l?.id ?: 0, type = r!!.getString("type"), amount = r.getDouble("amount"), accountId = accountId, categoryId = categoryOf(r),
            note = r.optString("note"), date = r.getLong("date"), tags = r.optString("tags"), source = l?.source ?: "COMPARTIDO", uid = uid,
          )
          dao.upsert(tx)
          count[0]++
          rows += r
          nextKnown.put(uid, JSONObject().put("h", r.optString("h")).put("m", r.optLong("m")))
        } else {
          rows += row(l!!, lh!!, lm)
          nextKnown.put(uid, JSONObject().put("h", lh).put("m", lm))
        }
      }
    }

    val a = account!!
    rows.sortBy { it.getString("uid") }
    val out = JSONObject()
      .put("account", data?.optJSONObject("account") ?: JSONObject().put("name", a.name).put("type", a.type).put("icon", a.icon).put("colorIdx", a.colorIdx).put("initialBalance", a.initialBalance))
      .put("txs", JSONArray(rows)).put("deleted", JSONArray(deleted.sorted()))
    val text = out.toString()
    val doc = db().collection("shared").document(shareId)
    var owner = remote?.get("owner") as? String ?: me
    @Suppress("UNCHECKED_CAST")
    var members = (remote?.get("members") as? List<String>) ?: listOf(me)
    if (remote == null) {
      doc.set(mapOf("owner" to me, "members" to listOf(me), "data" to text, "updatedAt" to FieldValue.serverTimestamp())).await()
      owner = me; members = listOf(me)
    } else if (text != remoteText) {
      doc.set(mapOf("data" to text, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }
    base.put(shareId, JSONObject().put("uids", nextKnown).put("owner", owner).put("members", JSONArray(members)))
  }
}
