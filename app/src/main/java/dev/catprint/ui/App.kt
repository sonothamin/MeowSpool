package dev.catprint.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.catprint.PState
import dev.catprint.PrinterManager
import kotlinx.coroutines.launch

enum class Dest(val title: String, val icon: ImageVector) {
    Home("MeowSpool", Icons.Default.Home),
    Devices("Devices", Icons.Default.Bluetooth),
    Paper("Paper", Icons.Default.Description),
    Print("Print settings", Icons.Default.Tune),
    Log("Debug log", Icons.Default.BugReport),
    Look("Appearance", Icons.Default.Palette),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeowApp(ui: UiState) {
    MeowTheme(ui.themeMode, ui.dynamicColor) {
        var dest by rememberSaveable { mutableStateOf(Dest.Home) }
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        BackHandler(dest != Dest.Home && drawer.isClosed) { dest = Dest.Home }
        fun go(d: Dest) { dest = d; scope.launch { drawer.close() } }

        // Drawer slides in from the right: mirror the layout direction around it, keep content LTR.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Sheet(ui, dest, ::go) } },
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(dest.title) },
                                navigationIcon = { if (dest != Dest.Home) IconButton(onClick = { dest = Dest.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                                actions = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                            )
                        },
                        snackbarHost = { SnackbarHost(ui.snack) },
                        floatingActionButton = {
                            if (dest == Dest.Devices) {
                                val scanning by ui.scanning.collectAsState()
                                ExtendedFloatingActionButton(
                                    onClick = { if (!scanning) ui.requestScan() },
                                    icon = { if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Search, null) },
                                    text = { Text(if (scanning) "Scanning…" else "Scan") },
                                )
                            }
                        },
                    ) { pad ->
                        when (dest) {
                            Dest.Home -> HomeScreen(ui, pad, ::go)
                            Dest.Devices -> DevicesScreen(ui, pad)
                            Dest.Paper -> PaperScreen(ui, pad)
                            Dest.Print -> PrintSettingsScreen(ui, pad)
                            Dest.Log -> LogScreen(pad)
                            Dest.Look -> AppearanceScreen(ui, pad)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(ui: UiState, current: Dest, go: (Dest) -> Unit) {
    val states by PrinterManager.states.collectAsState()
    ModalDrawerSheet {
        Column(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary)
                Text("MeowSpool", style = MaterialTheme.typography.titleLarge)
            }
            val p = ui.selectedPrinter
            Text(
                if (p == null) "No printer selected" else "${p.name} · ${summarize(states[p.addr] ?: PState()).title}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(bottom = 8.dp))
        Dest.values().forEach { d ->
            NavigationDrawerItem(
                label = { Text(if (d == Dest.Home) "Home" else d.title) }, icon = { Icon(d.icon, null) },
                selected = d == current, onClick = { go(d) }, modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
