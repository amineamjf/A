package ma.aminehanout.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val amount: Long, // centimes of dirham (1 DH = 100 centimes = 20 ryal, so 1 ryal = 5 centimes)
    val isDebt: Boolean, // true = أعطيت (debt/purchase given), false = استلمت (payment received)
    val note: String = "",
    val date: Long = System.currentTimeMillis()
)

// One line of a multi-item purchase. Txn.amount is always the sum of its items (when it has any).
@Entity
data class TxnItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: Long,
    val name: String = "",
    val amount: Long // centimes
)

data class CustomerRow(val id: Long, val name: String, val phone: String, val balance: Long, val lastDate: Long)

@Dao
interface LedgerDao {
    @Query("SELECT c.id AS id, c.name AS name, c.phone AS phone, COALESCE(SUM(CASE WHEN t.isDebt = 1 THEN t.amount ELSE -t.amount END), 0) AS balance, COALESCE(MAX(t.date), c.createdAt) AS lastDate FROM Customer c LEFT JOIN Txn t ON t.customerId = c.id GROUP BY c.id ORDER BY lastDate DESC")
    fun rows(): Flow<List<CustomerRow>>
    @Query("SELECT * FROM Customer WHERE id = :id") fun customer(id: Long): Flow<Customer?>
    @Query("SELECT * FROM Txn WHERE customerId = :id ORDER BY date ASC, id ASC") fun txns(id: Long): Flow<List<Txn>>
    @Query("SELECT * FROM TxnItem WHERE txnId IN (SELECT id FROM Txn WHERE customerId = :id) ORDER BY id ASC") fun itemsOf(id: Long): Flow<List<TxnItem>>
    @Query("SELECT * FROM Txn") fun allTxns(): Flow<List<Txn>>
    @Query("SELECT * FROM Customer") suspend fun customersOnce(): List<Customer>
    @Query("SELECT * FROM Txn") suspend fun txnsOnce(): List<Txn>
    @Query("SELECT * FROM TxnItem") suspend fun itemsOnce(): List<TxnItem>
    @Insert suspend fun addCustomer(c: Customer): Long
    @Insert suspend fun addTxn(t: Txn): Long
    @Insert suspend fun addItems(l: List<TxnItem>)
    @Insert suspend fun addCustomers(l: List<Customer>)
    @Insert suspend fun addTxns(l: List<Txn>)
    @Query("DELETE FROM Txn WHERE id = :id") suspend fun deleteTxn(id: Long)
    @Query("DELETE FROM TxnItem WHERE txnId = :id") suspend fun deleteItemsOfTxn(id: Long)
    @Query("DELETE FROM TxnItem WHERE txnId IN (SELECT id FROM Txn WHERE customerId = :id)") suspend fun deleteItemsOfCustomer(id: Long)
    @Query("DELETE FROM Txn WHERE customerId = :id") suspend fun deleteTxnsOf(id: Long)
    @Query("DELETE FROM Customer WHERE id = :id") suspend fun deleteCustomerRow(id: Long)
    @Query("DELETE FROM Customer") suspend fun clearCustomers()
    @Query("DELETE FROM Txn") suspend fun clearTxns()
    @Query("DELETE FROM TxnItem") suspend fun clearItems()
    @Transaction suspend fun addPurchase(t: Txn, names: List<String>, amounts: List<Long>) {
        val id = addTxn(t)
        if (names.isNotEmpty()) addItems(names.indices.map { TxnItem(txnId = id, name = names[it], amount = amounts[it]) })
    }
    @Transaction suspend fun deleteTxnFull(id: Long) { deleteItemsOfTxn(id); deleteTxn(id) }
    @Transaction suspend fun deleteCustomer(id: Long) { deleteItemsOfCustomer(id); deleteTxnsOf(id); deleteCustomerRow(id) }
    @Transaction suspend fun replaceAll(c: List<Customer>, t: List<Txn>, items: List<TxnItem>) {
        clearItems(); clearTxns(); clearCustomers(); addCustomers(c); addTxns(t); addItems(items)
    }
}

// v1 -> v2: adds the TxnItem table only. Existing customers and transactions are untouched.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `TxnItem` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `txnId` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL)")
    }
}

@Database(entities = [Customer::class, Txn::class, TxnItem::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "amine_hanout.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { inst = it }
        }
    }
}

// ---------- money (all exact BigDecimal / Long, never Double) ----------
// 1 DH = 20 ryal  =>  1 ryal = 0.05 DH = 5 centimes.
const val CENTS_PER_RYAL = 5L
const val UNIT_RYAL = "ريال"
const val UNIT_DH = "درهم"

fun ryalOf(cents: Long): BigDecimal = BigDecimal(cents).divide(BigDecimal(CENTS_PER_RYAL)) // always terminates (scale <= 1)
fun dhOf(cents: Long): BigDecimal = BigDecimal(cents).movePointLeft(2)

