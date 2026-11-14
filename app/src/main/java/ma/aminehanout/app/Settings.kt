package ma.aminehanout.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

fun prefs(ctx: Context) = ctx.getSharedPreferences("s", 0)

// true = amounts are typed and shown in ryal first (default), false = dirham first.
var curRyal by mutableStateOf(true)

fun loadSettings(ctx: Context) {
    val p = prefs(ctx)
    curRyal = p.getString("cur", "ryal") != "dh"
    lang = p.getString("lang", "ar") ?: "ar"
    eastDigits = p.getBoolean("east", false)
    themePref = p.getString("theme", "auto") ?: "auto"
    fontPref = p.getString("font", "1.0") ?: "1.0"
}

fun setCurrency(ctx: Context, ryal: Boolean) {
    curRyal = ryal
    prefs(ctx).edit().putString("cur", if (ryal) "ryal" else "dh").apply()
}
fun setLang(ctx: Context, v: String) { lang = v; prefs(ctx).edit().putString("lang", v).apply() }
fun setEast(ctx: Context, v: Boolean) { eastDigits = v; prefs(ctx).edit().putBoolean("east", v).apply() }
fun setTheme(ctx: Context, v: String) { themePref = v; prefs(ctx).edit().putString("theme", v).apply() }
fun setFont(ctx: Context, v: String) { fontPref = v; prefs(ctx).edit().putString("font", v).apply() }

fun unitRyal(): String = tr("u_ryal")
fun unitDh(): String = tr("u_dh")
fun unitName(): String = if (curRyal) unitRyal() else unitDh()

fun fmt(cents: Long): String = digits(fmtBoth(cents, curRyal, unitRyal(), unitDh()))       // "1,000 ريال (50 درهم)"
fun fmtP(cents: Long): String = digits(fmtPrimary(cents, curRyal, unitRyal(), unitDh()))   // main unit only
fun fmtS(cents: Long): String = digits(fmtSecondary(cents, curRyal, unitRyal(), unitDh())) // the other unit
fun parseAmt(s: String): Long? = parseAmount(s, curRyal)
fun dayS(ms: Long): String = digits(day(ms))
fun num(n: Number): String = digits(n.toString())

fun shop(ctx: Context): String = prefs(ctx).getString("shop", "Amine App") ?: "Amine App"
