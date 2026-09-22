package dev.meowspool.ui

import androidx.activity.compose.BackHandler
import dev.meowspool.Dbg
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.meowspool.History
import dev.meowspool.R
import kotlinx.coroutines.launch

enum class Dest(val title: String, val icon: ImageVector) {
    Home("MeowSpool", Icons.Default.Home),
    Direct("Print a file", Icons.Default.UploadFile),
    Devices("Devices", Icons.Default.Bluetooth),
    History("History", Icons.Default.History),
    Paper("Paper", Icons.Default.Description),
    Print("Print settings", Icons.Default.Tune),
    Server("Print server", Icons.Default.Dns),
    Look("Appearance", Icons.Default.Palette),
    Log("Debug log", Icons.Default.BugReport),
    About("About", Icons.Default.Info),
}

/** Drawer layout: (section heading, destinations). About is pinned separately at the bottom. */
private val sections = listOf(
    null to listOf(Dest.Home, Dest.Direct, Dest.Devices, Dest.History),
    "Printing" to listOf(Dest.Paper, Dest.Print, Dest.Server),
    "App" to listOf(Dest.Look, Dest.Log),
)

@Composable
fun MeowSpoolRoot(ui: UiState) {
    val style = UiStyle.fromPref(ui.uiStyle)
    val dark = when (ui.themeMode) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    MeowSpoolTheme(ui.themeMode, ui.dynamicColor, ui.uiFont, ui.amoled, style) {
        Box(Modifier.fillMaxSize().glassBackdrop(style, dark)) {
            if (ui.onboarding) OnboardingScreen(ui) else MainShell(ui, style)
            // Overlaid on top of whichever screen triggered it, so "test print" always confirms first.
            if (ui.testConfirm) TestPrintConfirmScreen(ui)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(ui: UiState, style: UiStyle) {
    var dest by rememberSaveable { mutableStateOf(Dest.Home) }
    var detail by rememberSaveable { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(dest != Dest.Home && drawer.isClosed) { dest = Dest.Home }
    BackHandler(detail != null && drawer.isClosed) { detail = null }
    fun go(d: Dest) { dest = d; detail = null; scope.launch { drawer.close() } }
    LaunchedEffect(ui.incomingShare) { Dbg.d("Share", "MainShell sees incomingShare=${ui.incomingShare}"); if (ui.incomingShare != null) go(Dest.Direct) }

    // Drawer slides in from the right: mirror the layout direction around it, keep content LTR.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Sheet(dest, ::go, style) } },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    containerColor = if (style == UiStyle.GLASS) Color.Transparent else MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                val titleStyle = if (style == UiStyle.ONE_UI) MaterialTheme.typography.headlineSmall else LocalTextStyle.current
                                CompositionLocalProvider(LocalTextStyle provides titleStyle) {
                                    if (dest == Dest.Home) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(28.dp)); Text(dest.title)
                                    } else Text(if (dest == Dest.History && detail != null) "Print details" else dest.title)
                                }
                            },
                            navigationIcon = { if (dest != Dest.Home) IconButton(onClick = { if (detail != null) detail = null else dest = Dest.Home }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                            actions = { IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                            colors = if (style == UiStyle.GLASS) TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent) else TopAppBarDefaults.topAppBarColors(),
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
                    // M3 shared-axis transition: incoming content fades/slides in with the emphasized-decelerate
                    // curve, outgoing content fades/slides out with emphasized-accelerate (per MD3 motion spec).
                    AnimatedContent(
                        targetState = dest to detail,
                        label = "destination",
                        transitionSpec = {
                            (fadeIn(tween(MotionTokens.DurationEnter, easing = MotionTokens.EmphasizedDecelerate)) +
                                slideInHorizontally(tween(MotionTokens.DurationEnter, easing = MotionTokens.EmphasizedDecelerate)) { it / 12 })
                                .togetherWith(fadeOut(tween(MotionTokens.DurationExit, easing = MotionTokens.EmphasizedAccelerate)))
                        },
                    ) { (curDest, curDetail) ->
                        when (curDest) {
                            Dest.Home -> HomeScreen(ui, pad, ::go)
                            Dest.Direct -> PrintFileScreen(ui, pad, ::go)
                            Dest.Devices -> DevicesScreen(ui, pad)
                            Dest.History -> {
                                val entries by History.entries.collectAsState()
                                val open = entries.firstOrNull { it.id == curDetail }
                                if (open != null) HistoryDetailScreen(ui, pad, open) { detail = null } else HistoryScreen(pad) { detail = it.id }
                            }
                            Dest.Paper -> PaperScreen(ui, pad)
                            Dest.Print -> PrintSettingsScreen(ui, pad)
                            Dest.Server -> ServerScreen(ui, pad)
                            Dest.Look -> AppearanceScreen(ui, pad)
                            Dest.Log -> LogScreen(pad)
                            Dest.About -> AboutScreen(pad)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sheet(current: Dest, go: (Dest) -> Unit, style: UiStyle) {
    val cs = MaterialTheme.colorScheme
    val oneUi = style == UiStyle.ONE_UI
    // One UI settings-style leading icons: each sits in its own colour chip instead of a flat glyph,
    // cycling through the palette so the list reads as more than one shade of grey.
    val chipColors = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.errorContainer to cs.onErrorContainer)
    @Composable fun item(d: Dest, index: Int) = NavigationDrawerItem(
        label = { Text(if (d == Dest.Home) "Home" else d.title) },
        icon = {
            if (oneUi) {
                val (bg, fg) = chipColors[index % chipColors.size]
                Box(Modifier.size(32.dp).background(bg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    DestIcon(d, style, Modifier.size(19.dp), tint = fg)
                }
            } else Icon(d.icon, null)
        },
        selected = d == current, onClick = { go(d) }, modifier = Modifier.padding(horizontal = 12.dp),
        // MD3 spec: nav drawer item active indicator uses the "full" (pill) shape; One UI keeps its own large-rounded look.
        shape = if (oneUi) RoundedCornerShape(20.dp) else CircleShape,
        colors = if (oneUi) NavigationDrawerItemDefaults.colors(
            selectedContainerColor = cs.primary.copy(alpha = 0.14f), unselectedContainerColor = Color.Transparent,
            selectedTextColor = cs.primary, unselectedTextColor = cs.onSurface,
            selectedIconColor = cs.onSurface, unselectedIconColor = cs.onSurface,
        ) else NavigationDrawerItemDefaults.colors(),
    )
    // MD3 spec: modal navigation drawer uses the "large" (16dp) end-corner shape token; One UI keeps its 26dp signature.
    val drawerShape = if (oneUi) RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp)
        else MaterialTheme.shapes.large.copy(topStart = CornerSize(0.dp), bottomStart = CornerSize(0.dp))
    ModalDrawerSheet(drawerShape = drawerShape) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 28.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // MD3 shape scale: 44dp avatar-style containers map to the "medium" (12dp) corner token; One UI keeps its own 14dp.
                Box(Modifier.size(44.dp).background(cs.primaryContainer, if (oneUi) RoundedCornerShape(14.dp) else MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(28.dp), tint = cs.onPrimaryContainer)
                }
                Column {
                    Text("MeowSpool", style = MaterialTheme.typography.titleLarge)
                    Text("Cat printer print service", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            var idx = 0
            sections.forEach { (heading, items) ->
                if (heading == null) HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                else Text(heading, style = MaterialTheme.typography.titleSmall, color = cs.primary, modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp))
                items.forEach { item(it, idx); idx++ }
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
            item(Dest.About, idx)
        }
    }
}

/** Renders a real OneUI icon when one resolved for [style], else the Material vector already used elsewhere. */
@Composable
private fun DestIcon(d: Dest, style: UiStyle, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val resId = d.resolvedIconRes(style)
    if (resId != null) Icon(painterResource(resId), null, modifier, tint = tint)
    else Icon(d.icon, null, modifier, tint = tint)
}
