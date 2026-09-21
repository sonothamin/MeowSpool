package dev.meowspool

/** State byte from the 0xA3 (get device state) reply. Bits per opuu/cat-printer. */
data class PrinterStatus(val raw: Int) {
    val outOfPaper get() = (raw and 0x01) != 0
    val coverOpen get() = (raw and 0x02) != 0
    val overheat get() = (raw and 0x04) != 0
    val lowPower get() = (raw and 0x08) != 0
    val paused get() = (raw and 0x10) != 0
    val busy get() = (raw and 0x80) != 0
    val blocking get() = outOfPaper || coverOpen || overheat

    /** Human-readable problems (empty = all good). */
    fun problems(): List<String> = buildList {
        if (outOfPaper) add("Out of paper")
        if (coverOpen) add("Cover open")
        if (overheat) add("Overheated")
        if (lowPower) add("Low battery")
        if (paused) add("Paused")
    }

    /** (label, isProblem) chips for the UI. */
    fun chips(): List<Pair<String, Boolean>> = listOf(
        (if (outOfPaper) "Out of paper" else "Paper OK") to outOfPaper,
        (if (coverOpen) "Cover open" else "Cover closed") to coverOpen,
        (if (overheat) "Overheated" else "Temp OK") to overheat,
        (if (lowPower) "Low battery" else "Power OK") to lowPower,
        (if (paused) "Paused" else "Ready") to paused,
        (if (busy) "Busy" else "Idle") to false,
    )
}
