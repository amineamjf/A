package ma.aminehanout.app

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

// Process-wide scope so a backup started in onStop is not cancelled with the activity.
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

const val AUTO_NAME = "amine-app-backup.json"
private const val EVERY_MS = 12L * 3600_000L

// Writes the full backup into the folder the user picked (for example inside Google Drive).
// Runs at most every 12 hours unless force = true. Returns true when a file was written.
suspend fun runAutoBackup(ctx: Context, dao: LedgerDao, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
    val p = prefs(ctx)
    val u = p.getString("auto_uri", null) ?: return@withContext false
    if (!force && System.currentTimeMillis() - p.getLong("auto_last", 0L) < EVERY_MS) return@withContext false
    try {
        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(u)) ?: return@withContext false
        val f = dir.findFile(AUTO_NAME) ?: dir.createFile("application/json", AUTO_NAME) ?: return@withContext false
        val json = toJson(dao.customersOnce(), dao.txnsOnce(), dao.itemsOnce(), dao.cashOnce())
        val out = ctx.contentResolver.openOutputStream(f.uri, "wt") ?: return@withContext false
        out.use { it.write(json.toByteArray()) }
        p.edit().putLong("auto_last", System.currentTimeMillis()).apply()
        true
    } catch (e: Exception) {
        false
    }
}
