package ma.aminehanout.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

fun prefs(ctx: Context) = ctx.getSharedPreferences("s", 0)

// true = amounts are typed and shown in ryal first (default), false = dirham first.
var curRyal by mutableStateOf(true)

fun loadSettings(ctx: Context) {
    curRyal = prefs(ctx).getString("cur", "ryal") != "dh"
}

fun setCurrency(ctx: Context, ryal: Boolean) {
    curRyal = ryal
    prefs(ctx).edit().putString("cur", if (ryal) "ryal" else "dh").apply()
}

fun fmt(cents: Long): String = fmtBoth(cents, curRyal)      // "1,000 ريال (50 درهم)"
fun fmtP(cents: Long): String = fmtPrimary(cents, curRyal)  // main unit only
fun fmtS(cents: Long): String = fmtSecondary(cents, curRyal) // the other unit
fun parseAmt(s: String): Long? = parseAmount(s, curRyal)
fun unitName(): String = if (curRyal) UNIT_RYAL else UNIT_DH
