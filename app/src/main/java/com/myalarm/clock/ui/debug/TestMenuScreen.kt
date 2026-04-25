package com.myalarm.clock.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.myalarm.clock.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestMenuScreen(
    onBack: () -> Unit,
    viewModel: TestMenuViewModel = hiltViewModel()
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun show(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.test_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item { SectionHeader(stringResource(R.string.test_group_a_title)) }
            item {
                TestButton(stringResource(R.string.test_5sec), stringResource(R.string.test_5sec_desc)) {
                    viewModel.createTestAlarm(5, "Тест 5 сек") { ts ->
                        show("Будильник запланирован на $ts")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_30sec), stringResource(R.string.test_30sec_desc)) {
                    viewModel.createTestAlarm(30, "Тест 30 сек") { ts ->
                        show("Будильник запланирован на $ts")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_2min), stringResource(R.string.test_2min_desc)) {
                    viewModel.createTestAlarm(120, "Тест 2 мин (Doze)") { ts ->
                        show("Будильник запланирован на $ts")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_series), stringResource(R.string.test_series_desc)) {
                    viewModel.createTestSeries { count ->
                        show("Запланировано $count тестов")
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.test_group_b_title)) }
            item {
                TestButton(stringResource(R.string.test_no_layer1), stringResource(R.string.test_no_layer1_desc)) {
                    viewModel.setLayerFlags(disableL1 = true, disableL2 = false, disableL3 = false)
                    viewModel.createTestAlarm(15, "Тест без L1") { ts ->
                        show("L1 отключён · сработает в $ts")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_no_layer2), stringResource(R.string.test_no_layer2_desc)) {
                    viewModel.setLayerFlags(disableL1 = false, disableL2 = true, disableL3 = false)
                    viewModel.createTestAlarm(15, "Тест без L2") { ts ->
                        show("L2 отключён · сработает в $ts")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_only_layer3), stringResource(R.string.test_only_layer3_desc)) {
                    viewModel.setLayerFlags(disableL1 = true, disableL2 = true, disableL3 = false)
                    viewModel.createTestAlarm(15, "Тест только L3") { ts ->
                        show("Только L3 · сработает в $ts")
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.resetLayerFlags()
                            show("Флаги сброшены — все слои работают")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.test_reset_flags))
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.test_group_c_title)) }
            item {
                TestButton(
                    stringResource(R.string.test_open_ui_direct),
                    stringResource(R.string.test_open_ui_direct_desc)
                ) {
                    viewModel.openRingingUiDirect {
                        show("Открыт UI без AlarmManager / звука")
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.test_group_d_title)) }
            item {
                TestButton(stringResource(R.string.test_delete_all), "") {
                    viewModel.deleteAllTestAlarms { count ->
                        show("Удалено: $count")
                    }
                }
            }
            item {
                TestButton(stringResource(R.string.test_clear_logs), "") {
                    viewModel.clearLogs {
                        show("Логи очищены")
                    }
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestButton(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
