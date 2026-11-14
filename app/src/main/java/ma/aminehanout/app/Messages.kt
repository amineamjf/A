package ma.aminehanout.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

// Messages sent to customers use Latin digits so every phone renders them the same way.
private fun money(c: Long): String = fmtBoth(c, curRyal, unitRyal(), unitDh())

fun statementText(shop: String, name: String, txns: List<Txn>, items: List<TxnItem>): String {
    val bal = balanceOf(txns)
    val byTxn = items.groupBy { it.txnId }
    val sb = StringBuilder(tr("wa_hello", name)).append("\n").append(tr("wa_statement_of", shop)).append("\n\n")
    txns.takeLast(10).forEach {
        sb.append("${day(it.date)}  ${if (it.isDebt) tr("wa_debt") else tr("wa_pay")}  ${money(it.amount)}")
        if (it.note.isNotBlank()) sb.append("  (${it.note})")
        sb.append("\n")
        byTxn[it.id]?.forEach { i -> sb.append("   • ${if (i.name.isBlank()) "—" else i.name}: ${money(i.amount)}\n") }
    }
    sb.append("\n")
    sb.append(if (bal > 0) tr("wa_remaining", money(bal)) else tr("wa_none") + " ✅")
    sb.append("\n").append(tr("wa_thanks")).append(" 🙏")
    return sb.toString()
}

fun reminderText(shop: String, name: String, bal: Long, due: Long): String {
    val sb = StringBuilder(tr("wa_hello", name)).append("\n").append(tr("wa_remind", shop, money(bal)))
    if (due > 0) sb.append("\n").append(tr("wa_due", day(due)))
    sb.append("\n").append(tr("wa_thanks")).append(" 🙏")
    return sb.toString()
}

fun openWa(ctx: Context, phone: String, text: String) {
    val url = "https://wa.me/" + normPhone(phone) + "?text=" + Uri.encode(text)
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, tr("err_wa")) }
}

fun openSms(ctx: Context, phone: String, text: String) {
    val num = phone.filter { it.isDigit() || it == '+' }
    try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, tr("err_generic")) }
}
