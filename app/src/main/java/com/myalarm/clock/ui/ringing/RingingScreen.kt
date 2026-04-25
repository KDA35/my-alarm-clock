package com.myalarm.clock.ui.ringing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myalarm.clock.R
import com.myalarm.clock.data.Alarm
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Mint = Color(0xFF60C099)
private val MintButton = Color(0xFF1D9E75)
private val SheetBackground = Color(0xFF161B28)

@Composable
fun RingingScreen(
    onSnooze: (Int) -> Unit,
    onPostpone: (Int) -> Unit,
    onPostponeUntil: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    viewModel: RingingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPostponeSheet by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    BackHandler { /* blocked */ }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF1A2540), Color(0xFF050810)),
                        center = Offset(size.width / 2f, 0f),
                        radius = 1200f
                    )
                )
            }
    ) {
        val statusBars = WindowInsets.systemBars.asPaddingValues()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBars.calculateTopPadding())
        ) {
            TopBar(currentTime = state.currentTime)
            Spacer(Modifier.weight(1f))
            CenterBlock(alarm = state.alarm, currentTime = state.currentTime)
            Spacer(Modifier.weight(1f))
            SnoozeControls(
                state = state,
                onIncrement = viewModel::incrementSnooze,
                onDecrement = viewModel::decrementSnooze
            )
            Spacer(Modifier.height(28.dp))
            ActionButtons(
                snoozeMinutes = state.snoozeMinutes,
                onSnoozeClick = {
                    val minutes = viewModel.snooze()
                    onSnooze(minutes)
                },
                onPostponeClick = { showPostponeSheet = true },
                onDismissClick = {
                    viewModel.dismiss()
                    onDismiss()
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            RepeatsHint(alarm = state.alarm)
            Spacer(Modifier.height(statusBars.calculateBottomPadding().coerceAtLeast(8.dp)))
        }
    }

    if (showPostponeSheet) {
        PostponeBottomSheet(
            currentTime = state.currentTime,
            onPreset = { minutes ->
                showPostponeSheet = false
                viewModel.postpone(minutes)
                onPostpone(minutes)
            },
            onPickTime = {
                showPostponeSheet = false
                showTimePicker = true
            },
            onDismissSheet = { showPostponeSheet = false }
        )
    }

    if (showTimePicker) {
        PostponeTimePicker(
            currentTime = state.currentTime,
            onConfirm = { hour, minute ->
                showTimePicker = false
                viewModel.postponeUntil(hour, minute)
                onPostponeUntil(hour, minute)
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@Composable
private fun TopBar(currentTime: Long) {
    val formatted = remember(currentTime / 30_000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentTime))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            formatted,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
        Text(
            stringResource(R.string.ringing_label_alarm).uppercase(),
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun CenterBlock(alarm: Alarm?, currentTime: Long) {
    val timeText = remember(alarm) {
        if (alarm == null) "--:--"
        else "${alarm.hour}:%02d".format(alarm.minute)
    }
    val dateText = remember(currentTime / 60_000L) {
        SimpleDateFormat("EEEE, d MMMM", Locale("ru")).format(Date(currentTime))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            timeText,
            fontSize = 80.sp,
            fontWeight = FontWeight.Light,
            color = Color.White.copy(alpha = 0.95f),
            letterSpacing = (-2).sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
        Spacer(Modifier.height(28.dp))
        val label = alarm?.label?.takeIf { it.isNotBlank() }
        if (label != null) {
            Text(
                label,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(
            dateText,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SnoozeControls(
    state: RingingState,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(
            symbol = "−",
            enabled = state.canDecrement,
            onClick = onDecrement
        )
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier.defaultMinSize(minWidth = 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedContent(
                targetState = state.snoozeMinutes,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically { it } + fadeIn()) togetherWith
                            (slideOutVertically { -it } + fadeOut())
                    } else {
                        (slideInVertically { -it } + fadeIn()) togetherWith
                            (slideOutVertically { it } + fadeOut())
                    }
                },
                label = "snoozeMinutes"
            ) { value ->
                Text(
                    "$value",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.ringing_minutes),
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = state.resultingRingTime(),
                label = "snoozeRingAt"
            ) { ringAt ->
                Text(
                    stringResource(R.string.ringing_will_ring_at, ringAt),
                    fontSize = 14.sp,
                    color = Mint,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        fontFeatureSettings = "tnum"
                    )
                )
            }
        }
        Spacer(Modifier.width(20.dp))
        RoundIconButton(
            symbol = "+",
            enabled = state.canIncrement,
            onClick = onIncrement
        )
    }
}

@Composable
private fun RoundIconButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.3f
    Box(
        modifier = Modifier
            .size(56.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            fontSize = 28.sp,
            color = Color.White
        )
    }
}

@Composable
private fun ActionButtons(
    snoozeMinutes: Int,
    onSnoozeClick: () -> Unit,
    onPostponeClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryActionButton(
                title = stringResource(R.string.ringing_snooze_btn),
                subtitle = stringResource(R.string.ringing_snooze_btn_subtitle, snoozeMinutes),
                onClick = onSnoozeClick,
                modifier = Modifier.weight(1f)
            )
            SecondaryActionButton(
                title = stringResource(R.string.ringing_postpone_btn),
                subtitle = stringResource(R.string.ringing_postpone_btn_subtitle),
                onClick = onPostponeClick,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MintButton)
                .clickable(onClick = onDismissClick)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.ringing_dismiss_btn),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SecondaryActionButton(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun RepeatsHint(alarm: Alarm?) {
    if (alarm == null || alarm.snoozeIntervalMinutes <= 0) {
        Spacer(Modifier.height(14.dp))
        return
    }
    val text = pluralizeRepeats(alarm.snoozeMaxRepeats)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponeBottomSheet(
    currentTime: Long,
    onPreset: (Int) -> Unit,
    onPickTime: () -> Unit,
    onDismissSheet: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        containerColor = SheetBackground,
        contentColor = Color.White,
        dragHandle = null,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.postpone_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismissSheet),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "×",
                        fontSize = 22.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val presetLabels = listOf(
                R.string.postpone_30min to 30,
                R.string.postpone_1h to 60,
                R.string.postpone_2h to 120,
                R.string.postpone_4h to 240
            )
            presetLabels.forEachIndexed { idx, (labelRes, minutes) ->
                if (idx > 0) Spacer(Modifier.height(6.dp))
                PostponeRow(
                    label = stringResource(labelRes),
                    timeText = formatTimeAfter(currentTime, minutes),
                    onClick = { onPreset(minutes) }
                )
            }
            Spacer(Modifier.height(6.dp))
            CustomTimeRow(onClick = onPickTime)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PostponeRow(label: String, timeText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = Color.White)
        Text(
            timeText,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f),
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun CustomTimeRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MintButton.copy(alpha = 0.15f))
            .border(
                width = 1.dp,
                color = MintButton.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.postpone_pick_time),
            fontSize = 14.sp,
            color = Mint
        )
        Text("›", fontSize = 18.sp, color = Mint)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostponeTimePicker(
    currentTime: Long,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember(currentTime) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(Calendar.HOUR_OF_DAY, 1)
            val rounded = ((get(Calendar.MINUTE) + 4) / 5) * 5
            set(Calendar.MINUTE, rounded.coerceIn(0, 55))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }
    val pickerState = rememberTimePickerState(
        initialHour = initial.first,
        initialMinute = initial.second,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SheetBackground,
        title = {
            Text(
                stringResource(R.string.time_picker_title),
                color = Color.White
            )
        },
        text = {
            TimePicker(state = pickerState)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.time_picker_confirm), color = Mint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.time_picker_cancel),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    )
}

private fun formatTimeAfter(currentTime: Long, minutesAhead: Int): String {
    val target = currentTime + minutesAhead * 60_000L
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(target))
}

internal fun pluralizeRepeats(count: Int): String = when {
    count == 0 -> "Без ограничений"
    count % 10 == 1 && count % 100 != 11 -> "$count повтор остался"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count повтора осталось"
    else -> "$count повторов осталось"
}
