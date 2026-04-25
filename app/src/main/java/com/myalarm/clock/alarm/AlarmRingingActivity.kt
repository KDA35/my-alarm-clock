package com.myalarm.clock.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.myalarm.clock.data.AlarmRepository
import com.myalarm.clock.ui.theme.MyAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    @Inject lateinit var repository: AlarmRepository
    @Inject lateinit var scheduler: AlarmScheduler

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
    }

    private var alarmId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)

        setContent {
            MyAlarmTheme {
                RingingScreen(
                    alarmId = alarmId,
                    onSnooze = { minutes -> doSnooze(minutes) },
                    onDismiss = { doDismiss() }
                )
            }
        }
    }

    private fun doSnooze(minutes: Int) {
        lifecycleScope.launch {
            val alarm = repository.getById(alarmId) ?: return@launch
            scheduler.scheduleSnooze(alarm, minutes)
            stopAlarmService()
            finish()
        }
    }

    private fun doDismiss() {
        stopAlarmService()
        finish()
    }

    private fun stopAlarmService() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
private fun RingingScreen(
    alarmId: Long,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var snoozeMinutes by remember { mutableIntStateOf(10) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Будильник", color = Color.White, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "ID: $alarmId",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(32.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { if (snoozeMinutes > 1) snoozeMinutes-- }) { Text("−") }
                Spacer(Modifier.width(16.dp))
                Text("$snoozeMinutes мин", color = Color.White, fontSize = 24.sp)
                Spacer(Modifier.width(16.dp))
                Button(onClick = { if (snoozeMinutes < 60) snoozeMinutes++ }) { Text("+") }
            }
            Spacer(Modifier.height(32.dp))

            Button(onClick = { onSnooze(snoozeMinutes) }) {
                Text("Отложить на $snoozeMinutes мин")
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onDismiss) {
                Text("Отключить", color = Color.White)
            }
        }
    }
}
