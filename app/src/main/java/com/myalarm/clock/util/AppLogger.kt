package com.myalarm.clock.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null
) {
    fun format(): String {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        val levelChar = when (level) {
            LogLevel.DEBUG -> "D"
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        val base = "$time $levelChar/$tag: $message"
        return if (throwable != null) "$base\n${Log.getStackTraceString(throwable)}" else base
    }
}

@Singleton
class AppLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LOGCAT_TAG = "MyAlarm"
        private const val MAX_BUFFER = 500
        private const val MAX_DAYS = 7
    }

    private val buffer = ArrayDeque<LogEntry>(MAX_BUFFER)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    init { ioScope.launch { cleanOldLogs() } }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    fun w(tag: String, message: String, t: Throwable? = null) = log(LogLevel.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, message, t)

    private fun log(level: LogLevel, tag: String, message: String, t: Throwable?) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message, t)
        val fullTag = "$LOGCAT_TAG/$tag"
        when (level) {
            LogLevel.DEBUG -> Log.d(fullTag, message, t)
            LogLevel.INFO -> Log.i(fullTag, message, t)
            LogLevel.WARN -> Log.w(fullTag, message, t)
            LogLevel.ERROR -> Log.e(fullTag, message, t)
        }
        synchronized(buffer) {
            if (buffer.size >= MAX_BUFFER) buffer.removeFirst()
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
        ioScope.launch { writeToFile(entry) }
    }

    private fun writeToFile(entry: LogEntry) {
        try {
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val today = dateFormat.format(Date(entry.timestamp))
            File(dir, "log_$today.txt").appendText(entry.format() + "\n")
        } catch (_: Exception) {
        }
    }

    private fun cleanOldLogs() {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) return
            val cutoff = System.currentTimeMillis() - MAX_DAYS * 86_400_000L
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        } catch (_: Exception) {
        }
    }

    fun getRecentEntries(limit: Int): List<LogEntry> = synchronized(buffer) {
        if (buffer.size <= limit) buffer.toList() else buffer.toList().takeLast(limit)
    }

    fun getAllLogText(): String {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) return ""
        return dir.listFiles()
            ?.sortedBy { it.name }
            ?.joinToString("\n\n") { "=== ${it.name} ===\n${it.readText()}" }
            ?: ""
    }

    fun clearAll() {
        synchronized(buffer) { buffer.clear() }
        _entries.value = emptyList()
        ioScope.launch {
            File(context.filesDir, "logs").listFiles()?.forEach { it.delete() }
        }
    }
}
