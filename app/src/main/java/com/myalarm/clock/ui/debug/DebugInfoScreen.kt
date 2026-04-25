package com.myalarm.clock.ui.debug

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myalarm.clock.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugInfoScreen(
    onBack: () -> Unit,
    viewModel: DebugInfoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val data by viewModel.data.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = DebugInfoBuilder.renderFullText(
                            context, data.alarms, data.groups, anonymize = false
                        )
                        copyToClipboard(context, text)
                        scope.launch {
                            snackbar.showSnackbar(context.getString(R.string.debug_info_copied))
                        }
                    }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.debug_info_copy)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (DebugInfoBuilder.anyTestFlagActive()) {
                item { TestModeBanner() }
            }
            item { SectionTitle(stringResource(R.string.debug_info_section_device)) }
            item { KeyValueBlock(DebugInfoBuilder.deviceInfo(context)) }
            item { HorizontalDivider() }

            item { SectionTitle(stringResource(R.string.debug_info_section_permissions)) }
            items(DebugInfoBuilder.checkPermissions(context)) { perm ->
                PermissionRow(perm, context)
            }
            item { HorizontalDivider() }

            item { SectionTitle(stringResource(R.string.debug_info_section_channel)) }
            item {
                val ch = DebugInfoBuilder.channelInfo(context)
                if (ch == null) {
                    Text(
                        "(channel not yet created)",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    KeyValueBlock(
                        linkedMapOf(
                            "ID" to ch.id,
                            "Importance" to ch.importance,
                            "Bypass DND" to ch.bypassDnd.toString(),
                            "Sound" to ch.sound,
                            "Lockscreen visibility" to ch.lockscreenVisibility
                        )
                    )
                }
            }
            item { HorizontalDivider() }

            item { SectionTitle(stringResource(R.string.debug_info_section_dnd)) }
            item {
                val dnd = DebugInfoBuilder.dndStatus(context)
                KeyValueBlock(linkedMapOf("DND" to dnd.state, "Filter" to dnd.filter))
            }
            item { HorizontalDivider() }

            item { SectionTitle(stringResource(R.string.debug_info_section_alarms)) }
            item {
                Text(
                    DebugInfoBuilder.alarmsDescription(data.alarms, data.groups, anonymize = false),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun TestModeBanner() {
    Surface(
        color = Color(0xFFFFF59D),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.debug_info_section_test_mode),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF5D4037)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                DebugInfoBuilder.testFlagsBlock(),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun KeyValueBlock(map: Map<String, String>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        map.forEach { (k, v) ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    "$k:",
                    modifier = Modifier.width(140.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    v,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: DebugInfoBuilder.PermissionRow, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, color) = if (perm.granted) "✅" to Color(0xFF2E7D32) else "❌" to Color(0xFFC62828)
        Text(icon, fontSize = 14.sp, color = color)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(perm.name, fontSize = 14.sp)
            if (!perm.granted && perm.howToFix != null) {
                Text(
                    perm.howToFix,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!perm.granted) {
            TextButton(onClick = { openPermissionSettings(context, perm.name) }) {
                Text(stringResource(R.string.debug_info_open_settings))
            }
        }
    }
}

private fun openPermissionSettings(context: Context, permName: String) {
    val intent = when {
        permName.startsWith("POST_NOTIFICATIONS") ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        permName.startsWith("SCHEDULE_EXACT_ALARM") ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            else null
        permName.startsWith("USE_FULL_SCREEN_INTENT") ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            else null
        permName.startsWith("SYSTEM_ALERT_WINDOW") ->
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        permName.startsWith("Battery") ->
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } ?: return
    runCatching { context.startActivity(intent) }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("MyAlarm Debug Info", text))
}
