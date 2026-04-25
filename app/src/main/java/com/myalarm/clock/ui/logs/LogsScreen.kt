package com.myalarm.clock.ui.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myalarm.clock.R
import com.myalarm.clock.util.LogEntry
import com.myalarm.clock.util.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onBack: () -> Unit,
    viewModel: LogsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    var clearConfirmOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = viewModel.getAllLogText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, context.getString(R.string.logs_share_chooser))
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.logs_share))
                    }
                    IconButton(onClick = { clearConfirmOpen = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                Text(
                    "—",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val reversed = entries.asReversed()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(reversed, key = { it.timestamp.toString() + it.tag + it.message.hashCode() }) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }

    if (clearConfirmOpen) {
        AlertDialog(
            onDismissRequest = { clearConfirmOpen = false },
            title = { Text(stringResource(R.string.logs_clear_title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    clearConfirmOpen = false
                }) {
                    Text(stringResource(R.string.logs_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmOpen = false }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val time = remember(entry.timestamp) {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            LevelChip(entry.level)
            Spacer(Modifier.width(8.dp))
            Text(
                entry.tag,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF1E88E5)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            entry.message,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (entry.throwable != null) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValuesNone
            ) {
                Text(
                    if (expanded) "▲ stack" else "▼ stack",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Text(
                    android.util.Log.getStackTraceString(entry.throwable),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LevelChip(level: LogLevel) {
    val (bg, label) = when (level) {
        LogLevel.DEBUG -> Color(0xFF9E9E9E) to "D"
        LogLevel.INFO -> Color(0xFF43A047) to "I"
        LogLevel.WARN -> Color(0xFFFB8C00) to "W"
        LogLevel.ERROR -> Color(0xFFE53935) to "E"
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

private val PaddingValuesNone = androidx.compose.foundation.layout.PaddingValues(0.dp)
