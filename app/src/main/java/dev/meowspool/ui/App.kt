package dev.meowspool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.meowspool.R
import kotlinx.coroutines.launch

enum class Dest(val title: String, val icon: ImageVector) {
    Home("MeowSpool", Icons.Default.Home),
    Direct("Print a file", Icons.Default.UploadFile),
    Devices("Devices", Icons.Default.Bluetooth),
    History("History", Icons.Default.History),
    Paper("Paper", Icons.Default.Description),
    Print("Print settings", Icons.Default.Tune),
    Look("Appearance", Icons.Default.Palette),
    Log("Debug log", Icons.Default.BugReport),
    About("About", Icons.Default.Info),
}

/** Drawer layout: (section heading, destinations). About is pinned separately at the bottom. */
private val sections = listOf(
    null to listOf(Dest.Home, Dest.Direct, Dest.Devices, Dest.History),
    "Printing" to listOf(Dest.Paper, Dest.Print),
    "App" to listOf(Dest.Look, Dest.Log),
)

@Composable
fun MeowSpoolRoot(ui: UiState) {
    MeowSpoolTheme(ui.themeMode, ui.dynamicColor) {
        Box(Modifier.fillMaxSize()) {
            if (ui.onboarding) OnboardingScreen(ui) else MainShell(ui)
            // Overlaid on top of whichever screen triggered it, so "test print" always confirms first.
            if (ui.testConfirm) TestPrintConfirmScreen(ui)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(ui: UiState) {
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
            drawerContent = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Sheet(dest, ::go) } },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                if (dest == Dest.Home) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(28.dp)); Text(dest.title)
                                } else Text(dest.title)
                            },
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
                        Dest.Direct -> PrintFileScreen(ui, pad, ::go)
                        Dest.Devices -> DevicesScreen(ui, pad)
                        Dest.History -> HistoryScreen(pad)
                        Dest.Paper -> PaperScreen(ui, pad)
                        Dest.Print -> PrintSettingsScreen(ui, pad)
                        Dest.Look -> AppearanceScreen(ui, pad)
                        Dest.Log -> LogScreen(pad)
                        Dest.About -> AboutScreen(pad)
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(current: Dest, go: (Dest) -> Unit) {
    val cs = MaterialTheme.colorScheme
    @Composable fun item(d: Dest) = NavigationDrawerItem(
        label = { Text(if (d == Dest.Home) "Home" else d.title) }, icon = { Icon(d.icon, null) },
        selected = d == current, onClick = { go(d) }, modifier = Modifier.padding(horizontal = 12.dp),
    )
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(44.dp).background(cs.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(28.dp), tint = cs.onPrimaryContainer)
                }
                Column {
                    Text("MeowSpool", style = MaterialTheme.typography.titleLarge)
                    Text("Cat printer print service", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            sections.forEach { (heading, items) ->
                if (heading == null) HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                else Text(heading, style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp))
                items.forEach { item(it) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
            item(Dest.About)
        }
    }
}
