package dev.meowspool.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.meowspool.R

private const val REPO = "https://github.com/sonothamin/MeowSpool"

@Composable
fun AboutScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
    val none = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
    val open = @Composable { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }
    Page(pad) {
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(96.dp).background(cs.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_meowspool), null, Modifier.size(60.dp), tint = cs.onPrimaryContainer)
                }
                Text("MeowSpool", style = MaterialTheme.typography.headlineMedium)
                if (version.isNotEmpty()) Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text("Print from any Android app to your Bluetooth cat thermal printer.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("Print service settings") }, supportingContent = { Text("Turn MeowSpool on or off for other apps") },
                    leadingContent = { Icon(Icons.Default.Print, null) }, trailingContent = open, colors = none,
                    modifier = Modifier.clickable { ctx.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Source code") }, supportingContent = { Text("github.com/sonothamin/MeowSpool") },
                    leadingContent = { Icon(Icons.Default.Code, null) }, trailingContent = open, colors = none,
                    modifier = Modifier.clickable { uri.openUri(REPO) },
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Report a problem") }, supportingContent = { Text("Tip: copy the debug log first") },
                    leadingContent = { Icon(Icons.Default.BugReport, null) }, trailingContent = open, colors = none,
                    modifier = Modifier.clickable { uri.openUri("$REPO/issues") },
                )
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerLow)) {
                ListItem(
                    headlineContent = { Text("Supported printers") },
                    supportingContent = { Text("GB01 / GB02 / GB03-style cat printers · 58 mm paper · 384 dots (48 mm) printable") },
                    leadingContent = { Icon(Icons.Default.Info, null) }, colors = none,
                )
            }
        }
    }
}
