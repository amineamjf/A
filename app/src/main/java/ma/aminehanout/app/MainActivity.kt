@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.aminehanout.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Brand = Color(0xFF0B5D4B)
val Red = Color(0xFFD64545)   // customer owes you / debt given (Khatabook logic)
val Green = Color(0xFF1B8A5A) // payment received / advance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = lightColorScheme(primary = Brand)) {
                    Surface(Modifier.fillMaxSize()) { App(this@MainActivity) }
                }
            }
        }
    }
}

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
fun shop(ctx: Context): String = prefs(ctx).getString("shop", "Amine App") ?: "Amine App"

@Composable
fun App(ctx: Context) {
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    BackHandler(openId != null) { openId = null }
    val id = openId
    if (id != null) { CustomerScreen(ctx, dao, id) { openId = null }; return }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amine App", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Person, null) }, label = { Text("الزبناء") })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("التقارير") })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") })
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = { showAdd = true }, containerColor = Brand, contentColor = Color.White) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("زبون جديد")
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CustomersTab(dao) { openId = it }
                1 -> ReportsTab(dao)
                else -> SettingsTab(ctx, dao)
            }
        }
    }
    if (showAdd) CustomerDialog({ showAdd = false }) { n, p ->
        showAdd = false
        scope.launch { openId = dao.addCustomer(Customer(name = n, phone = p)) }
    }
}

