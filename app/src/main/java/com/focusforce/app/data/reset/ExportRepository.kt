package com.focusforce.app.data.reset

import android.content.Context
import android.net.Uri
import com.focusforce.app.data.apps.AppInfoProvider
import com.focusforce.app.domain.export.FocusSessionRow
import com.focusforce.app.domain.export.UsageExport
import com.focusforce.app.domain.repo.FocusRepository
import com.focusforce.app.domain.repo.UsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Builds CSV text from the local database and writes it to a file the user chose. Nothing leaves the phone except into that file. */
@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usage: UsageRepository,
    private val focus: FocusRepository,
    private val appInfo: AppInfoProvider,
) {
    suspend fun usageCsv(): String = withContext(Dispatchers.Default) {
        val rows = usage.observeApps(LocalDate.of(2000, 1, 1), LocalDate.now()).first()
        UsageExport.usageCsv(rows) { appInfo.label(it) }
    }

    suspend fun sessionsCsv(): String = withContext(Dispatchers.Default) {
        val zone = ZoneId.systemDefault()
        val rows = focus.observeSessions(0L).first().map {
            val start = Instant.ofEpochMilli(it.startedAtMs).atZone(zone)
            val end = Instant.ofEpochMilli(it.endedAtMs).atZone(zone)
            FocusSessionRow(end.toLocalDate(), start.toLocalDateTime().withNano(0).toString(), end.toLocalDateTime().withNano(0).toString(), it.minutes, it.completed)
        }
        UsageExport.sessionsCsv(rows)
    }

    suspend fun write(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } != null
        }.getOrDefault(false)
    }
}
