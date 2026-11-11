package com.focusforce.app.domain.export

import com.focusforce.app.domain.usage.DayAppUsage
import java.time.LocalDate

/**
 * Minimal RFC 4180 CSV writer (CRLF line ends, quotes doubled).
 * Text cells that start with = + - @ or a control character are prefixed with an apostrophe so a spreadsheet can never run
 * them as a formula (CSV injection); genuine numbers are written as numbers and are not touched.
 */
object Csv {
    private val formulaStarts = setOf('=', '+', '-', '@', '\t', '\r')

    fun cell(value: Any?): String = when (value) {
        null -> ""
        is Int, is Long, is Short, is Byte -> value.toString()
        is Boolean -> value.toString()
        is Float, is Double -> value.toString()
        is LocalDate -> value.toString()
        else -> text(value.toString())
    }

    private fun text(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in formulaStarts) "'$raw" else raw
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun row(cells: List<Any?>): String = cells.joinToString(",") { cell(it) }

    fun document(header: List<String>, rows: List<List<Any?>>): String =
        (listOf(row(header)) + rows.map { row(it) }).joinToString("\r\n", postfix = "\r\n")
}

/** A finished or stopped focus block for export. */
data class FocusSessionRow(val date: LocalDate, val startIso: String, val endIso: String, val minutes: Int, val completed: Boolean)

object UsageExport {
    val USAGE_HEADER = listOf("date", "package", "app", "foreground_minutes", "opens")
    val SESSIONS_HEADER = listOf("date", "start", "end", "focus_minutes", "completed")

    /** One row per app per day, newest day first; minutes with one decimal. */
    fun usageCsv(rows: List<DayAppUsage>, label: (String) -> String): String = Csv.document(
        USAGE_HEADER,
        rows.sortedWith(compareByDescending<DayAppUsage> { it.date }.thenByDescending { it.foregroundMs }.thenBy { it.packageName })
            .map { listOf(it.date, it.packageName, label(it.packageName), Math.round(it.foregroundMs / 6_000.0) / 10.0, it.launches) },
    )

    fun sessionsCsv(rows: List<FocusSessionRow>): String = Csv.document(
        SESSIONS_HEADER,
        rows.sortedByDescending { it.startIso }.map { listOf(it.date, it.startIso, it.endIso, it.minutes, it.completed) },
    )
}
