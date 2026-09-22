package dev.meowspool.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import dev.meowspool.Dbg
import dev.meowspool.Dither

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
internal fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintSettingsScreen(ui: UiState, pad: PaddingValues) {
    Page(pad) {
        item {
            Group("Quality") {
                val label = when { ui.darkness < 34 -> "Light"; ui.darkness < 67 -> "Normal"; else -> "Dark" }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row { Text("Darkness", Modifier.weight(1f)); Text("$label · ${ui.darkness}%", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.darkness.toFloat(), onValueChange = { ui.darkness = it.toInt() }, valueRange = 0f..100f)
                    Text("Darker prints use more battery and can overheat on long jobs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("Dithering")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Dither.values().forEach { d ->
                            FilterChip(
                                selected = ui.dither == d, onClick = { ui.dither = d }, label = { Text(d.label) },
                                leadingIcon = if (ui.dither == d) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
                            )
                        }
                    }
                    Text(ui.dither.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Group("Finish") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row { Text("Feed after print", Modifier.weight(1f)); Text("${ui.feedMm} mm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.feedMm.toFloat(), onValueChange = { ui.feedMm = it.toInt() }, valueRange = 0f..40f, steps = 39)
                    Text("Extra paper pushed out so you can tear cleanly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Group("Paper control") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row { Text("Feed length", Modifier.weight(1f)); Text("${ui.feedStepMm} mm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.feedStepMm.toFloat(), onValueChange = { ui.feedStepMm = it.toInt() }, valueRange = 5f..100f, steps = 94)
                    Row { Text("Retract length", Modifier.weight(1f)); Text("${ui.retractStepMm} mm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.retractStepMm.toFloat(), onValueChange = { ui.retractStepMm = it.toInt() }, valueRange = 5f..100f, steps = 94)
                    Text("How far the Feed and Retract buttons move paper per tap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ui::feed, enabled = ui.selectedPrinter != null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("Feed")
                        }
                        OutlinedButton(onClick = ui::retract, enabled = ui.selectedPrinter != null, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(8.dp)); Text("Retract")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Group("Margins") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row { Text("Left & right", Modifier.weight(1f)); Text("${ui.marginSideMm} mm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.marginSideMm.toFloat(), onValueChange = { ui.marginSideMm = it.toInt() }, valueRange = 0f..10f, steps = 9)
                    Row { Text("Top & bottom", Modifier.weight(1f)); Text("${ui.marginVertMm} mm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Slider(value = ui.marginVertMm.toFloat(), onValueChange = { ui.marginVertMm = it.toInt() }, valueRange = 0f..10f, steps = 9)
                    Text(
                        "The printer head is 48 mm wide, so that’s the hardest limit: the 5 mm each side of 58 mm paper can’t be printed. " +
                            "These add extra space inside the 48 mm (0 = edge to edge). Apps pick them up the next time you open the print dialog.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Group("Tear-off line") {
                ListItem(
                    headlineContent = { Text("Line before print") }, leadingContent = { OneUiChipIcon(Icons.Default.ContentCut, ui) }, colors = clear(),
                    trailingContent = { Switch(ui.lineBefore, { ui.lineBefore = it }) },
                )
                ListItem(
                    headlineContent = { Text("Line after print") }, leadingContent = { OneUiChipIcon(Icons.Default.ContentCut, ui) }, colors = clear(),
                    trailingContent = { Switch(ui.lineAfter, { ui.lineAfter = it }) },
                )
                if (ui.lineBefore || ui.lineAfter) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!ui.lineDashed, { ui.lineDashed = false }, { Text("Solid") })
                    FilterChip(ui.lineDashed, { ui.lineDashed = true }, { Text("Dashed") })
                }
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ui::requestTestPrint, enabled = ui.canTest) { Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text("Print test page") }
                OutlinedButton(onClick = ui::resetPrintSettings) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset") }
            }
        }
    }
}

@Composable
fun LogScreen(pad: PaddingValues) {
    val version by Dbg.version.collectAsState()
    var errorsOnly by remember { mutableStateOf(false) }
    val lines = remember(version, errorsOnly) { Dbg.snapshot().let { l -> if (errorsOnly) l.filter { " E/" in it } else l } }
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Column(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!errorsOnly, { errorsOnly = false }, { Text("All") })
            FilterChip(errorsOnly, { errorsOnly = true }, { Text("Errors") })
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clip.setText(AnnotatedString(lines.joinToString("\n"))) }) { Icon(Icons.Default.ContentCopy, "Copy") }
            IconButton(onClick = {
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n")), "Share log"))
            }) { Icon(Icons.Default.Share, "Share") }
            IconButton(onClick = Dbg::clear) { Icon(Icons.Default.DeleteSweep, "Clear") }
        }
        if (lines.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest), state = list, contentPadding = PaddingValues(8.dp)) {
                items(lines.size) { i ->
                    val l = lines[i]
                    Text(l, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp,
                        color = if (" E/" in l) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(ui: UiState, pad: PaddingValues) {
    Page(pad) {
        item {
            Group("Theme") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opts = listOf("System", "Light", "Dark")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, o ->
                            SegmentedButton(selected = ui.themeMode == i, onClick = { ui.themeMode = i }, shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) ListItem(
                    headlineContent = { Text("Dynamic colour") }, supportingContent = { Text("Match your wallpaper") },
                    leadingContent = { OneUiChipIcon(Icons.Default.Palette, ui) }, colors = clear(),
                    trailingContent = { Switch(ui.dynamicColor, { ui.dynamicColor = it }) },
                )
                ListItem(
                    headlineContent = { Text("AMOLED black") }, supportingContent = { Text("True black backgrounds in dark mode, easier on OLED screens") },
                    leadingContent = { OneUiChipIcon(Icons.Default.Contrast, ui) }, colors = clear(),
                    trailingContent = { Switch(ui.amoled, { ui.amoled = it }) },
                )
                StyleDropdown(ui)
                ListItem(
                    headlineContent = { Text("Device pictures") }, supportingContent = { Text("Show a picture of your printer on Home when its model is recognised") },
                    leadingContent = { OneUiChipIcon(Icons.Default.Image, ui) }, colors = clear(),
                    trailingContent = { Switch(ui.deviceAvatars, { ui.deviceAvatars = it }) },
                )
            }
        }
        item {
            Group("Font") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UiFont.values().filter { it.isAvailable() }.forEach { f ->
                        ListItem(
                            headlineContent = { Text(f.label) }, colors = clear(),
                            leadingContent = { RadioButton(selected = ui.uiFont == f, onClick = { ui.uiFont = f }) },
                            modifier = Modifier.clickable { ui.uiFont = f },
                        )
                    }
                    if (ui.uiFont.titlesOnly) Text(
                        "${ui.uiFont.label} is a display face, so it's used for titles only; body text stays on Inter.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleDropdown(ui: UiState) {
    var open by remember { mutableStateOf(false) }
    val current = UiStyle.fromPref(ui.uiStyle)
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = when (current) { UiStyle.GLASS -> "Glass"; UiStyle.ONE_UI -> "One UI"; else -> "Material" }, onValueChange = {}, readOnly = true,
            label = { Text("Style") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Material") }, onClick = { ui.uiStyle = "material"; open = false })
            DropdownMenuItem(text = { Text("Glass") }, onClick = { ui.uiStyle = "glass"; open = false })
            DropdownMenuItem(text = { Text("One UI") }, onClick = { ui.uiStyle = "oneui"; open = false })
        }
    }
}
