package com.aura.finanzas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Los tipos se guardan como texto para no depender de conversores ni del orden de un enum.

object AccountType {
  const val CASH = "EFECTIVO"
  const val BANK = "BANCO"
  const val WALLET = "BILLETERA"
  const val CARD = "TARJETA"
  const val SAVINGS = "AHORRO"
  val all = listOf(CASH, BANK, WALLET, CARD, SAVINGS)
  fun label(t: String) = when (t) {
    CASH -> "Efectivo"; BANK -> "Banco"; WALLET -> "Billetera"; CARD -> "Tarjeta"; else -> "Ahorro"
  }
  fun icon(t: String) = when (t) {
    CASH -> "CASH"; BANK -> "BANK"; WALLET -> "WALLET"; CARD -> "CARD"; else -> "SAFE"
  }
}

object TxType {
  const val EXPENSE = "GASTO"
  const val INCOME = "INGRESO"
  const val TRANSFER = "TRANSFER"
}

@Entity(tableName = "accounts")
data class Account(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: String = AccountType.CASH,
  val initialBalance: Double = 0.0,
  val icon: String = "WALLET",
  val colorIdx: Int = 0,
  /** Cuenta para la liquidez (dinero disponible); si es false solo suma al patrimonio. */
  val liquid: Boolean = true,
  val archived: Boolean = false,
  val sort: Int = 0,
  /** Identificador de la billetera al compartirla; vacío mientras no se haya compartido. */
  @ColumnInfo(defaultValue = "") val shareId: String = "",
  /** Tope de gasto mensual de la cuenta; 0 = sin límite. */
  @ColumnInfo(defaultValue = "0") val monthlyLimit: Double = 0.0,
)

@Entity(tableName = "categories")
data class Category(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val kind: String = TxType.EXPENSE,
  val icon: String = "COIN",
  val colorIdx: Int = 0,
  val sort: Int = 0,
)

@Entity(tableName = "transactions", indices = [Index("date"), Index("accountId"), Index("categoryId")])
data class Tx(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: String,
  val amount: Double,
  val accountId: Long,
  val toAccountId: Long? = null,
  val categoryId: Long? = null,
  val note: String = "",
  val date: Long = System.currentTimeMillis(),
  val tags: String = "",
  /** MANUAL, RECIBO o VOZ. */
  val source: String = "MANUAL",
  val fixedId: Long? = null,
  /** Identificador estable del movimiento entre teléfonos; se asigna al compartir su billetera. */
  @ColumnInfo(defaultValue = "") val uid: String = "",
)

@Entity(tableName = "assets")
data class Asset(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  /** INMUEBLE, INVERSION, VEHICULO, AHORRO, CDT, ACCIONES u OTRO. */
  val kind: String,
  val value: Double,
  val icon: String = "HOME",
  val ratePct: Double? = null,
  val startDate: Long? = null,
  val endDate: Long? = null,
  val note: String = "",
  /** Solo para acciones: cantidad, precio de compra y precio de venta (o actual) por unidad. */
  val units: Double? = null,
  val buyPrice: Double? = null,
  val sellPrice: Double? = null,
)

@Entity(tableName = "debts")
data class Debt(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val kind: String = "PRESTAMO",
  val balance: Double,
  val ratePct: Double? = null,
  val monthlyPayment: Double? = null,
  val dueDay: Int? = null,
  val icon: String = "CARD",
)

/** Valor de [Budget.categoryId] para el presupuesto de todos los gastos del mes, sin importar la categoría. */
const val TOTAL_BUDGET = -1L

/** Valor de [Budget.accountId] para un presupuesto que suma los gastos de todas las cuentas. */
const val ALL_ACCOUNTS = 0L

@Entity(tableName = "budgets", indices = [Index(value = ["categoryId", "accountId"], unique = true)])
data class Budget(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  /** Categoría a la que aplica, o [TOTAL_BUDGET] si es el tope de todos los gastos. */
  val categoryId: Long,
  val monthlyLimit: Double,
  val alertPct: Int = 80,
  /** Cuenta cuyos gastos cuenta este presupuesto, o [ALL_ACCOUNTS] para todas. */
  @ColumnInfo(defaultValue = "0") val accountId: Long = ALL_ACCOUNTS,
) {
  val isTotal get() = categoryId == TOTAL_BUDGET
}

/**
 * Conversación con el asistente. [role] es USER o AI. Con [memo] en true es algo que la persona contó para que
 * el asistente lo tenga presente en adelante (un préstamo a un familiar, un negocio, un plan…), aunque no tenga cifras.
 */
@Entity(tableName = "ai_notes")
data class AiNote(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val role: String,
  val text: String,
  val date: Long = System.currentTimeMillis(),
  val memo: Boolean = false,
)

@Entity(tableName = "fixed_expenses")
data class FixedExpense(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val amount: Double,
  val categoryId: Long? = null,
  val accountId: Long? = null,
  val dayOfMonth: Int = 1,
  val subscription: Boolean = false,
  val active: Boolean = true,
  val icon: String = "BULB",
  /** Mes del último pago registrado, con formato yyyy-MM. */
  val lastPaidMonth: String = "",
)

@Entity(tableName = "goals")
data class Goal(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val target: Double,
  val saved: Double = 0.0,
  val deadline: Long? = null,
  val icon: String = "TARGET",
  val colorIdx: Int = 0,
)
