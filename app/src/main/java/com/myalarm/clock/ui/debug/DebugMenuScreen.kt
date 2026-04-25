package com.myalarm.clock.ui.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myalarm.clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMenuScreen(
    onBack: () -> Unit,
    onOpenTestMenu: () -> Unit,
    onOpenDebugInfo: () -> Unit,
    onOpenDiagnostic: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_menu_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                MenuRow(
                    title = stringResource(R.string.debug_menu_test),
                    subtitle = stringResource(R.string.debug_menu_test_desc),
                    onClick = onOpenTestMenu
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(
                    title = stringResource(R.string.debug_menu_info),
                    subtitle = stringResource(R.string.debug_menu_info_desc),
                    onClick = onOpenDebugInfo
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(
                    title = stringResource(R.string.debug_menu_diagnostic),
                    subtitle = stringResource(R.string.debug_menu_diagnostic_desc),
                    onClick = onOpenDiagnostic
                )
            }
        }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
