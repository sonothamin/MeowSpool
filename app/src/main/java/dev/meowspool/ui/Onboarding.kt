package dev.meowspool.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.meowspool.R

private const val LAST = 3

/** Friendly first-run flow: welcome → power on → pick printer → done. */
@Composable
fun OnboardingScreen(ui: UiState) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scanning by ui.scanning.collectAsState()
    val found by ui.found.collectAsState()
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Scaffold(snackbarHost = { SnackbarHost(ui.snack) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxHeight().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (step < LAST) TextButton(onClick = ui::finishOnboarding) { Text("Skip") }
                }
                AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(), label = "onboarding") { s ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        when (s) {
                            0 -> {
                                Hero { Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(96.dp), tint = cs.onPrimaryContainer) }
                                Title("Meet MeowSpool")
                                Body("Print from any app straight to your cat thermal printer over Bluetooth. Let’s connect yours — it takes about a minute.")
                            }
                            1 -> {
                                Hero { Icon(Icons.Default.PowerSettingsNew, null, Modifier.size(80.dp), tint = cs.onPrimaryContainer) }
                                Title("Switch your printer on")
                                Body("Press its power button until the light comes on, and keep it close to your phone. Make sure Bluetooth is on too.")
                            }
                            2 -> {
                                Hero {
                                    if (scanning) CircularProgressIndicator(Modifier.size(80.dp), strokeWidth = 6.dp, color = cs.onPrimaryContainer)
                                    else Icon(Icons.Default.BluetoothSearching, null, Modifier.size(80.dp), tint = cs.onPrimaryContainer)
                                }
                                Title(when { scanning && found.isEmpty() -> "Looking for your printer…"; found.isEmpty() -> "No printer found yet"; else -> "Tap your printer" })
                                if (found.isEmpty() && !scanning) Body("Check it’s on and not connected to another phone, then try again.")
                                found.entries.toList().forEach { (addr, name) ->
                                    Card(
                                        Modifier.fillMaxWidth().clickable { ui.select(Printer(addr, name)); step = 3 },
                                        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
                                    ) {
                                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Icon(Icons.Default.Print, null, Modifier.size(40.dp), tint = cs.primary)
                                            Column(Modifier.weight(1f)) {
                                                Text(name, style = MaterialTheme.typography.titleMedium)
                                                Text("Tap to connect", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                            }
                                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                                        }
                                    }
                                }
                            }
                            else -> {
                                Hero { Icon(Icons.Default.CheckCircle, null, Modifier.size(88.dp), tint = cs.onPrimaryContainer) }
                                Title("You’re all set!")
                                Body("${ui.selectedPrinter?.name ?: "Your printer"} is connected. Print a test page to see it purr.")
                                if (!ui.serviceOn) Card(colors = CardDefaults.cardColors(containerColor = cs.secondaryContainer)) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("One last step: turn on “MeowSpool” in your phone’s print settings so other apps can print here.", style = MaterialTheme.typography.bodyMedium)
                                        FilledTonalButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }) { Text("Open print settings") }
                                    }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dots(step)
                    Spacer(Modifier.height(4.dp))
                    when (step) {
                        0 -> BigButton("Get started") { step = 1 }
                        1 -> {
                            BigButton("Find my printer", Icons.Default.Search) { ui.requestScan(); step = 2 }
                            TextButton(onClick = { step = 0 }) { Text("Back") }
                        }
                        2 -> {
                            if (!scanning) BigButton("Scan again", Icons.Default.Refresh) { ui.requestScan() }
                            TextButton(onClick = { step = 1 }) { Text("Back") }
                        }
                        else -> {
                            OutlinedButton(onClick = ui::testPrint, enabled = ui.canTest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                                Icon(Icons.Default.ReceiptLong, null); Spacer(Modifier.width(8.dp)); Text(if (ui.testing) "Printing…" else "Print a test page")
                            }
                            BigButton("Done", onClick = ui::finishOnboarding)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(content: @Composable () -> Unit) =
    Box(Modifier.size(160.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { content() }

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }

@Composable
private fun Dots(step: Int) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    repeat(LAST + 1) { i ->
        Box(
            Modifier.height(8.dp).width(if (i == step) 24.dp else 8.dp)
                .background(if (i == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
    }
}
