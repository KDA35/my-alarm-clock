package com.myalarm.clock.ui.list

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myalarm.clock.R
import com.myalarm.clock.data.Alarm
import com.myalarm.clock.data.AlarmGroup
import com.myalarm.clock.data.DayOfWeekMask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onOpenLogs: () -> Unit,
    onCreateAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    onShowOnboarding: () -> Unit = {},
    onOpenDebug: () -> Unit = {},
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()
    val grouped by viewModel.grouped.collectAsState()
    val nextTriggerInfo by viewModel.nextTriggerInfo.collectAsState()

    var menuOpen by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Alarm?>(null) }
    var groupAction by remember { mutableStateOf<AlarmGroup?>(null) }
    var renameTarget by remember { mutableStateOf<AlarmGroup?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<AlarmGroup?>(null) }

    val permissions = rememberPermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_list_title)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_logs)) },
                            onClick = {
                                menuOpen = false
                                onOpenLogs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_onboarding)) },
                            onClick = {
                                menuOpen = false
                                onShowOnboarding()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_debug)) },
                            onClick = {
                                menuOpen = false
                                onOpenDebug()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateAlarm) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!permissions.allGranted) {
                PermissionsBanner(permissions)
            }
            OutlinedButton(
                onClick = {
                    viewModel.createTestAlarm()
                    Toast.makeText(
                        context,
                        context.getString(R.string.alarm_list_test_scheduled),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.alarm_list_test_button))
            }
            nextTriggerInfo?.let { info ->
                NextTriggerHint(info)
            }
            if (alarms.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                GroupedAlarmList(
                    grouped = grouped,
                    onToggle = viewModel::toggleEnabled,
                    onAlarmClick = onEditAlarm,
                    onAlarmLongClick = { alarm -> deleteCandidate = alarm },
                    onGroupToggle = viewModel::toggleGroup,
                    onGroupLongClick = { group -> groupAction = group },
                    contentPadding = PaddingValues(bottom = 96.dp)
                )
            }
        }
    }

    deleteCandidate?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.alarm_list_delete_title)) },
            text = {
                Text("%02d:%02d".format(alarm.hour, alarm.minute))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(alarm)
                    deleteCandidate = null
                }) {
                    Text(stringResource(R.string.alarm_list_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }

    groupAction?.let { group ->
        AlertDialog(
            onDismissRequest = { groupAction = null },
            title = { Text(group.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        groupAction = null
                        renameTarget = group
                    }) {
                        Text(stringResource(R.string.group_rename))
                    }
                    TextButton(onClick = {
                        groupAction = null
                        deleteGroupTarget = group
                    }) {
                        Text(
                            stringResource(R.string.group_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { groupAction = null }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }

    renameTarget?.let { group ->
        var name by remember(group.id) { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.group_rename)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameGroup(group.id, name)
                    renameTarget = null
                }) {
                    Text(stringResource(R.string.edit_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }

    deleteGroupTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text(stringResource(R.string.group_delete_dialog_title)) },
            text = { Text(stringResource(R.string.group_delete_dialog_body, group.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(group)
                    deleteGroupTarget = null
                }) {
                    Text(
                        stringResource(R.string.group_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupTarget = null }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }
}

@Composable
private fun NextTriggerHint(info: NextTriggerInfo) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        val text = if (info.deltaText == "сейчас") {
            stringResource(R.string.alarm_list_next_now)
        } else {
            stringResource(R.string.alarm_list_next, info.timeText, info.deltaText)
        }
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.alarm_list_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.alarm_list_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedAlarmList(
    grouped: GroupedAlarms,
    onToggle: (Long, Boolean) -> Unit,
    onAlarmClick: (Long) -> Unit,
    onAlarmLongClick: (Alarm) -> Unit,
    onGroupToggle: (Long, Boolean) -> Unit,
    onGroupLongClick: (AlarmGroup) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(contentPadding = contentPadding) {
        grouped.groups.forEach { section ->
            item(key = "group-${section.group.id}") {
                GroupHeader(
                    group = section.group,
                    onToggle = { onGroupToggle(section.group.id, it) },
                    onLongClick = { onGroupLongClick(section.group) }
                )
            }
            items(section.alarms, key = { "alarm-${it.id}" }) { alarm ->
                AlarmListItem(
                    alarm = alarm,
                    onToggle = { onToggle(alarm.id, it) },
                    onClick = { onAlarmClick(alarm.id) },
                    onLongClick = { onAlarmLongClick(alarm) },
                    showGroupAccent = true
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (grouped.ungrouped.isNotEmpty()) {
            if (grouped.groups.isNotEmpty()) {
                item(key = "ungrouped-header") { UngroupedHeader() }
            }
            items(grouped.ungrouped, key = { "alarm-${it.id}" }) { alarm ->
                AlarmListItem(
                    alarm = alarm,
                    onToggle = { onToggle(alarm.id, it) },
                    onClick = { onAlarmClick(alarm.id) },
                    onLongClick = { onAlarmLongClick(alarm) },
                    showGroupAccent = false
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(
    group: AlarmGroup,
    onToggle: (Boolean) -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            group.name.uppercase(),
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Switch(checked = group.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun UngroupedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            stringResource(R.string.group_ungrouped).uppercase(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmListItem(
    alarm: Alarm,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showGroupAccent: Boolean = false
) {
    val alpha = if (alarm.enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showGroupAccent) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "%02d:%02d".format(alarm.hour, alarm.minute),
                fontSize = 36.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Text(
                DayOfWeekMask.formatDaysOfWeek(alarm.daysOfWeek),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
            if (alarm.label.isNotEmpty()) {
                Text(
                    alarm.label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
            }
        }
        Switch(checked = alarm.enabled, onCheckedChange = onToggle)
    }
}

private data class PermissionState(
    val notificationsGranted: Boolean,
    val exactAlarmsGranted: Boolean,
    val fullScreenIntentGranted: Boolean,
    val grantNotifications: () -> Unit,
    val grantExactAlarms: () -> Unit,
    val grantFullScreenIntent: () -> Unit
) {
    val allGranted: Boolean
        get() = notificationsGranted && exactAlarmsGranted && fullScreenIntentGranted
}

@Composable
private fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notifications by remember { mutableStateOf(checkNotifications(context)) }
    var exactAlarms by remember { mutableStateOf(checkExactAlarms(context)) }
    var fullScreen by remember { mutableStateOf(checkFullScreenIntent(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notifications = checkNotifications(context)
                exactAlarms = checkExactAlarms(context)
                fullScreen = checkFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifications = granted }

    return PermissionState(
        notificationsGranted = notifications,
        exactAlarmsGranted = exactAlarms,
        fullScreenIntentGranted = fullScreen,
        grantNotifications = {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        grantExactAlarms = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        },
        grantFullScreenIntent = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    )
}

private fun checkNotifications(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun checkExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    return am.canScheduleExactAlarms()
}

private fun checkFullScreenIntent(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.canUseFullScreenIntent()
}

@Composable
private fun PermissionsBanner(state: PermissionState) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.permission_banner_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            if (!state.notificationsGranted) {
                PermissionRow(
                    label = stringResource(R.string.permission_notifications),
                    onGrant = state.grantNotifications
                )
            }
            if (!state.exactAlarmsGranted) {
                PermissionRow(
                    label = stringResource(R.string.permission_exact_alarms),
                    onGrant = state.grantExactAlarms
                )
            }
            if (!state.fullScreenIntentGranted) {
                PermissionRow(
                    label = stringResource(R.string.permission_full_screen),
                    onGrant = state.grantFullScreenIntent
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onGrant) {
            Text(stringResource(R.string.permission_grant_button))
        }
    }
}
