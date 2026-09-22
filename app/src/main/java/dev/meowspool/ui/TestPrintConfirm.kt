package dev.meowspool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.meowspool.TestPage

/**
 * Shown on top of everything else whenever a test print is requested, anywhere in the app.
 * Requires an explicit confirm tap so an accidental "Test page" press doesn't waste paper.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TestPrintConfirmScreen(ui: UiState) {
    BackHandler(onBack = ui::cancelTestPrint)
    val p = ui.selectedPrinter
    val preview = remember(p?.addr, p?.name) { p?.let { TestPage.render(it.name) } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm test page") },
                navigationIcon = { IconButton(onClick = ui::cancelTestPrint) { Icon(Icons.Default.Close, "Cancel") } },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "This is exactly what will print on ${p?.name ?: "your printer"}. Make sure paper is loaded, then confirm.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp).background(Color.White), contentAlignment = Alignment.Center) {
                        preview?.let { Image(it.asImageBitmap(), "Test page preview", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = ui::cancelTestPrint, enabled = !ui.testing, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = ui::confirmTestPrint, enabled = !ui.testing && p != null, modifier = Modifier.weight(1f)) {
                        if (ui.testing) LoadingIndicator(Modifier.size(18.dp)) else Icon(Icons.Default.Print, null)
                        Spacer(Modifier.width(8.dp)); Text(if (ui.testing) "Printing…" else "Print it")
                    }
                }
            }
        }
    }
}
