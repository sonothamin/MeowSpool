package dev.catprint.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.composed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.catprint.Conn
import dev.catprint.PState
import dev.catprint.PrinterStatus

/** What the user should see + do, derived from connection + printer status. */
data class Summary(val title: String, val detail: String?, val level: Level, val loading: Boolean = false)
enum class Level { OK, WARN, ERROR, INFO }

fun PrinterStatus.hint(): String? = when {
    outOfPaper -> "Load a paper roll, then it resumes automatically."
    coverOpen -> "Close the paper cover firmly."
    overheat -> "Let the printer cool down for a few minutes."
    lowPower -> "Battery is low — charge it soon."
    paused -> "Printer is paused."
    else -> null
}

fun summarize(s: PState): Summary {
    val st = s.status
    return when {
        s.conn == Conn.ERROR -> Summary("Can’t reach printer", s.error ?: "Check it is on and nearby.", Level.ERROR)
        s.conn == Conn.CONNECTING -> Summary("Connecting…", "Make sure the printer is on and in range.", Level.INFO, loading = true)
        s.conn == Conn.IDLE -> Summary("Not connected", null, Level.INFO)
        st == null -> Summary("Reading status…", null, Level.INFO, loading = true)
        st.blocking -> Summary(st.problems().first { it != "Low battery" }, st.hint(), Level.ERROR)
        s.printing -> Summary("Printing…", null, Level.INFO, loading = true)
        st.busy -> Summary("Busy", "Finishing the previous job.", Level.INFO, loading = true)
        st.lowPower || st.paused -> Summary(st.problems().first(), st.hint(), Level.WARN)
        else -> Summary("Ready to print", null, Level.OK)
    }
}

@Composable
fun StatusCard(name: String, sum: Summary, status: PrinterStatus?, showRetry: Boolean, onRetry: () -> Unit) {
    val (bg, fg) = when (sum.level) {
        Level.OK -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Level.WARN -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        Level.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Level.INFO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val container by animateColorAsState(bg, label = "statusBg")
    val icon = when (sum.level) {
        Level.OK -> Icons.Default.CheckCircle
        Level.WARN, Level.ERROR -> Icons.Default.Warning
        Level.INFO -> Icons.Default.Info
    }
    Card(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$name: ${sum.title}" },
        colors = CardDefaults.cardColors(containerColor = container, contentColor = fg),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sum.loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = fg)
                else Icon(icon, null, Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(sum.title, style = MaterialTheme.typography.titleLarge)
                    sum.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (status != null) StatusChips(status)
            if (showRetry) FilledTonalButton(onClick = onRetry) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Reconnect") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(status: PrinterStatus) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        status.chips().forEach { (label, problem) ->
            AssistChip(
                onClick = {}, enabled = true, label = { Text(label) },
                leadingIcon = { Icon(if (problem) Icons.Default.Warning else Icons.Default.Check, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (problem) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Transparent,
                    labelColor = if (problem) MaterialTheme.colorScheme.onError else LocalContentColor.current,
                    leadingIconContentColor = if (problem) MaterialTheme.colorScheme.onError else LocalContentColor.current,
                ),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

fun Modifier.verticalScrollCompat(): Modifier = composed { this.verticalScroll(rememberScrollState()) }
