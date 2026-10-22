package com.screenlate.app.ui

import android.content.Context
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenlate.app.R
import com.screenlate.app.accessibility.ServiceStatus
import com.screenlate.app.diagnostics.Diagnostics
import com.screenlate.app.model.ModelManager
import com.screenlate.app.services
import com.screenlate.app.translate.ModelSelfTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hidden screen with service events, refresh timings, errors and memory use. Shows no screen text. */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.services }
    val scope = rememberCoroutineScope()
    val running by ServiceStatus.running.collectAsStateWithLifecycle()
    val model by app.modelManager.state.collectAsStateWithLifecycle()
    var snapshot by remember { mutableStateOf(Diagnostics.snapshot()) }
    var memoryMb by remember { mutableIntStateOf(0) }
    var cache by remember { mutableStateOf("") }
    var selfTest by remember { mutableStateOf("") }
    var selfTestRunning by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = Diagnostics.snapshot()
            memoryMb = withContext(Dispatchers.Default) { (Debug.getPss() / 1024).toInt() }
            cache = context.getString(
                R.string.diagnostics_cache,
                app.repository.memoryEntries,
                app.repository.diskEntries(),
                "%.1f".format(Locale.ROOT, app.repository.diskBytes() / 1e6),
            )
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
                Text(text = cache, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Text(
                    text = stringResource(R.string.diagnostics_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    enabled = model is ModelManager.State.Ready && !selfTestRunning,
                    onClick = {
                        selfTestRunning = true
                        selfTest = context.getString(R.string.diagnostics_self_test_running)
                        scope.launch {
                            selfTest = runCatching { ModelSelfTest(context, app).run() }
                                .map { describe(context, it) }
                                .getOrElse { context.getString(R.string.diagnostics_self_test_failed, it.javaClass.simpleName) }
                            selfTestRunning = false
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.diagnostics_self_test))
                }
            }
            if (selfTest.isNotEmpty()) {
                item { Text(text = selfTest, style = MaterialTheme.typography.bodyMedium) }
            }
            section(R.string.diagnostics_errors, snapshot.errors, timeFormat)
            section(R.string.diagnostics_refreshes, snapshot.refreshes, timeFormat)
            section(R.string.diagnostics_events, snapshot.events, timeFormat)
        }
    }
}

private fun describe(context: Context, report: ModelSelfTest.Report): String {
    val summary = context.getString(
        R.string.diagnostics_self_test_report,
        report.phrasesClose,
        report.phrasesMatched,
        report.phrases,
        report.sentencesMatched,
        report.sentences,
        report.loadMillis,
        report.medianPhraseMillis,
        report.maxPhraseMillis,
        report.totalMillis,
        "%.2f".format(Locale.ROOT, report.cachedPhraseMillis),
        report.memoryMb,
    )
    if (report.mismatches.isEmpty()) return summary
    return summary + "\n" + context.getString(R.string.diagnostics_self_test_mismatches, report.mismatches.joinToString("; "))
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
