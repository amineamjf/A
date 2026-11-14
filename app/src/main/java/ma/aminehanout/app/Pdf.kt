package ma.aminehanout.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Date

// Builds an A4 account statement for one customer. Returns a shareable Uri, or null on failure.
fun makeStatementPdf(ctx: Context, shopName: String, c: Customer, txns: List<Txn>, items: List<TxnItem>): Uri? {
    return try {
        val rtl = lang != "fr"
        val w = 595
        val h = 842
        val doc = PdfDocument()
        val p = Paint().apply { isAntiAlias = true; textSize = 10f; color = android.graphics.Color.BLACK; textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT }
        fun x(r: Float): Float = if (rtl) r else w - r
        val colDate = 555f; val colNote = 480f; val colGave = 300f; val colGot = 215f; val colBal = 130f
        val byTxn = items.groupBy { it.txnId }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var cv: Canvas? = null
        var y = 0f

        fun header() {
            val k = cv!!
            p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 18f
            k.drawText(shopName, x(555f), 50f, p)
            p.textSize = 13f
            k.drawText(tr("pdf_title") + " - " + c.name, x(555f), 74f, p)
            p.typeface = Typeface.DEFAULT; p.textSize = 10f
            if (c.phone.isNotBlank()) k.drawText(c.phone, x(555f), 90f, p)
            k.drawText(tr("pdf_gen", digits(day(Date().time))), x(555f), 104f, p)
            y = 130f
            p.typeface = Typeface.DEFAULT_BOLD
            k.drawText(tr("pdf_date"), x(colDate), y, p)
            k.drawText(tr("pdf_note"), x(colNote), y, p)
            k.drawText(tr("gave"), x(colGave), y, p)
            k.drawText(tr("got"), x(colGot), y, p)
            k.drawText(tr("balance"), x(colBal), y, p)
            p.typeface = Typeface.DEFAULT
            y += 8f
            k.drawLine(30f, y, (w - 30).toFloat(), y, p)
            y += 14f
        }
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val pg = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            page = pg; cv = pg.canvas
            header()
        }
        fun footer() {
            p.textSize = 9f
            cv!!.drawText(tr("pdf_page", pageNo), x(555f), (h - 24).toFloat(), p)
            p.textSize = 10f
        }

        newPage()
        var run = 0L
        for (t in txns) {
            run += if (t.isDebt) t.amount else -t.amount
            val lines = byTxn[t.id] ?: emptyList()
            val need = 14f + lines.size * 12f
            if (y + need > h - 60) { footer(); newPage() }
            val k = cv!!
            k.drawText(digits(day(t.date)), x(colDate), y, p)
            k.drawText(t.note.take(26), x(colNote), y, p)
            if (t.isDebt) k.drawText(fmtP(t.amount), x(colGave), y, p) else k.drawText(fmtP(t.amount), x(colGot), y, p)
            k.drawText(fmtP(run), x(colBal), y, p)
            y += 12f
            p.textSize = 8.5f
            for (i in lines) {
                k.drawText("• " + (if (i.name.isBlank()) "—" else i.name) + ": " + fmtP(i.amount), x(colNote), y, p)
                y += 12f
            }
            p.textSize = 10f
            y += 2f
        }
        if (y + 40 > h - 40) { footer(); newPage() }
        val k = cv!!
        y += 6f
        k.drawLine(30f, y, (w - 30).toFloat(), y, p)
        y += 18f
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 12f
        k.drawText(tr("pdf_total") + ": " + fmt(run), x(555f), y, p)
        footer()
        page?.let { doc.finishPage(it) }

        val dir = File(ctx.cacheDir, "pdf").apply { mkdirs() }
        val safe = c.name.filter { it.isLetterOrDigit() }.ifBlank { "customer" }.take(30)
        val f = File(dir, "statement-$safe.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    } catch (e: Exception) {
        null
    }
}

fun sharePdf(ctx: Context, uri: Uri) {
    val i = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { toast(ctx, tr("err_generic")) }
}
