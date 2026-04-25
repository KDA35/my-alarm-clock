package com.myalarm.clock.ui.edit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myalarm.clock.R
import com.myalarm.clock.data.DayOfWeekMask
import com.myalarm.clock.util.pluralizeRepeats
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    onClose: () -> Unit,
    viewModel: AlarmEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
            viewModel.setRingtone(uri)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.edit_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onClose) {
                        Text(
                            stringResource(R.string.edit_cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onClose) }) {
                        Text(
                            stringResource(R.string.edit_save),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!state.isLoaded) {
                Spacer(Modifier.height(200.dp))
                return@Column
            }

            TimeWheelSection(
                hour = state.hour,
                minute = state.minute,
                onHourChange = viewModel::setHour,
                onMinuteChange = viewModel::setMinute
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DaySelectorSection(
                mask = state.daysOfWeek,
                onToggle = viewModel::toggleDay
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LabelField(
                value = state.label,
                onChange = viewModel::setLabel
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            EditSectionRow(
                title = stringResource(R.string.edit_sound_title),
                subtitle = ringtoneSubtitle(state.ringtoneUri),
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TITLE,
                            context.getString(R.string.edit_pick_ringtone)
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        state.ringtoneUri?.let {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                        }
                    }
                    ringtoneLauncher.launch(intent)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            VibrationRow(
                enabled = state.vibrationEnabled,
                onChange = viewModel::setVibration
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SnoozeSection(
                state = state,
                onToggleExpanded = viewModel::toggleSnoozeExpanded,
                onSetEnabled = viewModel::setSnoozeEnabled,
                onSetInterval = viewModel::setSnoozeInterval,
                onSetRepeats = viewModel::setSnoozeRepeats
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (viewModel.isEditing) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.edit_delete_button),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.edit_delete_dialog_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onClose)
                }) {
                    Text(
                        stringResource(R.string.edit_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.alarm_list_cancel))
                }
            }
        )
    }
}

@Composable
private fun ringtoneSubtitle(uriString: String?): String {
    val context = LocalContext.current
    if (uriString.isNullOrBlank()) return stringResource(R.string.edit_sound_default)
    val title = remember(uriString) {
        runCatching {
            RingtoneManager.getRingtone(context, Uri.parse(uriString))
                ?.getTitle(context)
        }.getOrNull()
    }
    return title ?: stringResource(R.string.edit_sound_default)
}

