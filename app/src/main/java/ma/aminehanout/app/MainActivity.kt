@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ma.aminehanout.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val Brand = Color(0xFF0B5D4B)
val BrandLight = Color(0xFF4DB89E)
val Red = Color(0xFFD64545)   // customer owes you / debt given (Khatabook logic)
val Green = Color(0xFF1B8A5A) // payment received / advance

class MainActivity : FragmentActivity() {
    var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings(this)
        // Stay unlocked across a rotation, but lock on a cold start or after a long absence.
        val recent = System.currentTimeMillis() - prefs(this).getLong("last_stop", 0L) < LOCK_AFTER_MS
        locked = pinEnabled(this) && !(savedInstanceState?.getBoolean("unlocked") == true && recent)
        setContent {
            val dark = when (themePref) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            val dens = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides (if (lang == "fr") LayoutDirection.Ltr else LayoutDirection.Rtl),
                LocalDensity provides Density(dens.density, dens.fontScale * (fontPref.toFloatOrNull() ?: 1f))
            ) {
                val scheme = if (dark) darkColorScheme(primary = BrandLight, onPrimary = Color.Black, background = Color(0xFF0F1412), surface = Color(0xFF1A211E),
                    outlineVariant = Color(0xFF2E3733), secondaryContainer = Color(0xFF1F4F43), onSecondaryContainer = Color.White)
                else lightColorScheme(primary = Brand, background = Color(0xFFF1F5F3), surface = Color.White,
                    outlineVariant = Color(0xFFE1E8E5), secondaryContainer = Color(0xFFD3EEE4), onSecondaryContainer = Color(0xFF064E3B))
                MaterialTheme(colorScheme = scheme, shapes = AppShapes) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        if (locked) LockScreen(this@MainActivity) { locked = false } else App(this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("unlocked", !locked)
    }

    override fun onStart() {
        super.onStart()
        val gone = System.currentTimeMillis() - prefs(this).getLong("last_stop", 0L)
        if (pinEnabled(this) && gone > LOCK_AFTER_MS) locked = true
        val app = applicationContext
        appScope.launch { runAutoBackup(app, AppDb.get(app).dao()) }
    }

    override fun onStop() {
        super.onStop()
        prefs(this).edit().putLong("last_stop", System.currentTimeMillis()).apply()
        val app = applicationContext
        appScope.launch { runAutoBackup(app, AppDb.get(app).dao()) }
    }

    companion object { const val LOCK_AFTER_MS = 20_000L }
}

val HeroBrush = Brush.linearGradient(listOf(Color(0xFF064E3B), Color(0xFF0B8F6F)))
val AppShapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp))

@Composable
fun cardColors() = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = cardColors(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content)
}

@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(HeroBrush).padding(20.dp)) { Column(content = content) }
}

@Composable
fun StatChip(modifier: Modifier, label: String, value: String) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .14f)).padding(12.dp)) {
        Column {
            Text(label, color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GradientBar(title: String, sub: String? = null) {
    Box(Modifier.fillMaxWidth().background(HeroBrush).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Column {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (!sub.isNullOrBlank()) Text(sub, color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
        }
    }
}

@Composable
fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }.padding(8.dp)) {
        Box(Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

fun avatarColor(name: String): Color {
    val cols = listOf(Color(0xFF0B5D4B), Color(0xFF3F51B5), Color(0xFF8E24AA), Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFF6D4C41))
    return cols[(name.hashCode() and 0x7fffffff) % cols.size]
}

fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun addDays(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

fun startOfMonth(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(ms); set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

fun addMonths(ms: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.MONTH, n) }.timeInMillis

fun endOfDayIn(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

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
            GradientBar(
                tr(when (tab) { 1 -> "daily"; 2 -> "reports"; 3 -> "settings"; else -> "app_name" }),
                if (tab == 0) shop(ctx) else null
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Default.Person, null) }, label = { Text(tr("customers")) })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Default.DateRange, null) }, label = { Text(tr("daily")) })
                NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text(tr("reports")) })
                NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Default.Settings, null) }, label = { Text(tr("settings")) })
            }
        },
        floatingActionButton = {
            if (tab == 0) ExtendedFloatingActionButton(onClick = { showAdd = true }, shape = RoundedCornerShape(20.dp), containerColor = Brand, contentColor = Color.White) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(tr("new_customer"))
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> CustomersTab(dao, { showAdd = true }) { openId = it }
                1 -> DailyTab(dao)
                2 -> ReportsTab(dao)
                else -> SettingsTab(ctx, dao)
            }
        }
    }
    if (showAdd) CustomerDialog(ctx, null, { showAdd = false }) { n, p ->
        showAdd = false
        scope.launch { openId = dao.addCustomer(Customer(name = n, phone = p)) }
    }
}

