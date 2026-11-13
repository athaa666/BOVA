package com.bova.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bova.app.R
import com.bova.app.data.*
import com.bova.app.AppSettings
import java.util.Calendar

class AppEnv(val ctx: android.content.Context, val db: BovaDatabase, val settings: AppSettings) {
    val dao: BovaDao get() = db.dao()
}

@Composable
fun BovaLogo(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.bova_mark), null,
            tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun Card2(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, color = color, border = border) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = MaterialTheme.shapes.large, color = color, border = border) {
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Field tanpa garis/kotak — dipakai untuk mengedit catatan langsung di tempat. */
@Composable
fun GhostField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    TextField(
        value = value, onValueChange = onChange, modifier = modifier,
        textStyle = style, singleLine = singleLine,
        placeholder = { Text(placeholder, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun TimeDialog(initialMin: Int, title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Pilih") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

/** Pilih tanggal lalu jam. Mengembalikan epoch millis (zona waktu perangkat). */
@Composable
fun DateTimeDialog(initial: Long?, onDismiss: () -> Unit, onConfirm: (Long) -> Unit, onClear: (() -> Unit)? = null) {
    val base = initial ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
    val localCal = Calendar.getInstance().apply { timeInMillis = base }
    val utcMidnight = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear(); set(localCal.get(Calendar.YEAR), localCal.get(Calendar.MONTH), localCal.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    var step by remember { mutableIntStateOf(0) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = utcMidnight)
    val timeState = rememberTimePickerState(
        initialHour = localCal.get(Calendar.HOUR_OF_DAY), initialMinute = localCal.get(Calendar.MINUTE), is24Hour = true
    )
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { step = 1 }) { Text("Lanjut") } },
            dismissButton = {
                Row {
                    if (onClear != null) TextButton(onClick = onClear) { Text("Hapus") }
                    TextButton(onClick = onDismiss) { Text("Batal") }
                }
            }
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Atur jam pengingat") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = timeState) } },
            confirmButton = {
                TextButton(onClick = {
                    val picked = dateState.selectedDateMillis ?: utcMidnight
                    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = picked }
                    val out = Calendar.getInstance().apply {
                        clear()
                        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                    }
                    onConfirm(out.timeInMillis)
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("Kembali") } }
        )
    }
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter(Char::isDigit)) }, modifier = modifier,
        label = { Text(label) }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun SmallDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun ClickableRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Suppress("unused")
private val keepImports = SolidColor(Color.Black) to Icons.Default.Add