private fun numStr(v: BigDecimal, decimals: Int): String {
    val isInt = v.stripTrailingZeros().scale() <= 0
    val pattern = if (isInt) "#,##0" else "#,##0." + "0".repeat(decimals)
    return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(v)
}

fun ryalText(cents: Long): String = numStr(ryalOf(cents), 1) + " " + UNIT_RYAL
fun dhText(cents: Long): String = numStr(dhOf(cents), 2) + " " + UNIT_DH

fun fmtPrimary(cents: Long, ryalFirst: Boolean): String = if (ryalFirst) ryalText(cents) else dhText(cents)
fun fmtSecondary(cents: Long, ryalFirst: Boolean): String = if (ryalFirst) dhText(cents) else ryalText(cents)
fun fmtBoth(cents: Long, ryalFirst: Boolean): String = fmtPrimary(cents, ryalFirst) + " (" + fmtSecondary(cents, ryalFirst) + ")"

fun day(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))

// Parses user input into centimes. `ryal` = the typed number is in ryal, otherwise in dirham.
fun parseAmount(s: String, ryal: Boolean): Long? {
    val n = s.map {
        when (it) {
            in '٠'..'٩' -> '0' + (it - '٠')
            '٫', ',' -> '.'
            else -> it
        }
    }.joinToString("").trim()
    val v = n.toBigDecimalOrNull() ?: return null
    if (v.signum() <= 0) return null
    val cents = if (ryal) v.multiply(BigDecimal(CENTS_PER_RYAL)) else v.multiply(BigDecimal(100))
    return cents.setScale(0, RoundingMode.HALF_UP).toLong().takeIf { it > 0 }
}

fun balanceOf(txns: List<Txn>): Long = txns.sumOf { if (it.isDebt) it.amount else -it.amount }

fun normPhone(p: String): String {
    var d = p.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.startsWith("0") && d.length == 10) d = "212" + d.drop(1)
    return d
}

fun waText(shop: String, name: String, txns: List<Txn>, items: List<TxnItem> = emptyList(), ryalFirst: Boolean = true): String {
    val bal = balanceOf(txns)
    val byTxn = items.groupBy { it.txnId }
    val sb = StringBuilder("مرحبا $name،\nكشف حسابك لدى $shop:\n\n")
    txns.takeLast(10).forEach {
        sb.append("${day(it.date)}  ${if (it.isDebt) "دين" else "دفعة"}  ${fmtBoth(it.amount, ryalFirst)}")
        if (it.note.isNotBlank()) sb.append("  (${it.note})")
        sb.append("\n")
        byTxn[it.id]?.forEach { i -> sb.append("   • ${if (i.name.isBlank()) "—" else i.name}: ${fmtBoth(i.amount, ryalFirst)}\n") }
    }
    sb.append("\n")
    sb.append(if (bal > 0) "المبلغ المتبقي: ${fmtBoth(bal, ryalFirst)}" else "لا يوجد مبلغ مستحق ✅")
    sb.append("\nشكراً لك 🙏")
    return sb.toString()
}

// ---------- backup ----------
data class Backup(val customers: List<Customer>, val txns: List<Txn>, val items: List<TxnItem>)

fun toJson(c: List<Customer>, t: List<Txn>, items: List<TxnItem>): String {
    val o = JSONObject()
    o.put("version", 2)
    o.put("customers", JSONArray().also { a -> c.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("createdAt", it.createdAt)) } })
    o.put("txns", JSONArray().also { a -> t.forEach { a.put(JSONObject().put("id", it.id).put("customerId", it.customerId).put("amount", it.amount).put("isDebt", it.isDebt).put("note", it.note).put("date", it.date)) } })
    o.put("items", JSONArray().also { a -> items.forEach { a.put(JSONObject().put("id", it.id).put("txnId", it.txnId).put("name", it.name).put("amount", it.amount)) } })
    return o.toString()
}

// Reads both the old format (customers + txns only) and the new one (adds items).
fun fromJson(s: String): Backup {
    val o = JSONObject(s)
    val ca = o.getJSONArray("customers"); val ta = o.getJSONArray("txns")
    val ia = o.optJSONArray("items")
    val c = (0 until ca.length()).map { ca.getJSONObject(it).let { j -> Customer(j.getLong("id"), j.getString("name"), j.optString("phone", ""), j.getLong("createdAt")) } }
    val t = (0 until ta.length()).map { ta.getJSONObject(it).let { j -> Txn(j.getLong("id"), j.getLong("customerId"), j.getLong("amount"), j.getBoolean("isDebt"), j.optString("note", ""), j.getLong("date")) } }
    val items = if (ia == null) emptyList() else (0 until ia.length()).map { ia.getJSONObject(it).let { j -> TxnItem(j.getLong("id"), j.getLong("txnId"), j.optString("name", ""), j.getLong("amount")) } }
    return Backup(c, t, items)
}
