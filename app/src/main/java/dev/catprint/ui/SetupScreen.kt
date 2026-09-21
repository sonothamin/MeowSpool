package dev.catprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.catprint.Dbg
import dev.catprint.PState
import dev.catprint.Prefs

data class Printer(val addr: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    saved: List<Printer>,
    nearby: List<Printer>,
    selected: String?,
    states: Map<String, PState>,
    scanning: Boolean,
    serviceOn: Boolean,
    snackbar: SnackbarHostState,
    onScan: () -> Unit,
    onSelect: (Printer) -> Unit,
    onRemove: (Printer) -> Unit,
    onReconnect: (String) -> Unit,
    onOpenPrintSettings: () -> Unit,
    crash: String?,
    onDismissCrash: () -> Unit,
) {
    var debug by remember { mutableStateOf(Prefs.debug) }
    var darkness by remember { mutableFloatStateOf(Prefs.darkness.toFloat()) }
    var showLog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MeowSpool") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!scanning) onScan() },
                icon = {
                    if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null)
                },
                text = { Text(if (scanning) "Scanning…" else "Scan for printers") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (crash != null) item {
                val clip = LocalClipboardManager.current
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The app crashed last time", style = MaterialTheme.typography.titleMedium)
                        Text(crash.lines().take(6).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 6)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { clip.setText(AnnotatedString(crash)) }) { Text("Copy details") }
                            TextButton(onClick = onDismissCrash) { Text("Dismiss") }
                        }
                    }
                }
            }
            if (!serviceOn) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Print service is off", style = MaterialTheme.typography.titleMedium)
                        Text("Enable “Cat Printer Service” so apps can print to your printer.", style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = onOpenPrintSettings) { Text("Open print settings") }
                    }
                }
            }

            item {
                val sel = saved.firstOrNull { it.addr == selected }
                if (sel == null) EmptyState()
                else {
                    val st = states[sel.addr] ?: PState()
                    val sum = summarize(st)
                    StatusCard(sel.name, sum, st.status, showRetry = sum.level == Level.ERROR && !sum.loading) { onReconnect(sel.addr) }
                }
            }

            item { SectionHeader("Print quality") }
            item {
                val label = when { darkness < 34 -> "Light"; darkness < 67 -> "Normal"; else -> "Dark" }
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Darkness"); Text("$label · ${darkness.toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Slider(
                        value = darkness, onValueChange = { darkness = it },
                        onValueChangeFinished = { Prefs.darkness = darkness.toInt() }, valueRange = 0f..100f,
                    )
                    Text("Darker prints use more battery and can overheat on long jobs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (saved.isNotEmpty()) {
                item { SectionHeader("My printers") }
                items(saved, key = { "s" + it.addr }) { p ->
                    val st = states[p.addr]
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text(if (p.addr == selected && st != null) summarize(st).title else p.addr) },
                        leadingContent = { RadioButton(selected = p.addr == selected, onClick = null) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(p) }) { Icon(Icons.Default.Delete, "Forget ${p.name}") }
                        },
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(p) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }

            val fresh = nearby.filter { n -> saved.none { it.addr == n.addr } }
            if (scanning || nearby.isNotEmpty()) {
                item { SectionHeader("Nearby") }
                if (fresh.isEmpty()) item {
                    Text(if (scanning) "Looking for printers… turn yours on and keep it close." else "No new printers found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(fresh, key = { "n" + it.addr }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) }, supportingContent = { Text(p.addr) },
                        trailingContent = { FilledTonalButton(onClick = { onSelect(p) }) { Text("Add") } },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }

            item { SectionHeader("Advanced") }
            item {
                ListItem(
                    headlineContent = { Text("Debug logging") },
                    trailingContent = { Switch(checked = debug, onCheckedChange = { debug = it; Prefs.debug = it; Dbg.d("Setup", "debug logging $it") }) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                )
                if (debug) TextButton(onClick = { showLog = true }) { Text("View debug log") }
            }
        }
    }
    if (showLog) LogDialog { showLog = false }
}

@Composable
private fun EmptyState() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No printer selected", style = MaterialTheme.typography.titleMedium)
            Text("Turn your printer on, then tap “Scan for printers”.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LogDialog(onClose: () -> Unit) {
    var lines by remember { mutableStateOf(Dbg.snapshot().joinToString("\n")) }
    val clip = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Debug log") },
        text = {
            SelectionContainer {
                Text(
                    lines.ifEmpty { "(empty — enable debug logging and try again)" },
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                    modifier = Modifier.heightIn(max = 360.dp).verticalScrollCompat(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        dismissButton = {
            Row {
                TextButton(onClick = { Dbg.clear(); lines = "" }) { Text("Clear") }
                TextButton(onClick = { clip.setText(AnnotatedString(lines)) }) { Text("Copy") }
            }
        },
    )
}
