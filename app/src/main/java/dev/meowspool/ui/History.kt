package dev.meowspool.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.meowspool.History
import dev.meowspool.HistoryEntry
import dev.meowspool.HistorySource
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.meowspool.CatProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = Color.Transparent)

private fun sourceLabel(s: HistorySource) = when (s) {
    HistorySource.TEST -> "Test page"
    HistorySource.DIRECT -> "Print a file"
    HistorySource.SERVICE -> "Printed from another app"
    HistorySource.API -> "Print server (API / web)"
    HistorySource.REPRINT -> "Reprint"
}
private fun sourceIcon(s: HistorySource): ImageVector = when (s) {
    HistorySource.TEST -> Icons.Default.ReceiptLong
    HistorySource.DIRECT -> Icons.Default.UploadFile
    HistorySource.SERVICE -> Icons.Default.Print
    HistorySource.API -> Icons.Default.Dns
    HistorySource.REPRINT -> Icons.Default.Replay
}

@Composable
fun HistoryScreen(pad: PaddingValues, onOpen: (HistoryEntry) -> Unit) {
    val entries by History.entries.collectAsState()
    Page(pad) {
        if (entries.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.History, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("No prints yet", style = MaterialTheme.typography.titleLarge)
                        Text("Test pages and prints show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { History.clear() }) { Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(8.dp)); Text("Clear all") }
                }
            }
            items(entries.size) { i -> HistoryRow(entries[i]) { onOpen(entries[i]) } }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = remember(e.id) { runCatching { BitmapFactory.decodeFile(e.thumbFile.path) }.getOrNull() }
            Box(
                Modifier.size(44.dp, 60.dp).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(sourceIcon(e.source), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text("${sourceLabel(e.source)} · ${e.printerName}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!e.ok) Text(e.error ?: "Failed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
            if (!e.ok) Icon(Icons.Default.ErrorOutline, "Failed", tint = MaterialTheme.colorScheme.error)
            IconButton(onClick = { History.remove(e) }) { Icon(Icons.Default.Delete, "Remove from history") }
        }
    }
}

@Composable
fun HistoryDetailScreen(ui: UiState, pad: PaddingValues, e: HistoryEntry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasData = remember(e.id) { e.rowsFile.exists() }
    val preview = remember(e.id) {
        runCatching {
            if (hasData) History.loadRows(e)?.let { CatProtocol.rowsToBitmap(it.take(12000)) } else BitmapFactory.decodeFile(e.thumbFile.path)
        }.getOrNull()
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { History.writePng(e, it) } }.isSuccess
            ui.notify(if (ok) "Image saved" else "Couldn't save image")
        }
    }
    fun share() = scope.launch(Dispatchers.IO) {
        runCatching {
            val f = File(File(ctx.cacheDir, "share").apply { mkdirs() }, "print-${e.id.take(8)}.png")
            f.outputStream().use { History.writePng(e, it) }
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) { ctx.startActivity(Intent.createChooser(i, "Share print")) }
        }.onFailure { ui.notify("Couldn't share image") }
    }
    val cs = MaterialTheme.colorScheme
    Page(pad) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.widthIn(max = 320.dp).fillMaxWidth().background(Color.White).border(1.dp, cs.outlineVariant)) {
                    if (preview != null) Image(preview.asImageBitmap(), "Printed page", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { Icon(sourceIcon(e.source), null, tint = cs.onSurfaceVariant) }
                }
            }
        }
        if (!hasData) item { Text("Full page data wasn’t saved for this older print, so only a preview is available.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ui.reprint(e) }, enabled = hasData && !ui.reprinting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Default.Replay, null); Spacer(Modifier.width(8.dp)); Text(if (ui.reprinting) "Printing…" else "Print again")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { share() }, enabled = preview != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share") }
                    OutlinedButton(onClick = { save.launch("meowspool-${e.id.take(8)}.png") }, enabled = preview != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Save image") }
                }
                TextButton(onClick = { History.remove(e); onClose() }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete from history")
                }
            }
        }
        item {
            Group("Details") {
                @Composable fun row(label: String, value: String, color: Color = Color.Unspecified) =
                    ListItem(overlineContent = { Text(label) }, headlineContent = { Text(value, color = color) }, colors = clear())
                row("Result", if (e.ok) "Printed" else e.error ?: "Failed", if (e.ok) Color.Unspecified else cs.error)
                row("Source", sourceLabel(e.source))
                row("Printer", e.printerName)
                row("Time", DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(Date(e.time)))
                row("Length", "${e.rows} rows · about ${e.rows / 8} mm")
                row("Darkness", "${e.darkness}%")
                row("Feed after print", "${e.feedMm} mm")
            }
        }
    }
}
