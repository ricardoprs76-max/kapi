package com.aura.finanzas.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {
  @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort, id") fun accounts(): Flow<List<Account>>
  @Query("SELECT * FROM categories ORDER BY sort, id") fun categories(): Flow<List<Category>>
  @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC") fun transactions(): Flow<List<Tx>>
  @Query("SELECT * FROM assets ORDER BY value DESC") fun assets(): Flow<List<Asset>>
  @Query("SELECT * FROM debts ORDER BY balance DESC") fun debts(): Flow<List<Debt>>
  @Query("SELECT * FROM budgets") fun budgets(): Flow<List<Budget>>
  @Query("SELECT * FROM fixed_expenses ORDER BY dayOfMonth") fun fixedExpenses(): Flow<List<FixedExpense>>
  @Query("SELECT * FROM goals ORDER BY id") fun goals(): Flow<List<Goal>>

  @Query("SELECT * FROM ai_notes ORDER BY date, id") fun aiNotes(): Flow<List<AiNote>>

  @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
  @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTxById(id: Long)
  @Query("DELETE FROM transactions WHERE accountId = :id OR toAccountId = :id") suspend fun deleteTxsOfAccount(id: Long)

  @Upsert suspend fun upsert(a: Account): Long
  @Upsert suspend fun upsert(c: Category): Long
  @Upsert suspend fun upsertCategories(c: List<Category>)
  @Upsert suspend fun upsert(t: Tx): Long
  @Upsert suspend fun upsert(a: Asset): Long
  @Upsert suspend fun upsert(d: Debt): Long
  @Upsert suspend fun upsert(b: Budget): Long
  @Upsert suspend fun upsert(f: FixedExpense): Long
  @Upsert suspend fun upsert(g: Goal): Long
  @Upsert suspend fun upsert(n: AiNote): Long

  @Delete suspend fun delete(a: Account)
  @Delete suspend fun delete(c: Category)
  @Delete suspend fun delete(t: Tx)
  @Delete suspend fun delete(a: Asset)
  @Delete suspend fun delete(d: Debt)
  @Delete suspend fun delete(b: Budget)
  @Delete suspend fun delete(f: FixedExpense)
  @Delete suspend fun delete(g: Goal)
  @Delete suspend fun delete(n: AiNote)
  @Query("DELETE FROM ai_notes WHERE memo = 0") suspend fun clearChat()
  /** Borra del historial los mensajes indicados (lo guardado en la memoria se conserva). */
  @Query("DELETE FROM ai_notes WHERE memo = 0 AND id IN (:ids)") suspend fun deleteNotes(ids: List<Long>)
}

@Database(
  entities = [Account::class, Category::class, Tx::class, Asset::class, Debt::class, Budget::class,
    FixedExpense::class, Goal::class, AiNote::class],
  version = 6,
  exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
  abstract fun dao(): FinanceDao

  companion object {
    @Volatile private var instance: AppDb? = null

    fun get(context: Context): AppDb = instance ?: synchronized(this) {
      // Cada persona que entra tiene su propia base (ver UserSpace); la primera conserva "aura.db".
      instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "aura${UserSpace.ensure(context)}.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
        .also { instance = it }
    }
  }
}

/** v2: columnas para compartir billeteras. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE accounts ADD COLUMN shareId TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE transactions ADD COLUMN uid TEXT NOT NULL DEFAULT ''")
  }
}

/** v3: límite de gasto mensual por cuenta. */
private val MIGRATION_2_3 = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE accounts ADD COLUMN monthlyLimit REAL NOT NULL DEFAULT 0")
  }
}

/** v4: acciones con cantidad, precio de compra y precio de venta. */
private val MIGRATION_3_4 = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE assets ADD COLUMN units REAL")
    db.execSQL("ALTER TABLE assets ADD COLUMN buyPrice REAL")
    db.execSQL("ALTER TABLE assets ADD COLUMN sellPrice REAL")
  }
}

/** v5: conversación y memoria del asistente. */
private val MIGRATION_4_5 = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS `ai_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `date` INTEGER NOT NULL, `memo` INTEGER NOT NULL)")
  }
}

/**
 * v6: presupuestos por cuenta. La tabla se rehace porque el índice único pasa de la categoría sola a categoría +
 * cuenta. Los límites mensuales que las cuentas tenían se vuelven el tope total de gastos de esa cuenta.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS `budgets_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `monthlyLimit` REAL NOT NULL, `alertPct` INTEGER NOT NULL, `accountId` INTEGER NOT NULL DEFAULT 0)")
    db.execSQL("INSERT INTO budgets_new (id, categoryId, monthlyLimit, alertPct, accountId) SELECT id, categoryId, monthlyLimit, alertPct, 0 FROM budgets")
    db.execSQL("DROP TABLE budgets")
    db.execSQL("ALTER TABLE budgets_new RENAME TO budgets")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_accountId` ON `budgets` (`categoryId`, `accountId`)")
    db.execSQL("INSERT OR IGNORE INTO budgets (categoryId, monthlyLimit, alertPct, accountId) SELECT -1, monthlyLimit, 80, id FROM accounts WHERE monthlyLimit > 0")
  }
}

val defaultCategories = listOf(
  Category(name = "Alimentación", icon = "FOOD", colorIdx = 3),
  Category(name = "Mercado", icon = "BAG", colorIdx = 1),
  Category(name = "Transporte", icon = "CAR", colorIdx = 2),
  Category(name = "Vivienda", icon = "HOME", colorIdx = 0),
  Category(name = "Servicios", icon = "BULB", colorIdx = 5),
  Category(name = "Salud", icon = "HEALTH", colorIdx = 6),
  Category(name = "Entretenimiento", icon = "TV", colorIdx = 4),
  Category(name = "Educación", icon = "BOOK", colorIdx = 2),
  Category(name = "Viajes", icon = "SUITCASE", colorIdx = 3),
  Category(name = "Regalos", icon = "GIFT", colorIdx = 4),
  Category(name = "Tecnología", icon = "PHONE", colorIdx = 0),
  Category(name = "Otros gastos", icon = "COIN", colorIdx = 5),
  Category(name = "Salario", kind = TxType.INCOME, icon = "CASH", colorIdx = 1),
  Category(name = "Negocio", kind = TxType.INCOME, icon = "CHART", colorIdx = 2),
  Category(name = "Inversiones", kind = TxType.INCOME, icon = "SAFE", colorIdx = 0),
  Category(name = "Otros ingresos", kind = TxType.INCOME, icon = "COIN", colorIdx = 5),
).mapIndexed { i, c -> c.copy(sort = i) }
