package dev.meowspool.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun paperColors(on: Boolean) = if (on) ListItemDefaults.colors(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    headlineColor = MaterialTheme.colorScheme.onPrimaryContainer,
    supportingColor = MaterialTheme.colorScheme.onPrimaryContainer,
    leadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    trailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
) else clear()

@Composable
fun PaperScreen(ui: UiState, pad: PaddingValues) {
    var adding by remember { mutableStateOf(false) }
    Page(pad) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, null)
                    Text("Your printer takes 58 mm paper and prints 48 mm across, centred (the print head’s fixed width). Adjust extra margins in Print settings. Pick the size you loaded; apps offer it when printing.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(ui.papers.size) { i ->
            val p = ui.papers[i]; val on = p.id == ui.paperId
            Card(colors = CardDefaults.cardColors(containerColor = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text(p.name) }, supportingContent = { Text(p.sizeText) },
                    leadingContent = { RadioButton(selected = on, onClick = null) },
                    trailingContent = { if (!p.builtIn) IconButton(onClick = { ui.removePaper(p) }) { Icon(Icons.Default.Delete, "Remove ${p.name}") } },
                    colors = paperColors(on), modifier = Modifier.clickable { ui.selectPaper(p) },
                )
            }
        }
        item { OutlinedButton(onClick = { adding = true }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add custom size") } }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var len by remember { mutableStateOf("") }
        val mm = len.toIntOrNull()
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Custom paper (58 mm wide)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(len, { len = it.filter(Char::isDigit).take(3) }, label = { Text("Length (mm)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = len.isNotEmpty() && (mm == null || mm < 10))
                }
            },
            confirmButton = {
                TextButton(enabled = mm != null && mm >= 10, onClick = { ui.addPaper(name.ifBlank { "58 × $mm mm" }, mm!!); adding = false }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
