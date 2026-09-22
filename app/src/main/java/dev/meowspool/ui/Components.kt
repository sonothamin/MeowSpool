package dev.meowspool.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.meowspool.Conn
import dev.meowspool.PState
import dev.meowspool.PrinterStatus

enum class Level { OK, WARN, ERROR, INFO }
data class Summary(val title: String, val detail: String?, val level: Level, val loading: Boolean = false)

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

data class StatusItem(val icon: ImageVector, val label: String, val problem: Boolean, val warn: Boolean = false)

fun statusItems(s: PState): List<StatusItem> {
    val conn = when (s.conn) {
        Conn.CONNECTED -> StatusItem(Icons.Default.Bluetooth, "Connected", false)
        Conn.CONNECTING -> StatusItem(Icons.Default.BluetoothSearching, "Connecting", false)
        Conn.ERROR -> StatusItem(Icons.Default.BluetoothDisabled, "Disconnected", true)
        Conn.IDLE -> StatusItem(Icons.Default.BluetoothDisabled, "Not connected", false)
    }
    val st = s.status ?: return listOf(conn)
    return listOf(
        conn,
        StatusItem(Icons.Default.Description, if (st.outOfPaper) "No paper" else "Paper OK", st.outOfPaper),
        StatusItem(if (st.coverOpen) Icons.Default.LockOpen else Icons.Default.Lock, if (st.coverOpen) "Cover open" else "Cover closed", st.coverOpen),
        StatusItem(Icons.Default.Thermostat, if (st.overheat) "Overheated" else "Temp OK", st.overheat),
        StatusItem(if (st.lowPower) Icons.Default.BatteryAlert else Icons.Default.BatteryFull, if (st.lowPower) "Low battery" else "Battery OK", st.lowPower, warn = true),
        when {
            st.paused -> StatusItem(Icons.Default.PauseCircle, "Paused", true, warn = true)
            st.busy || s.printing -> StatusItem(Icons.Default.HourglassTop, "Busy", false)
            else -> StatusItem(Icons.Default.CheckCircle, "Idle", false)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusChips(items: List<StatusItem>, tint: Color = MaterialTheme.colorScheme.onSurface) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { it ->
            val bad = it.problem
            val (bg, fg) = when {
                bad && it.warn -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
                bad -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
                else -> Color.Transparent to tint
            }
            AssistChip(
                onClick = {}, label = { Text(it.label) },
                leadingIcon = { Icon(it.icon, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = bg, labelColor = fg, leadingIconContentColor = fg),
                border = if (bad) null else BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
            )
        }
    }
}

@Composable
fun SectionHeader(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

/** Centred, max-width scrolling page (adapts to tablets / landscape). */
@Composable
fun Page(pad: PaddingValues, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** One UI settings rows put each leading icon in its own colour chip rather than a flat glyph. This wraps
 * any existing Material [ImageVector] with that treatment when [ui]'s style is One UI, and renders it
 * completely unchanged (a plain flat Icon) otherwise — so it's safe to drop into any ListItem/NavigationDrawerItem
 * across the app without touching the Material/Glass look. */
@Composable
fun OneUiChipIcon(icon: ImageVector, ui: UiState, modifier: Modifier = Modifier) {
    val style = UiStyle.fromPref(ui.uiStyle)
    if (style != UiStyle.ONE_UI) { Icon(icon, null, modifier); return }
    val cs = MaterialTheme.colorScheme
    val palette = listOf(
        cs.primaryContainer to cs.onPrimaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer, cs.errorContainer to cs.onErrorContainer,
    )
    val idx = (icon.hashCode() and 0x7fffffff) % palette.size
    val (bg, fg) = palette[idx]
    Box(modifier.size(32.dp).background(bg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
    }
}
