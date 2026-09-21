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
import java.text.DateFormat
import java.util.Date

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = Color.Transparent)

private fun sourceLabel(s: HistorySource) = when (s) {
    HistorySource.TEST -> "Test page"
    HistorySource.DIRECT -> "Print a file"
    HistorySource.SERVICE -> "Printed from another app"
    HistorySource.API -> "Print server (API / web)"
}
private fun sourceIcon(s: HistorySource): ImageVector = when (s) {
    HistorySource.TEST -> Icons.Default.ReceiptLong
    HistorySource.DIRECT -> Icons.Default.UploadFile
    HistorySource.SERVICE -> Icons.Default.Print
    HistorySource.API -> Icons.Default.Dns
}

@Composable
fun HistoryScreen(pad: PaddingValues) {
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
            items(entries.size) { i -> HistoryRow(entries[i]) }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
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