@Composable
private fun TimeWheelSection(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WheelNumberPicker(
                value = hour,
                range = 0..23,
                onValueChange = onHourChange,
                modifier = Modifier.width(110.dp)
            )
            Text(
                ":",
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            WheelNumberPicker(
                value = minute,
                range = 0..59,
                onValueChange = onMinuteChange,
                modifier = Modifier.width(110.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.edit_time_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

private val ItemHeight: Dp = 40.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeightPx = with(LocalDensity.current) { ItemHeight.toPx() }
    val containerHeight = ItemHeight * 5

    val initialIndex = remember { (value - range.first).coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(listState) {
        snapshotFlow {
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val center = firstVisible + if (offset > itemHeightPx / 2f) 1 else 0
            (range.first + center).coerceIn(range.first, range.last)
        }.distinctUntilChanged().collect { newValue ->
            if (newValue != currentValue) onValueChange(newValue)
        }
    }

    LaunchedEffect(value) {
        val targetIndex = (value - range.first).coerceAtLeast(0)
        val centerNow = listState.firstVisibleItemIndex +
            if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2f) 1 else 0
        if (targetIndex != centerNow && !listState.isScrollInProgress) {
            listState.scrollToItem(targetIndex)
        }
    }

    Box(
        modifier = modifier.height(containerHeight),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ItemHeight * 2),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(range.toList()) { item ->
                val distance = abs(item - value)
                val (fontSize, alpha) = when (distance) {
                    0 -> 64.sp to 1f
                    1 -> 18.sp to 0.5f
                    2 -> 14.sp to 0.25f
                    else -> 14.sp to 0f
                }
                val color = if (distance == 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ItemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "%02d".format(item),
                        fontSize = fontSize,
                        fontWeight = if (distance == 0) FontWeight.Light else FontWeight.Normal,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun DaySelectorSection(mask: Int, onToggle: (Int) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        val dayLabels = listOf(
            R.string.day_mon_short to DayOfWeekMask.MONDAY,
            R.string.day_tue_short to DayOfWeekMask.TUESDAY,
            R.string.day_wed_short to DayOfWeekMask.WEDNESDAY,
            R.string.day_thu_short to DayOfWeekMask.THURSDAY,
            R.string.day_fri_short to DayOfWeekMask.FRIDAY,
            R.string.day_sat_short to DayOfWeekMask.SATURDAY,
            R.string.day_sun_short to DayOfWeekMask.SUNDAY
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dayLabels.forEach { (labelRes, bit) ->
                val selected = (mask and bit) != 0
                DayCircle(
                    label = stringResource(labelRes),
                    selected = selected,
                    onClick = { onToggle(bit) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            DayOfWeekMask.formatDaysOfWeek(mask),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DayCircle(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg
        )
    }
}

@Composable
private fun LabelField(value: String, onChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.edit_alarm_name),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.primary
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Column {
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        if (value.isEmpty()) {
                            Text(
                                stringResource(R.string.edit_alarm_name_hint),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                        thickness = 1.dp
                    )
                }
            }
        )
    }
}

@Composable
private fun EditSectionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "›",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VibrationRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.edit_vibration_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (enabled) stringResource(R.string.edit_vibration_basic)
                else stringResource(R.string.edit_vibration_off),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun SnoozeSection(
    state: AlarmEditState,
    onToggleExpanded: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetRepeats: (Int) -> Unit
) {
    val context = LocalContext.current
    val summary = if (state.snoozeEnabled) {
        stringResource(
            R.string.edit_snooze_summary,
            state.snoozeIntervalMinutes,
            pluralizeRepeats(state.snoozeMaxRepeats, context)
        )
    } else {
        stringResource(R.string.edit_snooze_summary_off)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.edit_snooze_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val rotation by animateFloatAsState(
                targetValue = if (state.snoozeExpanded) 90f else 0f,
                label = "snoozeChevron"
            )
            Text(
                "›",
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }
        AnimatedVisibility(visible = state.snoozeExpanded) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.edit_snooze_enabled),
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = state.snoozeEnabled,
                        onCheckedChange = onSetEnabled
                    )
                }
                val sectionAlpha = if (state.snoozeEnabled) 1f else 0.4f
                Column(modifier = Modifier.alpha(sectionAlpha)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.edit_snooze_interval),
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${state.snoozeIntervalMinutes} ${stringResource(R.string.edit_snooze_minutes_short)}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = state.snoozeIntervalMinutes.toFloat(),
                        onValueChange = { onSetInterval(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 58,
                        enabled = state.snoozeEnabled
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(1, 15, 30, 45, 60).forEach { mark ->
                            Text(
                                if (mark == 60) "60 ${stringResource(R.string.edit_snooze_minutes_short)}" else "$mark",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.edit_snooze_repeats),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "1" to 1,
                            "3" to 3,
                            "5" to 5,
                            stringResource(R.string.edit_snooze_repeats_unlimited) to 0
                        ).forEach { (label, count) ->
                            RepeatsChip(
                                label = label,
                                selected = state.snoozeMaxRepeats == count,
                                onClick = { onSetRepeats(count) },
                                modifier = Modifier.weight(1f),
                                enabled = state.snoozeEnabled
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.edit_snooze_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RepeatsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    else
        androidx.compose.ui.graphics.Color.Transparent
    val border = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.outlineVariant
    val fg = if (selected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = fg
        )
    }
}
