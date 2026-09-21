package dev.meowspool.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
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
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
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
            Group("Tear-off line") {
                ListItem(
                    headlineContent = { Text("Line before print") }, leadingContent = { Icon(Icons.Default.ContentCut, null) }, colors = clear(),
                    trailingContent = { Switch(ui.lineBefore, { ui.lineBefore = it }) },
                )
                ListItem(
                    headlineContent = { Text("Line after print") }, leadingContent = { Icon(Icons.Default.ContentCut, null) }, colors = clear(),
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
                Button(onClick = ui::testPrint, enabled = ui.canTest) { Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text("Print test page") }
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
                    leadingContent = { Icon(Icons.Default.Palette, null) }, colors = clear(),
                    trailingContent = { Switch(ui.dynamicColor, { ui.dynamicColor = it }) },
                )
            }
        }
    }
}
