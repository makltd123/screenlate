package com.screenlate.app.ui

import android.os.Debug
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenlate.app.R
import com.screenlate.app.accessibility.ServiceStatus
import com.screenlate.app.diagnostics.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hidden screen with service events, refresh timings, errors and memory use. Shows no screen text. */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val running by ServiceStatus.running.collectAsStateWithLifecycle()
    var snapshot by remember { mutableStateOf(Diagnostics.snapshot()) }
    var memoryMb by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = Diagnostics.snapshot()
            memoryMb = withContext(Dispatchers.Default) { (Debug.getPss() / 1024).toInt() }
            delay(1_000)
        }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    Text(text = stringResource(R.string.diagnostics_title), style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                Text(
                    text = stringResource(
                        R.string.diagnostics_summary,
                        stringResource(if (running) R.string.diagnostics_running else R.string.diagnostics_stopped),
                        snapshot.eventCount,
                        snapshot.refreshCount,
                        memoryMb,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.diagnostics_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            section(R.string.diagnostics_errors, snapshot.errors, timeFormat)
            section(R.string.diagnostics_refreshes, snapshot.refreshes, timeFormat)
            section(R.string.diagnostics_events, snapshot.events, timeFormat)
        }
    }
}

private fun LazyListScope.section(title: Int, entries: List<Diagnostics.Entry>, timeFormat: SimpleDateFormat) {
    item {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
    if (entries.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.diagnostics_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(entries) { entry ->
            Text(
                text = "${timeFormat.format(Date(entry.timeMillis))}  ${entry.message}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
