package dev.meowspool.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.meowspool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
private fun clear() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SliderRow(label: String, valueText: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row { Text(label, Modifier.weight(1f)); Text(valueText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = v, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onChange: (Boolean) -> Unit) =
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, colors = clear(), trailingContent = { Switch(on, onChange) })

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrintFileScreen(ui: UiState, pad: PaddingValues, go: (Dest) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by remember { mutableStateOf<DocSource?>(null) }
    var s by remember { mutableStateOf(PrintJobSettings()) }
    var page by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { doc?.close() } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { runCatching { DocSource.open(ctx, uri) } }
                .onSuccess { nd -> doc?.close(); doc = nd; s = nd.defaults(); page = 0; preview = null }
                .onFailure { Dbg.e("Direct", "open failed", it); ui.notify("Couldn’t open that file: ${it.message ?: "unsupported"}") }
        }
    }
    val cur = page.coerceIn(s.firstPage, s.lastPage.coerceAtLeast(s.firstPage))

    val d0 = doc
    LaunchedEffect(d0, s, cur) {
        if (d0 == null) { preview = null; return@LaunchedEffect }
        delay(150); rendering = true
        val bmp = withContext(Dispatchers.Default) {
            runCatching { CatProtocol.rowsToBitmap(CatProtocol.toRows(Composer.compose(d0.page(cur), s), s.dither)) }.getOrNull()
        }
        if (bmp != null) preview = bmp
        rendering = false
    }

    fun send() {
        val d = doc ?: return; val p = ui.selectedPrinter ?: return
        val job = s; sending = true
        scope.launch(Dispatchers.IO) {
            val err = try {
                val rows = ArrayList<ByteArray>()
                repeat(job.copies) { c ->
                    if (c > 0) rows += CatProtocol.separator(true)
                    for (i in job.firstPage..job.lastPage) rows += CatProtocol.toRows(Composer.compose(d.page(i), job), job.dither)
                }
                PrintEngine.sendRows(p.addr, rows, job.options())
                null
            } catch (e: Throwable) { Dbg.e("Direct", "print failed", e); e.message ?: "Print failed" }
            sending = false
            ui.notify(if (err == null) "Sent to ${p.name}" else "Print failed: $err")
        }
    }

    Page(pad) {
        val d = doc
        if (d == null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.UploadFile, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Print a photo or PDF", style = MaterialTheme.typography.titleLarge)
                        Text("Pick a file, tweak how it looks, and print it straight to your printer.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose file") }
                        Text(
                            "Word, web pages and other documents: open them in their own app, then Print → MeowSpool.",
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            return@Page
        }
        val multi = d.isPdf && d.pageCount > 1
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text(d.name, maxLines = 1) }, supportingContent = { Text(d.info) },
                    leadingContent = { Icon(if (d.isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image, null) }, colors = clear(),
                    trailingContent = {
                        Row {
                            IconButton(onClick = { pick.launch(arrayOf("image/*", "application/pdf")) }) { Icon(Icons.Default.FolderOpen, "Choose another file") }
                            IconButton(onClick = { doc?.close(); doc = null; preview = null }) { Icon(Icons.Default.Close, "Close file") }
                        }
                    },
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.widthIn(max = 384.dp).fillMaxWidth().heightIn(min = 160.dp).background(Color.White).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it.asImageBitmap(), "Print preview", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                        if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                    if (multi && s.lastPage > s.firstPage) Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = cur - 1 }, enabled = cur > s.firstPage) { Icon(Icons.Default.ChevronLeft, "Previous page") }
                        Text("Page ${cur + 1} of ${d.pageCount}")
                        IconButton(onClick = { page = cur + 1 }, enabled = cur < s.lastPage) { Icon(Icons.Default.ChevronRight, "Next page") }
                    }
                    Text("Preview is exactly what prints, 48 mm wide.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { send() }, enabled = ui.selectedPrinter != null && !sending && preview != null, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = LocalContentColor.current) else Icon(Icons.Default.Print, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sending) "Printing…" else if (s.copies > 1) "Print ${s.copies} copies" else "Print", style = MaterialTheme.typography.titleMedium)
                }
                if (ui.selectedPrinter == null) TextButton(onClick = { go(Dest.Devices) }) { Text("Add a printer first") }
            }
        }
        item {
            Group("Layout") {
                SliderRow("Size", "${s.sizePct}%", s.sizePct.toFloat(), 25f..100f) { s = s.copy(sizePct = it.roundToInt()) }
                SliderRow("Left & right margin", "${s.sideMm} mm", s.sideMm.toFloat(), 0f..10f, 9) { s = s.copy(sideMm = it.roundToInt()) }
                SliderRow("Top & bottom margin", "${s.vertMm} mm", s.vertMm.toFloat(), 0f..10f, 9) { s = s.copy(vertMm = it.roundToInt()) }
                if (multi) Column(Modifier.padding(horizontal = 16.dp)) {
                    Row { Text("Pages", Modifier.weight(1f)); Text("${s.firstPage + 1}–${s.lastPage + 1} of ${d.pageCount}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    RangeSlider(
                        value = s.firstPage.toFloat()..s.lastPage.toFloat(),
                        onValueChange = { r -> s = s.copy(firstPage = r.start.roundToInt(), lastPage = r.endInclusive.roundToInt()) },
                        valueRange = 0f..(d.pageCount - 1).toFloat(), steps = (d.pageCount - 2).coerceAtLeast(0),
                    )
                }
                ListItem(
                    headlineContent = { Text("Rotate") }, supportingContent = { Text("${s.rotation}°") }, leadingContent = { Icon(Icons.Default.RotateRight, null) }, colors = clear(),
                    trailingContent = { OutlinedButton(onClick = { s = s.copy(rotation = (s.rotation + 90) % 360) }) { Text("Rotate 90°") } },
                )
                ListItem(
                    headlineContent = { Text("Copies") }, leadingContent = { Icon(Icons.Default.ContentCopy, null) }, colors = clear(),
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { s = s.copy(copies = s.copies - 1) }, enabled = s.copies > 1) { Icon(Icons.Default.Remove, "Fewer") }
                            Text("${s.copies}", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { s = s.copy(copies = s.copies + 1) }, enabled = s.copies < 20) { Icon(Icons.Default.Add, "More") }
                        }
                    },
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth().clickable { advanced = !advanced }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("Advanced settings") }, supportingContent = { Text("Brightness, contrast, dithering, darkness…") },
                    leadingContent = { Icon(Icons.Default.Tune, null) }, colors = clear(),
                    trailingContent = { Icon(if (advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) },
                )
            }
        }
        if (advanced) {
            item {
                Group("Image") {
                    SliderRow("Brightness", "${s.brightness}", s.brightness.toFloat(), -100f..100f) { s = s.copy(brightness = it.roundToInt()) }
                    SliderRow("Contrast", "${s.contrast}", s.contrast.toFloat(), -100f..100f) { s = s.copy(contrast = it.roundToInt()) }
                    SwitchRow("Invert (negative)", Icons.Default.InvertColors, s.invert) { s = s.copy(invert = it) }
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("Dithering")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dither.values().forEach { dd -> FilterChip(selected = s.dither == dd, onClick = { s = s.copy(dither = dd) }, label = { Text(dd.label) }) }
                        }
                        Text(s.dither.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Group("Output") {
                    SliderRow("Darkness", "${s.darkness}%", s.darkness.toFloat(), 0f..100f) { s = s.copy(darkness = it.roundToInt()) }
                    SliderRow("Feed after print", "${s.feedMm} mm", s.feedMm.toFloat(), 0f..40f, 39) { s = s.copy(feedMm = it.roundToInt()) }
                    SwitchRow("Tear-off line before", Icons.Default.ContentCut, s.lineBefore) { s = s.copy(lineBefore = it) }
                    SwitchRow("Tear-off line after", Icons.Default.ContentCut, s.lineAfter) { s = s.copy(lineAfter = it) }
                }
            }
        }
        item { OutlinedButton(onClick = { s = d.defaults() }) { Icon(Icons.Default.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset these settings") } }
    }
}
