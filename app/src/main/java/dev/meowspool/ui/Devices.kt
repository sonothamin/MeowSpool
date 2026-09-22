package dev.meowspool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.meowspool.PState
import dev.meowspool.PrinterManager

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
fun DevicesScreen(ui: UiState, pad: PaddingValues) {
    val states by PrinterManager.states.collectAsState()
    val found by ui.found.collectAsState()
    val scanning by ui.scanning.collectAsState()
    val fresh = found.filter { (a, _) -> ui.saved.none { it.addr == a } }.map { Printer(it.key, it.value) }
    Page(pad) {
        if (ui.saved.isNotEmpty()) {
            item { SectionHeader("My printers") }
            items(ui.saved.size, key = { ui.saved[it].addr }) { i ->
                val p = ui.saved[i]; val on = p.addr == ui.selected
                val sum = if (on) summarize(states[p.addr] ?: PState()) else null
                Card(
                    modifier = Modifier.animateItem(), // smooth reflow when a saved printer's selection/status changes
                    colors = CardDefaults.cardColors(containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text(sum?.title ?: p.addr) },
                        leadingContent = { OneUiChipIcon(if (on) Icons.Default.Print else Icons.Default.Bluetooth, ui) },
                        trailingContent = { IconButton(onClick = { ui.remove(p) }) { Icon(Icons.Default.Delete, "Forget ${p.name}") } },
                        colors = clear(), modifier = Modifier.clickable { ui.select(p) },
                    )
                }
            }
        }
        item { SectionHeader("Nearby") }
        if (fresh.isEmpty()) item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Default.BluetoothSearching, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (scanning) "Looking for printers… turn yours on and keep it close." else "No new printers. Tap Scan to search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(fresh.size, key = { fresh[it].addr }) { i ->
            val p = fresh[i]
            Card(
                modifier = Modifier.animateItem(), // newly-discovered printers slide/fade into place as scan results stream in
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                ListItem(
                    headlineContent = { Text(p.name) }, supportingContent = { Text(p.addr) },
                    leadingContent = { OneUiChipIcon(Icons.Default.Bluetooth, ui) },
                    trailingContent = { FilledTonalButton(onClick = { ui.select(p) }) { Text("Add") } }, colors = clear(),
                )
            }
        }
    }
}