@Composable
fun CustomersTab(dao: LedgerDao, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val rows by remember(dao) { dao.rows() }.collectAsState(emptyList())
    val all by remember(dao) { dao.allTxns() }.collectAsState(emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("recent") }
    val now = System.currentTimeMillis()
    val dues = remember(all) { all.groupBy { it.customerId }.mapValues { nextDue(it.value) } }
    fun isOver(r: CustomerRow): Boolean { val d = dues[r.id] ?: 0L; return r.balance > 0 && d > 0L && d < now }
    val owed = rows.filter { it.balance > 0 }.sumOf { it.balance }
    val adv = rows.filter { it.balance < 0 }.sumOf { -it.balance }
    val overdueCount = rows.count { isOver(it) }
    val filtered = rows.filter { r ->
        (r.name.contains(q, true) || r.phone.contains(q)) && when (filter) {
            "owe" -> r.balance > 0
            "credit" -> r.balance < 0
            "settled" -> r.balance == 0L
            "overdue" -> isOver(r)
            else -> true
        }
    }
    val list = when (sort) {
        "amount" -> filtered.sortedByDescending { it.balance }
        "name" -> filtered.sortedBy { it.name.lowercase() }
        else -> filtered.sortedByDescending { it.lastDate }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            HeroCard(Modifier.fillMaxWidth()) {
                Text(tr("you_get"), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                Text(fmtP(owed), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text(fmtS(owed), color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip(Modifier.weight(1f), tr("you_owe"), fmtP(adv))
                    StatChip(Modifier.weight(1f), tr("f_overdue"), num(overdueCount))
                }
            }
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(28.dp), placeholder = { Text(tr("search")) }, leadingIcon = { Icon(Icons.Default.Search, null) })
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all", "owe", "credit", "settled", "overdue")) { f -> FilterChip(filter == f, { filter = f }, { Text(tr("f_$f")) }) }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("recent", "amount", "name")) { s -> FilterChip(sort == s, { sort = s }, { Text(tr("s_$s")) }) }
            }
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("empty_title"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(tr("empty_body"), color = Color.Gray)
                Button(onAdd) { Text(tr("new_customer")) }
            }
        }
        items(list, key = { it.id }) { r ->
            val over = isOver(r)
            val due = dues[r.id] ?: 0L
            AppCard(Modifier.fillMaxWidth().clickable { onOpen(r.id) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(avatarColor(r.name)), contentAlignment = Alignment.Center) {
                        Text(r.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(dayS(r.lastDate), fontSize = 12.sp, color = Color.Gray)
                        if (r.balance > 0 && due > 0) Text(if (over) tr("overdue") + " · " + tr("due_on", dayS(due)) else tr("due_on", dayS(due)), fontSize = 11.sp, color = if (over) Red else Color.Gray, fontWeight = if (over) FontWeight.Bold else FontWeight.Normal)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val bc = if (r.balance > 0) Red else if (r.balance < 0) Green else Color.Gray
                        Text(fmtP(kotlin.math.abs(r.balance)), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = bc)
                        Text(fmtS(kotlin.math.abs(r.balance)), fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Pill(if (r.balance > 0) tr("owes") else if (r.balance < 0) tr("credit") else tr("settled"), bc)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerDialog(ctx: Context, initial: Customer?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by remember { mutableStateOf(initial?.name ?: "") }
    var p by remember { mutableStateOf(initial?.phone ?: "") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) fr.requestFocus() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (uri != null) {
            try {
                ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        p = c.getString(0) ?: p
                        if (n.isBlank()) n = c.getString(1) ?: n
                    }
                }
            } catch (e: Exception) { toast(ctx, tr("err_generic")) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (initial == null) "new_customer" else "edit_customer")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, Modifier.focusRequester(fr), singleLine = true, label = { Text(tr("name")) })
                OutlinedTextField(p, { p = it }, singleLine = true, label = { Text(tr("phone")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                TextButton({
                    try { pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) } catch (e: Exception) { toast(ctx, tr("err_generic")) }
                }) { Text(tr("from_contacts")) }
            }
        },
        confirmButton = { Button({ if (n.isNotBlank()) onSave(n.trim(), p.trim()) }) { Text(tr("save")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("cancel")) } }
    )
}

class ItemRow(initName: String = "", initAmount: String = "") {
    var name by mutableStateOf(initName)
    var amount by mutableStateOf(initAmount)
}

private fun centsToInput(c: Long): String = (if (curRyal) ryalOf(c) else dhOf(c)).stripTrailingZeros().toPlainString()

// Entry dialog. For a debt/purchase it supports several items in one operation (each with an optional name),
// a due date, a live total and the resulting balance. For a payment it is a single amount.
// `initial` is non-null when editing an existing operation. prevBalance excludes the operation being edited.
@Composable
fun EntryDialog(
    ctx: Context, isDebt: Boolean, prevBalance: Long, initial: Txn?, initialItems: List<TxnItem>,
    onDismiss: () -> Unit, onSave: (Long, String, List<Pair<String, Long>>, Long) -> Unit
) {
    val rows = remember {
        mutableStateListOf<ItemRow>().also { l ->
            if (initial == null) l.add(ItemRow())
            else if (initialItems.isNotEmpty()) initialItems.forEach { l.add(ItemRow(it.name, centsToInput(it.amount))) }
            else l.add(ItemRow("", centsToInput(initial.amount)))
        }
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var due by remember { mutableLongStateOf(initial?.dueDate ?: 0L) }
    var err by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initial == null) fr.requestFocus() }
    val total = rows.sumOf { parseAmt(it.amount) ?: 0L }
    val after = if (isDebt) prevBalance + total else prevBalance - total
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (isDebt) "dlg_gave" else "dlg_got"), color = if (isDebt) Red else Green) },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("cur_label"), fontSize = 13.sp, color = Color.Gray)
                    FilterChip(curRyal, { setCurrency(ctx, true) }, { Text(unitRyal()) })
                    FilterChip(!curRyal, { setCurrency(ctx, false) }, { Text(unitDh()) })
                }
                rows.forEachIndexed { i, row ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(row.amount, { row.amount = it; err = "" },
                            Modifier.weight(if (isDebt) 1f else 2f).let { m -> if (i == 0) m.focusRequester(fr) else m },
                            singleLine = true, label = { Text(tr("amount_unit", unitName())) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        if (isDebt) {
                            OutlinedTextField(row.name, { row.name = it }, Modifier.weight(1.2f), singleLine = true, label = { Text(tr("item_name")) })
                            if (rows.size > 1) IconButton({ rows.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = Red) }
                        }
                    }
                }
                if (isDebt) TextButton({ rows.add(ItemRow()) }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(tr("add_item")) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 50, 100).forEach { v ->
                        AssistChip(onClick = {
                            val last = rows.last()
                            val cur = last.amount.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
                            last.amount = cur.add(BigDecimal(v)).stripTrailingZeros().toPlainString(); err = ""
                        }, label = { Text("+" + num(v)) })
                    }
                }
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("note_opt")) })
                if (isDebt) {
                    Text(tr("due_label") + (if (due > 0) ": " + dayS(due) else ""), fontSize = 13.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(due == 0L, { due = 0L }, { Text(tr("due_none")) })
                        listOf(7, 15, 30).forEach { d -> AssistChip(onClick = { due = endOfDayIn(d) }, label = { Text(digits(tr("due_days", d))) }) }
                    }
                    TextButton({
                        val cal = Calendar.getInstance().apply { if (due > 0) timeInMillis = due }
                        android.app.DatePickerDialog(ctx, { _, y, m, d ->
                            due = Calendar.getInstance().apply { set(y, m, d, 23, 59, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Icon(Icons.Default.DateRange, null); Spacer(Modifier.width(6.dp)); Text(tr("due_pick")) }
                }
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row { Text(tr(if (isDebt) "this_total" else "pay_amount"), Modifier.weight(1f), fontSize = 13.sp); Text(fmt(total), fontWeight = FontWeight.Bold, color = if (isDebt) Red else Green, fontSize = 13.sp) }
                        Row {
                            Text(tr(if (after >= 0) "bal_after" else "bal_credit"), Modifier.weight(1f), fontSize = 13.sp)
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
                if (bad) err = tr("err_amount")
                else if (list.isEmpty()) err = tr("err_enter")
                else {
                    val plain = !isDebt || (list.size == 1 && list[0].first.isBlank())
                    onSave(list.sumOf { it.second }, note.trim(), if (plain) emptyList() else list, if (isDebt) due else 0L)
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = if (isDebt) Red else Green)) { Text(tr("save")) }
        },
        dismissButton = { TextButton(onDismiss) { Text(tr("cancel")) } }
    )
}

fun doStatementWa(ctx: Context, c: Customer, txns: List<Txn>, items: List<TxnItem>) =
    openWa(ctx, c.phone, statementText(shop(ctx), c.name, txns, items))

fun doRemind(ctx: Context, c: Customer, txns: List<Txn>, sms: Boolean) {
    val text = reminderText(shop(ctx), c.name, balanceOf(txns), nextDue(txns))
    if (sms) openSms(ctx, c.phone, text) else openWa(ctx, c.phone, text)
}

fun doPdf(ctx: Context, c: Customer, txns: List<Txn>, items: List<TxnItem>) {
    val uri = makeStatementPdf(ctx, shop(ctx), c, txns, items)
    if (uri == null) toast(ctx, tr("pdf_err")) else sharePdf(ctx, uri)
}

@Composable
fun CustomerScreen(ctx: Context, dao: LedgerDao, id: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cust by remember(id) { dao.customer(id) }.collectAsState(null)
    val txns by remember(id) { dao.txns(id) }.collectAsState(emptyList())
    val items by remember(id) { dao.itemsOf(id) }.collectAsState(emptyList())
    val itemsBy = remember(items) { items.groupBy { it.txnId } }
    val snack = remember { SnackbarHostState() }
    var entry by remember { mutableStateOf<Boolean?>(null) }
    var actTxn by remember { mutableStateOf<Txn?>(null) }
    var editTxn by remember { mutableStateOf<Txn?>(null) }
    var delCust by remember { mutableStateOf(false) }
    var editCust by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val bal = balanceOf(txns)
    val totalDebts = txns.filter { it.isDebt }.sumOf { it.amount }
    val totalPays = txns.filter { !it.isDebt }.sumOf { it.amount }
    val due = remember(txns) { nextDue(txns) }
    val now = System.currentTimeMillis()
    var run = 0L
    val withRun = txns.map { run += if (it.isDebt) it.amount else -it.amount; it to run }.reversed()

    Scaffold(
        topBar = {
            Row(Modifier.fillMaxWidth().background(HeroBrush).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(cust?.name ?: "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    if (!cust?.phone.isNullOrBlank()) Text(cust?.phone ?: "", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                }
                IconButton({ editCust = true }) { Icon(Icons.Default.Edit, tr("edit"), tint = Color.White) }
                IconButton({ delCust = true }) { Icon(Icons.Default.Delete, tr("delete"), tint = Color.White) }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ entry = true }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text(tr("gave"), fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                Button({ entry = false }, Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text(tr("got"), fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                HeroCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(if (bal > 0) "bal_left" else if (bal < 0) "bal_credit" else "bal_zero"), Modifier.weight(1f), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        if (bal != 0L) Pill(tr(if (bal > 0) "owes" else "credit"), if (bal > 0) Color(0xFFFFB4B4) else Color(0xFFB4F0D2))
                    }
                    Text(fmtP(kotlin.math.abs(bal)), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                    Text(fmtS(kotlin.math.abs(bal)), color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatChip(Modifier.weight(1f), tr("total_debts"), fmtP(totalDebts))
                        StatChip(Modifier.weight(1f), tr("total_pays"), fmtP(totalPays))
                    }
                    if (bal > 0 && due > 0) {
                        Spacer(Modifier.height(10.dp))
                        Pill((if (due < now) tr("overdue") + " · " else "") + tr("due_on", dayS(due)), if (due < now) Color(0xFFFFB4B4) else Color.White)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (!cust?.phone.isNullOrBlank()) QuickAction(Icons.Default.Call, tr("call")) {
                        try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + cust?.phone))) } catch (e: Exception) { toast(ctx, tr("err_generic")) }
                    }
                    QuickAction(Icons.Default.Notifications, tr("q_remind")) { cust?.let { doRemind(ctx, it, txns, false) } }
                    QuickAction(Icons.Default.Share, tr("q_pdf")) { cust?.let { doPdf(ctx, it, txns, items) } }
                    Box {
                        QuickAction(Icons.Default.MoreVert, tr("q_more")) { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(tr("m_statement")) }, onClick = { menu = false; cust?.let { doStatementWa(ctx, it, txns, items) } })
                            DropdownMenuItem(text = { Text(tr("m_remind_sms")) }, onClick = { menu = false; cust?.let { doRemind(ctx, it, txns, true) } })
                        }
                    }
                }
            }
            if (txns.isEmpty()) item { Text(tr("no_txns"), Modifier.padding(24.dp), color = Color.Gray) }
            items(withRun, key = { it.first.id }) { (t, r) ->
                val c = if (t.isDebt) Red else Green
                AppCard(Modifier.fillMaxWidth().clickable { actTxn = t }) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(c))
                        Row(Modifier.weight(1f).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(dayS(t.date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                if (t.note.isNotBlank()) Text(t.note, fontSize = 12.sp, color = Color.Gray)
                                (itemsBy[t.id] ?: emptyList()).forEach { i ->
                                    Text("• " + (if (i.name.isBlank()) "—" else i.name) + ": " + fmtP(i.amount), fontSize = 12.sp, color = Color.Gray)
                                }
                                if (t.isDebt && t.dueDate > 0) Text(tr("due_on", dayS(t.dueDate)), fontSize = 11.sp, color = if (t.dueDate < now && bal > 0) Red else Color.Gray)
                                Text(tr("balance") + ": " + fmtP(r), fontSize = 11.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(fmtP(t.amount), color = c, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(fmtS(t.amount), color = Color.Gray, fontSize = 11.sp)
                                Spacer(Modifier.height(4.dp))
                                Pill(tr(if (t.isDebt) "gave" else "got"), c)
                            }
                        }
                    }
                }
            }
        }
    }
    entry?.let { d ->
        EntryDialog(ctx, d, bal, null, emptyList(), { entry = null }) { a, n, its, dd ->
            entry = null
            scope.launch { dao.addPurchase(Txn(customerId = id, amount = a, isDebt = d, note = n, dueDate = dd), its.map { it.first }, its.map { it.second }) }
        }
    }
    editTxn?.let { t ->
        val prev = bal - (if (t.isDebt) t.amount else -t.amount)
        EntryDialog(ctx, t.isDebt, prev, t, itemsBy[t.id] ?: emptyList(), { editTxn = null }) { a, n, its, dd ->
            editTxn = null
            scope.launch { dao.editTxn(t.copy(amount = a, note = n, dueDate = dd), its.map { it.first }, its.map { it.second }) }
        }
    }
    actTxn?.let { t ->
        AlertDialog(onDismissRequest = { actTxn = null }, title = { Text(tr("act_title")) }, text = { Text(dayS(t.date) + " — " + fmt(t.amount)) },
            confirmButton = { TextButton({ actTxn = null; editTxn = t }) { Text(tr("edit")) } },
            dismissButton = {
                TextButton({
                    actTxn = null
                    val its = itemsBy[t.id] ?: emptyList()
                    scope.launch {
                        dao.deleteTxnFull(t.id)
                        val res = snack.showSnackbar(tr("deleted"), tr("undo"))
                        if (res == SnackbarResult.ActionPerformed) dao.restoreTxn(t, its)
                    }
                }) { Text(tr("delete"), color = Red) }
            })
    }
    if (editCust) cust?.let { c ->
        CustomerDialog(ctx, c, { editCust = false }) { n, p -> editCust = false; scope.launch { dao.updateCustomer(c.copy(name = n, phone = p)) } }
    }
    if (delCust) AlertDialog(onDismissRequest = { delCust = false }, title = { Text(tr("del_cust")) },
        confirmButton = { TextButton({ delCust = false; scope.launch { dao.deleteCustomer(id); withContext(Dispatchers.Main) { onBack() } } }) { Text(tr("delete"), color = Red) } },
        dismissButton = { TextButton({ delCust = false }) { Text(tr("cancel")) } })
}

