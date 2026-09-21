package dev.meowspool.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.meowspool.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KProperty

data class Printer(val addr: String, val name: String)

/** Compose state that writes through to [save] on every change. */
class PrefState<T>(init: T, private val save: (T) -> Unit) {
    private var s by mutableStateOf(init)
    operator fun getValue(r: Any?, p: KProperty<*>): T = s
    operator fun setValue(r: Any?, p: KProperty<*>, v: T) { s = v; save(v) }
}

/** Everything the screens read or change; survives recomposition, owned by the activity. */
class UiState(
    private val scanner: Scanner,
    val snack: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    val found = scanner.found
    val scanning = scanner.scanning

    var saved by mutableStateOf(load()); private set
    var selected by mutableStateOf(Prefs.selected); private set
    var testing by mutableStateOf(false); private set
    /** Whether the test-print preview/confirmation screen should be showing, from anywhere in the app. */
    var testConfirm by mutableStateOf(false); private set
    var serviceOn by mutableStateOf(true)
    var crash by mutableStateOf(Prefs.lastCrash)
    /** First-run "connect your first printer" flow; skipped for anyone who already has a printer. */
    var onboarding by mutableStateOf(!Prefs.onboarded && Prefs.printers().isEmpty()); private set
    fun finishOnboarding() { Prefs.onboarded = true; onboarding = false }

    // Print settings (persisted)
    var darkness by PrefState(Prefs.darkness) { Prefs.darkness = it }
    var dither by PrefState(Dither.fromPref()) { Prefs.dither = it.name }
    var feedMm by PrefState(Prefs.feedMm) { Prefs.feedMm = it }
    var feedStepMm by PrefState(Prefs.feedStepMm) { Prefs.feedStepMm = it }
    var retractStepMm by PrefState(Prefs.retractStepMm) { Prefs.retractStepMm = it }
    var lineBefore by PrefState(Prefs.lineBefore) { Prefs.lineBefore = it }
    var lineAfter by PrefState(Prefs.lineAfter) { Prefs.lineAfter = it }
    var lineDashed by PrefState(Prefs.lineDashed) { Prefs.lineDashed = it }
    var marginSideMm by PrefState(Prefs.marginSideMm) { Prefs.marginSideMm = it }
    var marginVertMm by PrefState(Prefs.marginVertMm) { Prefs.marginVertMm = it }
    var themeMode by PrefState(Prefs.theme) { Prefs.theme = it }
    var dynamicColor by PrefState(Prefs.dynamicColor) { Prefs.dynamicColor = it }

    var paperId by mutableStateOf(Paper.selected().id); private set
    var papers by mutableStateOf(Paper.all()); private set

    var serviceCheck: () -> Boolean = { true }
    var requestScan: () -> Unit = {}

    private var latched: String? = null
    private fun load() = Prefs.printers().map { Printer(it.first, it.second) }
    private fun say(msg: String, action: String? = null, onAction: () -> Unit = {}) = scope.launch {
        if (snack.showSnackbar(msg, action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onAction()
    }

    val selectedPrinter get() = saved.firstOrNull { it.addr == selected }
    val paper get() = papers.firstOrNull { it.id == paperId } ?: Paper.builtIns[0]
    val finishSummary get() = buildList {
        add("Feed $feedMm mm")
        if (lineBefore && lineAfter) add("line before & after") else if (lineBefore) add("line before") else if (lineAfter) add("line after")
        add(dither.label.lowercase())
    }.joinToString(" · ")

    fun notify(msg: String) { say(msg) }

    fun onStart() { Prefs.selected?.let { latch(it) } }
    fun onStop() { scanner.stop(); latched?.let { PrinterManager.release(it) }; latched = null }
    fun refreshService() { serviceOn = serviceCheck() }
    fun dismissCrash() { Prefs.lastCrash = null; crash = null }

    private fun latch(addr: String?) {
        selected = addr; Prefs.selected = addr
        if (latched != addr) { latched?.let { PrinterManager.release(it) }; latched = addr?.also { PrinterManager.latch(it) } }
    }

    fun select(p: Printer) { Prefs.add(p.addr, p.name); saved = load(); latch(p.addr) }
    fun remove(p: Printer) {
        Prefs.remove(p.addr); saved = load(); latch(Prefs.selected)
        PrinterManager.forget(p.addr) // else it hangs ghost-connected until the grace period ends
        say("Forgot ${p.name}", "Undo") { Prefs.add(p.addr, p.name); saved = load(); if (selected == null) latch(p.addr) }
    }
    fun scan() { if (!scanner.start()) say("Turn Bluetooth on to scan") }
    fun permissionDenied() { say("Bluetooth permission is needed to find printers") }

    fun selectPaper(p: PaperPreset) { Prefs.paperId = p.id; paperId = p.id }
    fun addPaper(name: String, mm: Int) {
        val id = "c${System.currentTimeMillis()}"
        Prefs.addPaper(id, name, mm); papers = Paper.all(); selectPaper(Paper.all().first { it.id == id })
    }
    fun removePaper(p: PaperPreset) {
        Prefs.removePaper(p.id); papers = Paper.all(); paperId = Prefs.paperId
        say("Removed ${p.name}", "Undo") { Prefs.addPaper(p.id, p.name, p.lengthMm ?: 100); papers = Paper.all() }
    }

    fun resetPrintSettings() { darkness = 60; dither = Dither.FLOYD; feedMm = 12; lineBefore = false; lineAfter = false; lineDashed = true; marginSideMm = 0; marginVertMm = 0; feedStepMm = 20; retractStepMm = 20 }

    val canTest get() = selectedPrinter != null && !testing

    /** Step 1: show the preview/confirmation screen instead of printing straight away, to avoid wasting paper on an accidental tap. */
    fun requestTestPrint() { if (selectedPrinter != null) testConfirm = true }
    fun cancelTestPrint() { if (!testing) testConfirm = false }
    /** Step 2: the user confirmed on the preview screen, so actually send it. */
    fun confirmTestPrint() {
        val p = selectedPrinter ?: return
        testing = true
        scope.launch(Dispatchers.IO) {
            val err = try { PrintEngine.printTestPage(p.addr, p.name); null } catch (e: Throwable) { Dbg.e("UI", "test print failed", e); e.message ?: "Print failed" }
            testing = false; testConfirm = false
            if (err != null) say("Test print failed: $err")
        }
    }

    fun feed() {
        val p = selectedPrinter ?: return
        scope.launch(Dispatchers.IO) { try { PrintEngine.feed(p.addr, feedStepMm) } catch (e: Throwable) { Dbg.e("UI", "feed failed", e); say("Feed failed: ${e.message ?: "error"}") } }
    }
    fun retract() {
        val p = selectedPrinter ?: return
        scope.launch(Dispatchers.IO) { try { PrintEngine.retract(p.addr, retractStepMm) } catch (e: Throwable) { Dbg.e("UI", "retract failed", e); say("Retract failed: ${e.message ?: "error"}") } }
    }
}
