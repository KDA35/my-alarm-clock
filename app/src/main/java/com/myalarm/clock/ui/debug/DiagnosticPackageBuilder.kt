package com.myalarm.clock.ui.debug

import android.content.Context
import com.myalarm.clock.BuildConfig
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticPackageBuilder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
    private val repository: AlarmRepository
) {
    suspend fun build(): File = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outFile = File(context.cacheDir, "myalarm-diagnostic-$timestamp.zip")
        outFile.delete()

        ZipOutputStream(outFile.outputStream().buffered()).use { zos ->
            writeEntry(zos, "debug_info.txt", buildDebugInfo())
            writeEntry(zos, "logs_recent.txt", buildRecentLogs())
            writeEntry(zos, "logs_files.txt", buildLogFiles())
            writeEntry(zos, "alarms_dump.txt", buildAlarmsDump())
            writeEntry(zos, "build_info.txt", buildBuildInfo(timestamp))
        }
        logger.i("Diagnostic", "Built diagnostic package at ${outFile.absolutePath} (${outFile.length()} bytes)")
        outFile
    }

    private fun writeEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private suspend fun buildDebugInfo(): String {
        val alarms = repository.observeAll().first()
        val groups = repository.observeGroups().first()
        return DebugInfoBuilder.renderFullText(context, alarms, groups, anonymize = true)
    }

    private fun buildRecentLogs(): String {
        val recent = logger.getRecentEntries(200)
        if (recent.isEmpty()) return "(no in-memory log entries)"
        return recent.joinToString("\n") { it.format() }
    }

    private fun buildLogFiles(): String {
        val text = logger.getAllLogText()
        return text.ifBlank { "(no log files yet)" }
    }

    private suspend fun buildAlarmsDump(): String {
        val alarms = repository.observeAll().first()
        val groups = repository.observeGroups().first()
        val sb = StringBuilder()
        sb.appendLine("=== Groups ===")
        if (groups.isEmpty()) sb.appendLine("(none)")
        groups.forEach { g ->
            sb.appendLine("ID ${g.id} · <group ${g.name.length} chars> · enabled=${g.enabled}")
        }
        sb.appendLine()
        sb.appendLine("=== Alarms ===")
        sb.appendLine(DebugInfoBuilder.alarmsDescription(alarms, groups, anonymize = true))
        return sb.toString()
    }

    private fun buildBuildInfo(timestamp: String): String =
        """
        versionName: ${BuildConfig.VERSION_NAME}
        versionCode: ${BuildConfig.VERSION_CODE}
        applicationId: ${BuildConfig.APPLICATION_ID}
        buildType: ${BuildConfig.BUILD_TYPE}
        diagnostic generated at: $timestamp
        """.trimIndent()
}