@Composable
fun CashDialog(isSale: Boolean, onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val c = parseAmt(amt)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(if (isSale) "d_sale_t" else "d_exp_t"), color = if (isSale) Green else Red) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amt, { amt = it; err = false }, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true, label = { Text(tr("amount_unit", unitName())) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("note_opt")) })
                if (c != null) Text(fmt(c), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (err) Text(tr("err_amount"), color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = { Button({ val v = parseAmt(amt); if (v == null) err = true else onSave(v, note.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = if (isSale) Green else Red)) { Text(tr("save")) } },
        dismissButton = { TextButton(onDismiss) { Text(tr("cancel")) } }
    )
}

@Composable
fun DailyTab(dao: LedgerDao) {
    val scope = rememberCoroutineScope()
    val all by remember(dao) { dao.cash() }.collectAsState(emptyList())
    var dlg by remember { mutableStateOf<Boolean?>(null) }
    var del by remember { mutableStateOf<CashEntry?>(null) }
    val t0 = startOfDay(System.currentTimeMillis())
    val today = all.filter { it.date >= t0 }
    val sales = today.filter { it.isSale }.sumOf { it.amount }
    val exp = today.filter { !it.isSale }.sumOf { it.amount }
    val recent = all.filter { it.date >= addDays(t0, -6) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(tr("d_today") + " · " + dayS(t0), fontWeight = FontWeight.Bold)
                    Row { Text(tr("r_cash"), Modifier.weight(1f), fontSize = 13.sp); Text(fmt(sales), color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Row { Text(tr("r_exp"), Modifier.weight(1f), fontSize = 13.sp); Text(fmt(exp), color = Red, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    val net = sales - exp
                    Row { Text(tr("r_net"), Modifier.weight(1f), fontWeight = FontWeight.Bold); Text((if (net < 0) "-" else "") + fmt(kotlin.math.abs(net)), fontWeight = FontWeight.Bold, color = if (net < 0) Red else Green) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button({ dlg = true }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text(tr("d_sale"), color = Color.White) }
                Button({ dlg = false }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text(tr("d_exp"), color = Color.White) }
            }
        }
        item { Text(tr("d_recent"), fontWeight = FontWeight.Bold) }
        if (recent.isEmpty()) item { Text(tr("d_none"), color = Color.Gray) }
        items(recent, key = { it.id }) { e ->
            AppCard(Modifier.fillMaxWidth().clickable { del = e }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(if (e.isSale) "d_sale_t" else "d_exp_t"), fontSize = 13.sp)
                        Text(dayS(e.date) + (if (e.note.isNotBlank()) " · " + e.note else ""), fontSize = 12.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtP(e.amount), fontWeight = FontWeight.Bold, color = if (e.isSale) Green else Red)
                        Text(fmtS(e.amount), fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
    dlg?.let { sale -> CashDialog(sale, { dlg = null }) { a, n -> dlg = null; scope.launch { dao.addCash(CashEntry(isSale = sale, amount = a, note = n)) } } }
    del?.let { e ->
        AlertDialog(onDismissRequest = { del = null }, title = { Text(tr("d_del")) }, text = { Text(dayS(e.date) + " — " + fmt(e.amount)) },
            confirmButton = { TextButton({ del = null; scope.launch { dao.deleteCash(e.id) } }) { Text(tr("delete"), color = Red) } },
            dismissButton = { TextButton({ del = null }) { Text(tr("cancel")) } })
    }
}

private data class Period(val given: Long, val got: Long, val sales: Long, val exp: Long)

private fun period(txns: List<Txn>, cash: List<CashEntry>, from: Long, to: Long): Period {
    val t = txns.filter { it.date >= from && it.date < to }
    val c = cash.filter { it.date >= from && it.date < to }
    return Period(t.filter { it.isDebt }.sumOf { it.amount }, t.filter { !it.isDebt }.sumOf { it.amount }, c.filter { it.isSale }.sumOf { it.amount }, c.filter { !it.isSale }.sumOf { it.amount })
}

private data class MonthBar(val month: Int, val p: Period)

@Composable
fun ReportsTab(dao: LedgerDao) {
    val rows by remember(dao) { dao.rows() }.collectAsState(emptyList())
    val all by remember(dao) { dao.allTxns() }.collectAsState(emptyList())
    val cash by remember(dao) { dao.cash() }.collectAsState(emptyList())
    val debts = all.filter { it.isDebt }.sumOf { it.amount }
    val pays = all.filter { !it.isDebt }.sumOf { it.amount }
    val top = rows.filter { it.balance > 0 }.sortedByDescending { it.balance }.take(5)
    val now = System.currentTimeMillis()
    val d0 = startOfDay(now)
    val m0 = startOfMonth(now)
    val today = period(all, cash, d0, addDays(d0, 1))
    val month = period(all, cash, m0, addMonths(m0, 1))
    val months = (5 downTo 0).map { i ->
        val s = addMonths(m0, -i)
        MonthBar(Calendar.getInstance().apply { timeInMillis = s }.get(Calendar.MONTH) + 1, period(all, cash, s, addMonths(s, 1)))
    }
    val maxV = months.maxOf { maxOf(it.p.given, it.p.got) }.coerceAtLeast(1L)
    val onSurface = MaterialTheme.colorScheme.onSurface
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    ReportLine(tr("r_count"), num(rows.size), onSurface)
                    ReportLine(tr("r_given"), fmt(debts), Red)
                    ReportLine(tr("r_received"), fmt(pays), Green)
                    ReportLine(tr("r_remaining"), fmt(rows.filter { it.balance > 0 }.sumOf { it.balance }), Red)
                }
            }
        }
        item { PeriodBlock(tr("r_today"), today) }
        item { PeriodBlock(tr("r_month"), month) }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("r_chart"), fontSize = 12.sp, color = Color.Gray)
                    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        months.forEach { m ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Box(Modifier.width(10.dp).height(((m.p.given.toFloat() / maxV) * 90f).dp).background(Red))
                                    Spacer(Modifier.width(2.dp))
                                    Box(Modifier.width(10.dp).height(((m.p.got.toFloat() / maxV) * 90f).dp).background(Green))
                                }
                                Text(num(m.month), fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
        item { Text(tr("r_top"), fontWeight = FontWeight.Bold) }
        if (top.isEmpty()) item { Text(tr("r_none"), color = Color.Gray) }
        items(top) { r ->
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.name, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) { Text(fmtP(r.balance), color = Red, fontWeight = FontWeight.Bold); Text(fmtS(r.balance), color = Color.Gray, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun ReportLine(a: String, b: String, c: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text(a, Modifier.weight(1f), fontSize = 13.sp); Text(b, color = c, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun PeriodBlock(title: String, p: Period) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            ReportLine(tr("r_given"), fmt(p.given), Red)
            ReportLine(tr("r_received"), fmt(p.got), Green)
            ReportLine(tr("r_cash"), fmt(p.sales), Green)
            ReportLine(tr("r_exp"), fmt(p.exp), Red)
            val net = p.got + p.sales - p.exp
            ReportLine(tr("r_net"), (if (net < 0) "-" else "") + fmt(kotlin.math.abs(net)), if (net < 0) Red else Green)
        }
    }
}

@Composable
fun PinDialog(mode: String, ctx: Context, onDone: (Boolean) -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    var first by remember { mutableStateOf("") }
    var v by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onDone(false) },
        title = { Text(tr(if (mode == "off") "pin_enter_off" else if (step == 1) "pin_set" else "pin_confirm")) },
        text = {
            Column {
                OutlinedTextField(v, { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) { v = it; err = "" } }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                if (err.isNotEmpty()) Text(err, color = Red, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button({
                if (v.length == 4) {
                    if (mode == "off") {
                        if (checkPin(ctx, v)) { clearPin(ctx); onDone(true) } else { err = tr("pin_wrong"); v = "" }
                    } else if (step == 1) { first = v; v = ""; step = 2 }
                    else if (v == first) { setPin(ctx, v); onDone(true) }
                    else { err = tr("pin_mismatch"); step = 1; v = ""; first = "" }
                }
            }) { Text(tr("save")) }
        },
        dismissButton = { TextButton({ onDone(false) }) { Text(tr("cancel")) } }
    )
}

@Composable
private fun SectionTitle(s: String) { Text(s, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }

@Composable
fun SettingsTab(ctx: Context, dao: LedgerDao) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(shop(ctx)) }
    var pinOn by remember { mutableStateOf(pinEnabled(ctx)) }
    var bioOn by remember { mutableStateOf(bioEnabled(ctx)) }
    var pinDlg by remember { mutableStateOf<String?>(null) }
    var autoUri by remember { mutableStateOf(prefs(ctx).getString("auto_uri", null)) }
    var autoLast by remember { mutableLongStateOf(prefs(ctx).getLong("auto_last", 0L)) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val json = toJson(dao.customersOnce(), dao.txnsOnce(), dao.itemsOnce(), dao.cashOnce())
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                tr("backup_saved")
            } catch (e: Exception) { tr("backup_failed") }
            withContext(Dispatchers.Main) { toast(ctx, msg) }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val msg = try {
                val s = ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                val b = fromJson(s)
                dao.replaceAll(b.customers, b.txns, b.items, b.cash)
                tr("restore_ok", b.customers.size)
            } catch (e: Exception) { tr("bad_file") }
            withContext(Dispatchers.Main) { toast(ctx, msg) }
        }
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Exception) { }
            prefs(ctx).edit().putString("auto_uri", uri.toString()).putLong("auto_last", 0L).apply()
            autoUri = uri.toString(); autoLast = 0L
            scope.launch {
                val ok = runAutoBackup(ctx, dao, force = true)
                autoLast = prefs(ctx).getLong("auto_last", 0L)
                toast(ctx, tr(if (ok) "auto_done" else "auto_fail"))
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it; prefs(ctx).edit().putString("shop", it).apply() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(tr("shop_name")) })

        SettingsGroup(tr("sec_look")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("language"), Modifier.width(72.dp), fontSize = 13.sp)
            FilterChip(lang == "ar", { setLang(ctx, "ar") }, { Text("العربية") })
            FilterChip(lang == "fr", { setLang(ctx, "fr") }, { Text("Français") })
            FilterChip(lang == "dr", { setLang(ctx, "dr") }, { Text("الدارجة") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("digits"), Modifier.width(72.dp), fontSize = 13.sp)
            FilterChip(!eastDigits, { setEast(ctx, false) }, { Text("123") })
            FilterChip(eastDigits, { setEast(ctx, true) }, { Text("١٢٣") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("theme"), Modifier.width(72.dp), fontSize = 13.sp)
            FilterChip(themePref == "auto", { setTheme(ctx, "auto") }, { Text(tr("theme_auto")) })
            FilterChip(themePref == "light", { setTheme(ctx, "light") }, { Text(tr("theme_light")) })
            FilterChip(themePref == "dark", { setTheme(ctx, "dark") }, { Text(tr("theme_dark")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("font"), Modifier.width(72.dp), fontSize = 13.sp)
            FilterChip(fontPref == "1.0", { setFont(ctx, "1.0") }, { Text(tr("font_n")) })
            FilterChip(fontPref == "1.15", { setFont(ctx, "1.15") }, { Text(tr("font_l")) })
            FilterChip(fontPref == "1.3", { setFont(ctx, "1.3") }, { Text(tr("font_xl")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("cur_label"), Modifier.width(72.dp), fontSize = 13.sp)
            FilterChip(curRyal, { setCurrency(ctx, true) }, { Text(tr("cur_default", unitRyal())) })
            FilterChip(!curRyal, { setCurrency(ctx, false) }, { Text(unitDh()) })
        }
        Text(tr("rate_note"), fontSize = 12.sp, color = Color.Gray)

        }
        SettingsGroup(tr("sec_security")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("pin_on"), Modifier.weight(1f))
            Switch(pinOn, { want -> pinDlg = if (want) "set" else "off" })
        }
        if (pinOn && canBiometric(ctx)) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("bio_on"), Modifier.weight(1f))
            Switch(bioOn, { v -> bioOn = v; setBio(ctx, v) })
        }

        }
        SettingsGroup(tr("sec_backup")) {
        Button({ backup.launch("amine-app-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json") }, Modifier.fillMaxWidth().height(50.dp)) { Text(tr("backup")) }
        OutlinedButton({ restore.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth().height(50.dp)) { Text(tr("restore")) }
        if (autoUri == null) {
            Text(tr("auto_hint"), fontSize = 12.sp, color = Color.Gray)
            OutlinedButton({ pickFolder.launch(null) }, Modifier.fillMaxWidth().height(50.dp)) { Text(tr("auto_choose")) }
        } else {
            Text(tr("auto_on", if (autoLast == 0L) tr("auto_never") else dayS(autoLast) + " " + digits(SimpleDateFormat("HH:mm", Locale.US).format(Date(autoLast)))), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    scope.launch {
                        val ok = runAutoBackup(ctx, dao, force = true)
                        autoLast = prefs(ctx).getLong("auto_last", 0L)
                        toast(ctx, tr(if (ok) "auto_done" else "auto_fail"))
                    }
                }, Modifier.weight(1f)) { Text(tr("auto_now")) }
                OutlinedButton({
                    prefs(ctx).edit().remove("auto_uri").apply(); autoUri = null
                }, Modifier.weight(1f)) { Text(tr("auto_off")) }
            }
        }
        Text(tr("local_note"), fontSize = 12.sp, color = Color.Gray)
        }
    }
    pinDlg?.let { m ->
        PinDialog(m, ctx) { ok ->
            pinDlg = null
            if (ok) { pinOn = pinEnabled(ctx); bioOn = bioEnabled(ctx) }
        }
    }
}
