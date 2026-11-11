package ma.aminehanout.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
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
    val amount: Long, // centimes
    val isDebt: Boolean, // true = أعطيت (debt given), false = استلمت (payment received)
    val note: String = "",
    val date: Long = System.currentTimeMillis()
)

data class CustomerRow(val id: Long, val name: String, val phone: String, val balance: Long, val lastDate: Long)

@Dao
interface LedgerDao {
    @Query("SELECT c.id AS id, c.name AS name, c.phone AS phone, COALESCE(SUM(CASE WHEN t.isDebt = 1 THEN t.amount ELSE -t.amount END), 0) AS balance, COALESCE(MAX(t.date), c.createdAt) AS lastDate FROM Customer c LEFT JOIN Txn t ON t.customerId = c.id GROUP BY c.id ORDER BY lastDate DESC")
    fun rows(): Flow<List<CustomerRow>>
    @Query("SELECT * FROM Customer WHERE id = :id") fun customer(id: Long): Flow<Customer?>
    @Query("SELECT * FROM Txn WHERE customerId = :id ORDER BY date ASC, id ASC") fun txns(id: Long): Flow<List<Txn>>
    @Query("SELECT * FROM Txn") fun allTxns(): Flow<List<Txn>>
    @Query("SELECT * FROM Customer") suspend fun customersOnce(): List<Customer>
    @Query("SELECT * FROM Txn") suspend fun txnsOnce(): List<Txn>
    @Insert suspend fun addCustomer(c: Customer): Long
    @Insert suspend fun addTxn(t: Txn)
    @Insert suspend fun addCustomers(l: List<Customer>)
    @Insert suspend fun addTxns(l: List<Txn>)
    @Query("DELETE FROM Txn WHERE id = :id") suspend fun deleteTxn(id: Long)
    @Query("DELETE FROM Txn WHERE customerId = :id") suspend fun deleteTxnsOf(id: Long)
    @Query("DELETE FROM Customer WHERE id = :id") suspend fun deleteCustomerRow(id: Long)
    @Query("DELETE FROM Customer") suspend fun clearCustomers()
    @Query("DELETE FROM Txn") suspend fun clearTxns()
    @Transaction suspend fun deleteCustomer(id: Long) { deleteTxnsOf(id); deleteCustomerRow(id) }
    @Transaction suspend fun replaceAll(c: List<Customer>, t: List<Txn>) { clearTxns(); clearCustomers(); addCustomers(c); addTxns(t) }
}

@Database(entities = [Customer::class, Txn::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LedgerDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "amine_hanout.db").build().also { inst = it }
        }
    }
}

// ---------- helpers ----------
fun fmt(cents: Long): String = String.format(Locale.US, "%,.2f", cents / 100.0) + " DH"
fun day(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))

fun parseAmount(s: String): Long? {
    val n = s.map { if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("").trim().replace(',', '.')
    return n.toBigDecimalOrNull()?.multiply(BigDecimal(100))?.toLong()?.takeIf { it > 0 }
}

fun normPhone(p: String): String {
    var d = p.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.startsWith("0") && d.length == 10) d = "212" + d.drop(1)
    return d
}

fun waText(shop: String, name: String, txns: List<Txn>): String {
    val bal = txns.sumOf { if (it.isDebt) it.amount else -it.amount }
    val sb = StringBuilder("مرحبا $name،\nكشف حسابك لدى $shop:\n\n")
    txns.takeLast(10).forEach {
        sb.append("${day(it.date)}  ${if (it.isDebt) "دين" else "دفعة"}  ${fmt(it.amount)}")
        if (it.note.isNotBlank()) sb.append("  (${it.note})")
        sb.append("\n")
    }
    sb.append("\n")
    sb.append(if (bal > 0) "المبلغ المتبقي: ${fmt(bal)}" else "لا يوجد مبلغ مستحق ✅")
    sb.append("\nشكراً لك 🙏")
    return sb.toString()
}

fun toJson(c: List<Customer>, t: List<Txn>): String {
    val o = JSONObject()
    o.put("customers", JSONArray().also { a -> c.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("createdAt", it.createdAt)) } })
    o.put("txns", JSONArray().also { a -> t.forEach { a.put(JSONObject().put("id", it.id).put("customerId", it.customerId).put("amount", it.amount).put("isDebt", it.isDebt).put("note", it.note).put("date", it.date)) } })
    return o.toString()
}

fun fromJson(s: String): Pair<List<Customer>, List<Txn>> {
    val o = JSONObject(s)
    val ca = o.getJSONArray("customers"); val ta = o.getJSONArray("txns")
    val c = (0 until ca.length()).map { ca.getJSONObject(it).let { j -> Customer(j.getLong("id"), j.getString("name"), j.getString("phone"), j.getLong("createdAt")) } }
    val t = (0 until ta.length()).map { ta.getJSONObject(it).let { j -> Txn(j.getLong("id"), j.getLong("customerId"), j.getLong("amount"), j.getBoolean("isDebt"), j.getString("note"), j.getLong("date")) } }
    return c to t
}
