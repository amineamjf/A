package ma.aminehanout.app

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

private fun hashPin(pin: String, salt: String): String =
    MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

fun pinEnabled(ctx: Context): Boolean = prefs(ctx).getString("pin_hash", null) != null

fun setPin(ctx: Context, pin: String) {
    val salt = UUID.randomUUID().toString()
    prefs(ctx).edit().putString("pin_salt", salt).putString("pin_hash", hashPin(pin, salt)).apply()
}

fun checkPin(ctx: Context, pin: String): Boolean {
    val p = prefs(ctx)
    val h = p.getString("pin_hash", null) ?: return true
    return hashPin(pin, p.getString("pin_salt", "") ?: "") == h
}

fun clearPin(ctx: Context) {
    prefs(ctx).edit().remove("pin_hash").remove("pin_salt").putBoolean("bio", false).apply()
}

fun bioEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("bio", false)
fun setBio(ctx: Context, v: Boolean) { prefs(ctx).edit().putBoolean("bio", v).apply() }

fun canBiometric(ctx: Context): Boolean =
    BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun promptBiometric(act: FragmentActivity, onOk: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(tr("unlock_title"))
        .setNegativeButtonText(tr("cancel"))
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        .build()
    val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
    })
    prompt.authenticate(info)
}

@Composable
fun LockScreen(act: FragmentActivity, onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bioOk = remember { bioEnabled(act) && canBiometric(act) }
    LaunchedEffect(Unit) { if (bioOk) promptBiometric(act, onUnlock) }

    fun press(d: String) {
        if (pin.length >= 4) return
        pin += d
        err = false
        if (pin.length == 4) {
            if (checkPin(act, pin)) onUnlock() else { err = true; pin = "" }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(tr("unlock_title"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(4) { i ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (i < pin.length) Brand else Color.Gray.copy(alpha = .35f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (err) tr("pin_wrong") else " ", color = Red, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            if (k.isEmpty()) Spacer(Modifier.size(72.dp))
                            else Box(
                                Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                                    .clickable { if (k == "<") { pin = pin.dropLast(1); err = false } else press(k) },
                                contentAlignment = Alignment.Center
                            ) { Text(if (k == "<") "⌫" else k, fontSize = 24.sp) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (bioOk) TextButton({ promptBiometric(act, onUnlock) }) { Text(tr("use_bio")) }
        TextButton({ reset = true }) { Text(tr("forgot"), color = Color.Gray) }
    }

    if (reset) AlertDialog(
        onDismissRequest = { reset = false },
        title = { Text(tr("reset_title")) },
        text = { Text(tr("reset_body")) },
        confirmButton = {
            TextButton({
                reset = false
                scope.launch {
                    withContext(Dispatchers.IO) { AppDb.get(act).clearAllTables() }
                    clearPin(act)
                    onUnlock()
                }
            }) { Text(tr("delete"), color = Red) }
        },
        dismissButton = { TextButton({ reset = false }) { Text(tr("cancel")) } }
    )
}
