package dev.meowspool.ui

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import dev.meowspool.Conn
import dev.meowspool.PState
import dev.meowspool.Prefs
import dev.meowspool.PrinterManager
import dev.meowspool.R

/** Best-effort model match from the BLE advertised name, for a friendly device picture on Home. */
private fun avatarFor(name: String): Int? {
    val n = name.uppercase()
    return when {
        n.contains("X5") -> R.drawable.printer_mx05
        n.contains("MX") -> R.drawable.printer_mx10
        n.contains("GB") -> R.drawable.printer_gb01
        else -> null
    }
}

/** Fades the printer's picture in from black to full brightness with a bouncy scale-up on launch, and
 * crossfades out/in when the model changes (e.g. switching printers) instead of jump-cutting. */
@Composable
private fun AvatarReveal(avatar: Int) {
    AnimatedContent(
        targetState = avatar,
        transitionSpec = {
            (fadeIn(tween(500, easing = FastOutSlowInEasing)) + scaleIn(initialScale = 0.7f, animationSpec = tween(500, easing = FastOutSlowInEasing)))
                .togetherWith(fadeOut(tween(250)) + scaleOut(targetScale = 0.8f, animationSpec = tween(250)))
        },
        label = "avatarReveal",
    ) { a ->
        val reveal = remember(a) { Animatable(0f) }
        LaunchedEffect(a) { reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
        val v = reveal.value
        val matrix = ColorMatrix().apply { setToScale(v, v, v, 1f) }
        Image(
            painterResource(a), null,
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer { alpha = v; scaleX = 0.8f + 0.2f * v; scaleY = 0.8f + 0.2f * v },
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(matrix),
        )
    }
}

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
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
        item {
            Card(Modifier.fillMaxWidth().clickable { go(Dest.Direct) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("Print a photo or PDF") }, supportingContent = { Text("Preview, adjust and print straight from here") },
                    leadingContent = { OneUiChipIcon(Icons.Default.UploadFile, ui) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }, colors = clear(),
                )
            }
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PrinterHero(p: Printer, st: PState, ui: UiState, onSwitch: () -> Unit) {
    val sum = summarize(st)
    val cs = MaterialTheme.colorScheme
    val danger = sum.level == Level.ERROR
    val problems = statusItems(st).filter { it.problem }
    val ready = st.conn == Conn.CONNECTED && st.status?.blocking != true && !st.printing && !ui.testing
    val avatar = if (ui.deviceAvatars) avatarFor(p.name) else null

    // MD3 spacing uses an 8dp grid; 8/16/24 throughout instead of ad-hoc values.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (avatar != null && !sum.loading) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            AvatarReveal(avatar)
        }

        // A real error is the one state worth a distinct container; otherwise this is just an
        // elevated tonal surface, one step up from the page background — the correct MD3 way to
        // show "this card matters more," rather than hand-mixing custom colours.
        val (containerColor, contentColor) = if (danger) cs.errorContainer to cs.onErrorContainer else cs.surfaceContainerHigh to cs.onSurface
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (avatar == null) Box(Modifier.size(48.dp).background(contentColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        if (sum.loading) LoadingIndicator(Modifier.size(24.dp), color = contentColor)
                        else Icon(if (danger) Icons.Default.Warning else Icons.Default.Print, null, Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.labelLarge)
                        Text(sum.title, style = MaterialTheme.typography.headlineSmall)
                        sum.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (problems.isNotEmpty()) StatusChips(problems, contentColor)
                // Standard M3 button roles — filled for the primary action, tonal for a secondary
                // one, outlined for a lower-emphasis action — rather than custom-tinted variants.
                // That keeps every button correctly paired (container + its own "on" colour) even
                // when this whole card is sitting on the error-container tone.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = ui::requestTestPrint, enabled = ready) {
                        if (ui.testing) LoadingIndicator(Modifier.size(18.dp)) else Icon(Icons.Default.ReceiptLong, null)
                        Spacer(Modifier.width(8.dp)); Text(if (ui.testing) "Printing…" else "Test page")
                    }
                    if (st.conn == Conn.ERROR) FilledTonalButton(onClick = { PrinterManager.reconnect(p.addr) }) {
                        Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reconnect")
                    }
                    OutlinedButton(onClick = onSwitch) {
                        Icon(Icons.Default.SwapHoriz, null); Spacer(Modifier.width(8.dp)); Text("Switch printer")
                    }
                    if (st.conn == Conn.CONNECTED || st.conn == Conn.CONNECTING) IconButton(onClick = ui::disconnectPrinter) {
                        Icon(Icons.Default.LinkOff, "Disconnect")
                    } else if (st.conn == Conn.IDLE) IconButton(onClick = ui::connectPrinter) {
                        Icon(Icons.Default.Link, "Connect")
                    }
                }
            }
        }

        // Feed/retract: its own tonal surface, one step below the status card — a distinct but
        // lower-emphasis grouping, using the same surface-container ladder rather than a flat colour.
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ui::feed, enabled = ready, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ArrowUpward, null); Spacer(Modifier.width(8.dp)); Text("Feed")
                }
                OutlinedButton(onClick = ui::retract, enabled = ready, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ArrowDownward, null); Spacer(Modifier.width(8.dp)); Text("Retract")
                }
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
            leadingContent = { OneUiChipIcon(Icons.Default.Description, ui) }, trailingContent = chevron, colors = clear(),
            modifier = Modifier.clickable { go(Dest.Paper) },
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Print settings") }, supportingContent = { Text("Darkness ${ui.darkness}% · ${ui.finishSummary}") },
            leadingContent = { OneUiChipIcon(Icons.Default.Tune, ui) }, trailingContent = chevron, colors = clear(),
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