@Composable
fun CustomersTab(dao: LedgerDao, onOpen: (Long) -> Unit) {
    val rows by remember(dao) { dao.rows() }.collectAsState(emptyList())
    var q by remember { mutableStateOf("") }
    val owed = rows.filter { it.balance > 0 }.sumOf { it.balance }
    val adv = rows.filter { it.balance < 0 }.sumOf { -it.balance }
    val list = rows.filter { it.name.contains(q, true) || it.phone.contains(q) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) { Text("لك عند الزبناء", fontSize = 13.sp, color = Color.Gray); Text(fmtP(owed), color = Red, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text(fmtS(owed), fontSize = 12.sp, color = Color.Gray) }
                    Column(Modifier.weight(1f)) { Text("عليك للزبناء", fontSize = 13.sp, color = Color.Gray); Text(fmtP(adv), color = Green, fontWeight = FontWeight.Bold, fontSize = 20.sp); Text(fmtS(adv), fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("بحث عن زبون") }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        if (rows.isEmpty()) item { Text("لا يوجد زبناء بعد. اضغط «زبون جديد» للبدء.", Modifier.padding(24.dp), color = Color.Gray) }
        items(list, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(r.id) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Brand.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Text(r.name.take(1), color = Brand, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(day(r.lastDate), fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtP(kotlin.math.abs(r.balance)), fontWeight = FontWeight.Bold, color = if (r.balance > 0) Red else if (r.balance < 0) Green else Color.Gray)
                        Text(fmtS(kotlin.math.abs(r.balance)), fontSize = 11.sp, color = Color.Gray)
                        Text(if (r.balance > 0) "عليه" else if (r.balance < 0) "له" else "مسدّد", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("زبون جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, Modifier.focusRequester(fr), singleLine = true, label = { Text("الاسم") })
                OutlinedTextField(p, { p = it }, singleLine = true, label = { Text("رقم الهاتف (واتساب)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { Button({ if (n.isNotBlank()) onSave(n.trim(), p.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

class ItemRow(name: String = "", amount: String = "") {
    var name by mutableStateOf(name)
    var amount by mutableStateOf(amount)
}

// Entry dialog. For a debt/purchase (isDebt = true) it supports several items in one operation;
// for a payment it is a single amount, exactly like before.
// onSave(total, note, items): items is empty for a plain single-amount entry.
@Composable
fun EntryDialog(ctx: Context, isDebt: Boolean, prevBalance: Long, onDismiss: () -> Unit, onSave: (Long, String, List<Pair<String, Long>>) -> Unit) {
    val rows = remember { mutableStateListOf(ItemRow()) }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val total = rows.sumOf { parseAmt(it.amount) ?: 0L }
    val after = if (isDebt) prevBalance + total else prevBalance - total
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDebt) "أعطيت (دين / مشتريات جديدة)" else "استلمت (دفعة)", color = if (isDebt) Red else Green) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("العملة:", fontSize = 13.sp, color = Color.Gray)
                    FilterChip(curRyal, { setCurrency(ctx, true) }, { Text(UNIT_RYAL) })
                    FilterChip(!curRyal, { setCurrency(ctx, false) }, { Text(UNIT_DH) })
                }
                rows.forEachIndexed { i, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(row.amount, { row.amount = it; err = "" },
                            Modifier.weight(if (isDebt) 1f else 2f).let { if (i == 0) it.focusRequester(fr) else it },
                            singleLine = true, label = { Text("المبلغ (${unitName()})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        if (isDebt) {
                            OutlinedTextField(row.name, { row.name = it }, Modifier.weight(1.2f), singleLine = true, label = { Text("السلعة (اختياري)") })
                            if (rows.size > 1) IconButton({ rows.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = Red) }
                        }
                    }
                }
                if (isDebt) TextButton({ rows.add(ItemRow()) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة عنصر آخر") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 50, 100).forEach { v ->
                        AssistChip(onClick = {
                            val last = rows.last()
                            val cur = last.amount.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
                            last.amount = cur.add(BigDecimal(v)).toPlainString(); err = ""
                        }, label = { Text("+$v") })
                    }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("ملاحظة (اختياري)") })
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { Text(if (isDebt) "مجموع هذه العملية" else "مبلغ الدفعة", Modifier.weight(1f), fontSize = 13.sp); Text(fmt(total), fontWeight = FontWeight.Bold, color = if (isDebt) Red else Green, fontSize = 13.sp) }
                        Row {
                            Text(if (after >= 0) "الرصيد بعد العملية" else "رصيد لصالح الزبون", Modifier.weight(1f), fontSize = 13.sp)
                            Text(fmt(kotlin.math.abs(after)), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (after > 0) Red else if (after < 0) Green else Color.Gray)
                        }
                    }
                }
                if (err.isNotEmpty()) Text(err, color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button({
                val list = ArrayList<Pair<String, Long>>()
                var bad = false
                rows.forEach { r ->
                    if (r.amount.isBlank() && r.name.isBlank()) return@forEach
                    val c = parseAmt(r.amount)
                    if (c == null) bad = true else list.add(r.name.trim() to c)
                }
                if (bad) err = "مبلغ غير صالح، أدخل رقماً أكبر من صفر"
                else if (list.isEmpty()) err = "أدخل مبلغاً"
                else {
                    val plain = !isDebt || (list.size == 1 && list[0].first.isBlank())
                    onSave(list.sumOf { it.second }, note.trim(), if (plain) emptyList() else list)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = if (isDebt) Red else Green)) { Text("حفظ") }
        },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun CustomerScreen(ctx: Context, dao: LedgerDao, id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cust by remember(id) { dao.customer(id) }.collectAsState(null)
    val txns by remember(id) { dao.txns(id) }.collectAsState(emptyList())
    val items by remember(id) { dao.itemsOf(id) }.collectAsState(emptyList())
    val itemsBy = remember(items) { items.groupBy { it.txnId } }
    var entry by remember { mutableStateOf<Boolean?>(null) }
    var delTxn by remember { mutableStateOf<Txn?>(null) }
    var delCust by remember { mutableStateOf(false) }
    val bal = balanceOf(txns)
    val totalDebts = txns.filter { it.isDebt }.sumOf { it.amount }
    val totalPays = txns.filter { !it.isDebt }.sumOf { it.amount }
    var run = 0L
    val withRun = txns.map { run += if (it.isDebt) it.amount else -it.amount; it to run }.reversed()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text(cust?.name ?: "", fontWeight = FontWeight.Bold); if (!cust?.phone.isNullOrBlank()) Text(cust?.phone ?: "", fontSize = 12.sp) } },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton({
                        val c = cust ?: return@IconButton
                        val url = "https://wa.me/" + normPhone(c.phone) + "?text=" + Uri.encode(waText(shop(ctx), c.name, txns, items, curRyal))
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, "تعذر فتح واتساب") }
                    }) { Icon(Icons.Default.Share, null) }
                    IconButton({ delCust = true }) { Icon(Icons.Default.Delete, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ entry = true }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("أعطيت", fontSize = 17.sp) }
                Button({ entry = false }, Modifier.weight(1f).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("استلمت", fontSize = 17.sp) }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مجموع المشتريات والديون", Modifier.weight(1f), fontSize = 13.sp)
                            Text(fmt(totalDebts), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مجموع الدفعات", Modifier.weight(1f), fontSize = 13.sp)
                            Text(fmt(totalPays), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (bal > 0) "الرصيد المتبقي" else if (bal < 0) "رصيد لصالح الزبون" else "الحساب مسدّد", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtP(kotlin.math.abs(bal)), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = if (bal > 0) Red else if (bal < 0) Green else Color.Gray)
                                Text(fmtS(kotlin.math.abs(bal)), fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text("التفاصيل", Modifier.weight(2f), fontSize = 12.sp, color = Color.Gray)
                    Text("أعطيت", Modifier.weight(1f), fontSize = 12.sp, color = Red)
                    Text("استلمت", Modifier.weight(1f), fontSize = 12.sp, color = Green)
                }
            }
            if (txns.isEmpty()) item { Text("لا توجد عمليات بعد.", Modifier.padding(24.dp), color = Color.Gray) }
            items(withRun, key = { it.first.id }) { (t, r) ->
                Card(Modifier.fillMaxWidth().clickable { delTxn = t }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(2f)) {
                            Text(day(t.date), fontSize = 13.sp)
                            if (t.note.isNotBlank()) Text(t.note, fontSize = 12.sp, color = Color.Gray)
                            (itemsBy[t.id] ?: emptyList()).forEach { it ->
                                Text("• " + (if (it.name.isBlank()) "—" else it.name) + ": " + fmtP(it.amount), fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("الرصيد: " + fmtP(r), fontSize = 11.sp, color = Color.Gray)
                        }
                        Column(Modifier.weight(1f)) {
                            if (t.isDebt) { Text(fmtP(t.amount), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(fmtS(t.amount), color = Color.Gray, fontSize = 10.sp) }
                        }
                        Column(Modifier.weight(1f)) {
                            if (!t.isDebt) { Text(fmtP(t.amount), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(fmtS(t.amount), color = Color.Gray, fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
    }
    entry?.let { d ->
        EntryDialog(ctx, d, bal, { entry = null }) { a, n, its ->
            entry = null
            scope.launch { dao.addPurchase(Txn(customerId = id, amount = a, isDebt = d, note = n), its.map { it.first }, its.map { it.second }) }
        }
    }
    delTxn?.let { t ->
        AlertDialog(onDismissRequest = { delTxn = null }, title = { Text("حذف العملية؟") }, text = { Text("${day(t.date)} — ${fmt(t.amount)}") },
            confirmButton = { TextButton({ delTxn = null; scope.launch { dao.deleteTxnFull(t.id) } }) { Text("حذف", color = Red) } },
            dismissButton = { TextButton({ delTxn = null }) { Text("إلغاء") } })
    }
    if (delCust) AlertDialog(onDismissRequest = { delCust = false }, title = { Text("حذف الزبون وكل عملياته؟") },
        confirmButton = { TextButton({ delCust = false; scope.launch { dao.deleteCustomer(id); withContext(Dispatchers.Main) { onBack() } } }) { Text("حذف", color = Red) } },
        dismissButton = { TextButton({ delCust = false }) { Text("إلغاء") } })
}

@Composable
fun ReportsTab(dao: LedgerDao) {
    val rows by remember(dao) { dao.rows() }.collectAsState(emptyList())
    val all by remember(dao) { dao.allTxns() }.collectAsState(emptyList())
    val debts = all.filter { it.isDebt }.sumOf { it.amount }
    val pays = all.filter { !it.isDebt }.sumOf { it.amount }
    val top = rows.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)
    @Composable fun line(a: String, b: String, c: Color) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(a, Modifier.weight(1f)); Text(b, color = c, fontWeight = FontWeight.Bold) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(Modifier.padding(16.dp)) {
                    line("عدد الزبناء", rows.size.toString(), Color.Black)
                    line("إجمالي الديون المعطاة", fmt(debts), Red)
                    line("إجمالي الدفعات المستلمة", fmt(pays), Green)
                    line("المتبقي عند الزبناء", fmt(rows.filter { it.balance > 0 }.sumOf { it.balance }), Red)
                }
            }
        }
        item { Text("أكبر المدينين", fontWeight = FontWeight.Bold) }
        if (top.isEmpty()) item { Text("لا توجد ديون مستحقة.", color = Color.Gray) }
        items(top) { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp)) { Text(r.name, Modifier.weight(1f)); Text(fmt(r.balance), color = Red, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun SettingsTab(ctx: Context, dao: LedgerDao) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(shop(ctx)) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val json = toJson(dao.customersOnce(), dao.txnsOnce(), dao.itemsOnce())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                "تم حفظ النسخة الاحتياطية"
            } catch (e: Exception) { "فشل النسخ الاحتياطي" }
            withContext(Dispatchers.Main) { toast(ctx, msg) }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val s = ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                val b = fromJson(s)
                dao.replaceAll(b.customers, b.txns, b.items)
                "تمت الاستعادة: ${b.customers.size} زبون"
            } catch (e: Exception) { "ملف غير صالح" }
            withContext(Dispatchers.Main) { toast(ctx, msg) }
        }
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(name, { name = it; prefs(ctx).edit().putString("shop", it).apply() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("اسم المحل (يظهر في رسالة واتساب)") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("عملة الإدخال والعرض:", fontSize = 13.sp)
            FilterChip(curRyal, { setCurrency(ctx, true) }, { Text("$UNIT_RYAL (الافتراضي)") })
            FilterChip(!curRyal, { setCurrency(ctx, false) }, { Text(UNIT_DH) })
        }
        Text("1 درهم = 20 ريال  ·  1000 ريال = 50 درهماً", fontSize = 12.sp, color = Color.Gray)
        Button({ backup.launch("amine-hanout-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json") }, Modifier.fillMaxWidth().height(50.dp)) { Text("نسخ احتياطي") }
        OutlinedButton({ restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(50.dp)) { Text("استعادة من نسخة احتياطية") }
        Text("البيانات محفوظة محلياً على هاتفك بدون إنترنت. احفظ النسخة الاحتياطية في مكان آمن (Drive، واتساب...).", fontSize = 12.sp, color = Color.Gray)
    }
}
