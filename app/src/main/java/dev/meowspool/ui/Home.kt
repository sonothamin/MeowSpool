package dev.meowspool.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.meowspool.Conn
import dev.meowspool.PState
import dev.meowspool.PrinterManager

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
fun HomeScreen(ui: UiState, pad: PaddingValues, go: (Dest) -> Unit) {
    val states by PrinterManager.states.collectAsState()
    val ctx = LocalContext.current
    Page(pad) {
        ui.crash?.let { c -> item { CrashCard(c, ui::dismissCrash) } }
        if (!ui.serviceOn) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Print, null)
                    Column(Modifier.weight(1f)) {
                        Text("Print service is off", style = MaterialTheme.typography.titleSmall)
                        Text("Turn on “MeowSpool” so apps can print here.", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }) { Text("Enable") }
                }
            }
        }
        item {
            val p = ui.selectedPrinter
            if (p == null) NoPrinter { go(Dest.Devices); ui.requestScan() }
            else PrinterHero(p, states[p.addr] ?: PState(), ui) { go(Dest.Devices) }
        }
        item { SectionHeader("Print setup") }
        item { SetupCard(ui, go) }
    }
}

@Composable
private fun NoPrinter(onFind: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BluetoothSearching, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No printer yet", style = MaterialTheme.typography.titleLarge)
            Text("Turn your printer on and keep it close.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onFind) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Find printers") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrinterHero(p: Printer, st: PState, ui: UiState, onSwitch: () -> Unit) {
    val sum = summarize(st)
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (sum.level) {
        Level.OK -> cs.primaryContainer to cs.onPrimaryContainer
        Level.WARN -> cs.tertiaryContainer to cs.onTertiaryContainer
        Level.ERROR -> cs.errorContainer to cs.onErrorContainer
        Level.INFO -> cs.surfaceVariant to cs.onSurfaceVariant
    }
    val ready = st.conn == Conn.CONNECTED && st.status?.blocking != true && !st.printing && !ui.testing
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(56.dp).background(fg.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    if (sum.loading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = fg)
                    else Icon(if (sum.level == Level.ERROR || sum.level == Level.WARN) Icons.Default.Warning else Icons.Default.Print, null, Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.labelLarge)
                    Text(sum.title, style = MaterialTheme.typography.headlineSmall)
                    sum.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            StatusChips(statusItems(st), fg)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = ui::testPrint, enabled = ready, colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg)) {
                    if (ui.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.ReceiptLong, null)
                    Spacer(Modifier.width(8.dp)); Text(if (ui.testing) "Printing…" else "Test page")
                }
                if (st.conn == Conn.ERROR) FilledTonalButton(onClick = { PrinterManager.reconnect(p.addr) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = fg.copy(alpha = 0.16f), contentColor = fg)) {
                    Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reconnect")
                }
                OutlinedButton(
                    onClick = onSwitch,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
                    border = BorderStroke(1.dp, fg.copy(alpha = 0.6f)),
                ) { Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(8.dp)); Text("Switch printer") }
            }
        }
    }
}

@Composable
private fun SetupCard(ui: UiState, go: (Dest) -> Unit) {
    val chevron = @Composable { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            headlineContent = { Text("Paper") }, supportingContent = { Text("${ui.paper.name} · ${ui.paper.sizeText}") },
            leadingContent = { Icon(Icons.Default.Description, null) }, trailingContent = chevron, colors = clear(),
            modifier = Modifier.clickable { go(Dest.Paper) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Print settings") }, supportingContent = { Text("Darkness ${ui.darkness}% · ${ui.finishSummary}") },
            leadingContent = { Icon(Icons.Default.Tune, null) }, trailingContent = chevron, colors = clear(),
            modifier = Modifier.clickable { go(Dest.Print) },
        )
    }
}

@Composable
private fun CrashCard(crash: String, onDismiss: () -> Unit) {
    val clip = LocalClipboardManager.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("The app crashed last time", style = MaterialTheme.typography.titleMedium)
            Text(crash.lines().take(6).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { clip.setText(AnnotatedString(crash)) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.onErrorContainer, contentColor = MaterialTheme.colorScheme.errorContainer)) { Text("Copy details") }
                TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("Dismiss") }
            }
        }
    }
}
