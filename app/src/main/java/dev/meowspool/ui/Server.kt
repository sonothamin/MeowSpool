package dev.meowspool.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.meowspool.Power
import dev.meowspool.ServerService

@Composable
private fun clearColors() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

/** Always black-on-white so it scans in dark theme too. */
private fun qr(text: String, px: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
    return bmp
}

@Composable
fun ServerScreen(ui: UiState, pad: PaddingValues) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val srv by ServerService.state.collectAsState()
    var showQr by remember { mutableStateOf(false) }
    var port by remember(ui.serverPort) { mutableStateOf(ui.serverPort.toString()) }
    val portOk = port.toIntOrNull()?.let { it in 1024..65535 } == true
    val url = if (srv.running) ServerService.url() else null

    Page(pad) {
        item {
            Group("Print server") {
                ListItem(
                    headlineContent = { Text("Run print server") },
                    supportingContent = { Text(when {
                        srv.error != null -> srv.error!!
                        srv.running -> url ?: "Running (no network address found)"
                        ui.serverEnabled -> "Starting…"
                        else -> "Off: print from other devices, scripts or a browser"
                    }, color = if (srv.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Dns, null) }, colors = clearColors(),
                    trailingContent = { Switch(ui.serverEnabled, { ui.setServer(it) }) },
                )
                if (srv.running) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (srv.lan && url != null) FilledTonalButton(onClick = { showQr = true }) { Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("Show QR") }
                    if (url != null) OutlinedButton(onClick = { clip.setText(AnnotatedString(ServerService.url(true) ?: url)) }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Copy link") }
                }
            }
        }
        if (ui.serverEnabled && !ui.batteryOk) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.BatteryAlert, null); Text("Battery optimisation is on", style = MaterialTheme.typography.titleSmall) }
                    Text("Android may put the server (and the printer connection) to sleep when the screen is off. Allow MeowSpool to run in the background.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { Power.request(ctx) }) { Text("Allow background use") }
                }
            }
        }
        item {
            Group("Access") {
                ListItem(
                    headlineContent = { Text("Network access") }, supportingContent = { Text(if (ui.serverLan) "Other devices on your Wi-Fi can print" else "This phone only (127.0.0.1)") },
                    leadingContent = { Icon(Icons.Default.Wifi, null) }, colors = clearColors(), trailingContent = { Switch(ui.serverLan, { ui.serverLan = it }) },
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true, isError = !portOk,
                    supportingText = { Text(if (portOk) "Press done to apply" else "Use 1024–65535") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (portOk) ui.serverPort = port.toInt() }),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                ListItem(
                    headlineContent = { Text("Require access token") }, supportingContent = { Text(if (ui.serverAuth) "Requests without the token are refused" else "Anyone on the network can print") },
                    leadingContent = { Icon(Icons.Default.Key, null) }, colors = clearColors(), trailingContent = { Switch(ui.serverAuth, { ui.serverAuth = it }) },
                )
                if (ui.serverAuth) ListItem(
                    headlineContent = { Text(ui.token, fontFamily = FontFamily.Monospace) }, supportingContent = { Text("Send as “Authorization: Bearer …”") }, colors = clearColors(),
                    trailingContent = { Row {
                        IconButton(onClick = { clip.setText(AnnotatedString(ui.token)) }) { Icon(Icons.Default.ContentCopy, "Copy token") }
                        IconButton(onClick = ui::regenToken) { Icon(Icons.Default.Refresh, "New token") }
                    } },
                )
            }
        }
        item {
            Group("Interfaces") {
                ListItem(
                    headlineContent = { Text("Web page") }, supportingContent = { Text("Upload and print from any browser") },
                    leadingContent = { Icon(Icons.Default.Language, null) }, colors = clearColors(), trailingContent = { Switch(ui.serverWeb, { ui.serverWeb = it }) },
                )
                ListItem(
                    headlineContent = { Text("API") }, supportingContent = { Text("For scripts and apps; see the docs in About") },
                    leadingContent = { Icon(Icons.Default.Api, null) }, colors = clearColors(), trailingContent = { Switch(ui.serverApi, { ui.serverApi = it }) },
                )
            }
        }
    }

    if (showQr && url != null) AlertDialog(
        onDismissRequest = { showQr = false },
        confirmButton = { TextButton(onClick = { showQr = false }) { Text("Close") } },
        title = { Text("Scan to print") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val link = ServerService.url(true) ?: url
                val img = remember(link) { qr(link).asImageBitmap() }
                Image(img, "QR code for $url", Modifier.size(240.dp), filterQuality = FilterQuality.None)
                Text(url, style = MaterialTheme.typography.bodyMedium)
                if (ui.serverAuth) Text("The code includes your access token: only show it to people you trust.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}
