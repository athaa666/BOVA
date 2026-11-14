package com.bova.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.bova.app.*
import kotlinx.coroutines.delay

/** Layar kunci: biometrik/PIN HP, atau PIN BOVA dengan pembatasan percobaan. */
@Composable
fun LockScreen(s: AppSettings, onUnlock: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? FragmentActivity
    val usePin = s.lockMethod == 1 && s.hasPin
    val canBio = remember { Biometrics.available(ctx) }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(s.pinLockRemaining()) }
    var shake by remember { mutableStateOf(false) }

    fun askBiometric() {
        if (activity == null || !canBio) { message = "Verifikasi perangkat tidak tersedia."; return }
        Biometrics.prompt(activity, onSuccess = { onUnlock() }, onError = { message = it })
    }
    LaunchedEffect(Unit) { if ((!usePin || s.biometricOnPin) && canBio) askBiometric() }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait = s.pinLockRemaining() } }

    fun submit() {
        val r = s.checkPin(pin)
        pin = ""
        when {
            r == 0 -> onUnlock()
            r > 0 -> { wait = r; message = "Terlalu banyak percobaan. Coba lagi nanti." }
            else -> { message = "PIN salah"; shake = !shake }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        BovaLogo(64.dp)
        Spacer(Modifier.height(16.dp))
        Text("BOVA terkunci", style = MaterialTheme.typography.headlineSmall)
        Text(if (usePin) "Masukkan PIN untuk melanjutkan" else "Verifikasi untuk membuka", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        if (usePin) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.height(16.dp)) {
                for (i in 0 until 6) {
                    val filled = i < pin.length
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (wait > 0) "Tunggu $wait detik" else (message ?: " "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.height(20.dp)
            )
            Spacer(Modifier.height(12.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            for (row in 0 until 4) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    for (col in 0 until 3) {
                        val k = keys[row * 3 + col]
                        Box(
                            Modifier.size(68.dp).clip(CircleShape).background(if (k == "bio" && !(canBio && s.biometricOnPin)) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            when (k) {
                                "bio" -> { if (canBio && s.biometricOnPin) IconButton(onClick = { askBiometric() }) { Icon(Icons.Default.Fingerprint, "Biometrik") } }
                                "del" -> { IconButton(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) { Icon(Icons.Default.Backspace, "Hapus") } }
                                else -> TextButton(onClick = {
                                    if (wait > 0 || pin.length >= 6) return@TextButton
                                    pin += k; message = null
                                    if (pin.length >= 4 && pin.length == 6) submit()
                                }, modifier = Modifier.fillMaxSize()) { Text(k, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { if (pin.length >= 4) submit() }, enabled = pin.length >= 4 && wait == 0) { Text("Buka") }
        } else {
            if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { askBiometric() }, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("Buka kunci")
            }
        }
    }
}
