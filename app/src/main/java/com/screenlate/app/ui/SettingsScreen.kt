package com.screenlate.app.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenlate.app.R
import com.screenlate.app.model.ModelManager
import com.screenlate.app.services
import com.screenlate.app.settings.AppSettings
import com.screenlate.app.settings.DisplayMode
import com.screenlate.app.settings.PlaqueColor
import com.screenlate.app.settings.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenExclusions: () -> Unit) {
    val context = LocalContext.current
    val app = remember { context.services }
    val repository = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    val loaded by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val model by app.modelManager.state.collectAsStateWithLifecycle()
    val settings = loaded ?: return
    val update: ((AppSettings) -> AppSettings) -> Unit = { transform -> scope.launch { repository.update(transform) } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            }

            Section(R.string.settings_section_translation) {
                SwitchRow(R.string.settings_enabled, R.string.settings_enabled_body, settings.enabled) { value ->
                    update { it.copy(enabled = value) }
                }
                SwitchRow(R.string.settings_economy, R.string.settings_economy_body, settings.economy) { value ->
                    update { it.copy(economy = value) }
                }
                SwitchRow(R.string.settings_editable, R.string.settings_editable_body, settings.translateEditable) { value ->
                    update { it.copy(translateEditable = value) }
                }
                Label(R.string.settings_threads, R.string.settings_threads_body)
                listOf(2, 4).forEach { count ->
                    Choice(stringResource(R.string.settings_threads_value, count), settings.threads == count) {
                        update { it.copy(threads = count) }
                    }
                }
            }

            Section(R.string.settings_section_plaques) {
                Label(R.string.settings_mode, null)
                DisplayMode.entries.forEach { mode ->
                    val (title, body) = when (mode) {
                        DisplayMode.Replace -> R.string.mode_replace to R.string.mode_replace_body
                        DisplayMode.Overlay -> R.string.mode_overlay to R.string.mode_overlay_body
                        DisplayMode.Hint -> R.string.mode_hint to R.string.mode_hint_body
                    }
                    Choice(stringResource(title), settings.displayMode == mode, stringResource(body)) {
                        update { it.copy(displayMode = mode) }
                    }
                }
                if (settings.displayMode == DisplayMode.Overlay) {
                    SliderRow(
                        label = { stringResource(R.string.settings_opacity, (it * 100).roundToInt()) },
                        value = settings.overlayOpacity,
                        range = 0.3f..1f,
                        steps = 6,
                    ) { value -> update { it.copy(overlayOpacity = value) } }
                }
                Label(R.string.settings_color, null)
                PlaqueColor.entries.forEach { color ->
                    val title = when (color) {
                        PlaqueColor.System -> R.string.color_system
                        PlaqueColor.Light -> R.string.color_light
                        PlaqueColor.Dark -> R.string.color_dark
                    }
                    Choice(stringResource(title), settings.plaqueColor == color) { update { it.copy(plaqueColor = color) } }
                }
                SliderRow(
                    label = { stringResource(R.string.settings_min_text, it.roundToInt()) },
                    value = settings.minTextSp.toFloat(),
                    range = 6f..14f,
                    steps = 7,
                ) { value -> update { it.copy(minTextSp = value.roundToInt()) } }
            }

            Section(R.string.settings_section_bubble) {
                SwitchRow(R.string.settings_bubble, R.string.settings_bubble_body, settings.showBubble) { value ->
                    update { it.copy(showBubble = value) }
                }
            }

            Section(R.string.settings_section_exclusions) {
                Hint(stringResource(R.string.settings_exclusions_body))
                OutlinedButton(onClick = onOpenExclusions) {
                    Text(stringResource(R.string.settings_exclusions_button, settings.excludedPackages.size))
                }
            }

            Section(R.string.settings_section_model) {
                val status = when (val state = model) {
                    is ModelManager.State.Ready -> stringResource(R.string.step_model_body_ready, state.bytes / 1_000_000)
                    is ModelManager.State.Downloading -> stringResource(
                        R.string.step_model_body_downloading,
                        state.downloaded / 1_000_000,
                        state.total / 1_000_000,
                    )
                    ModelManager.State.Installing -> stringResource(R.string.step_model_body_installing)
                    else -> stringResource(R.string.settings_model_missing)
                }
                Hint(status)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            app.repository.clear()
                            Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.action_clear_cache)) }
                    if (model is ModelManager.State.Ready) {
                        OutlinedButton(onClick = { app.modelManager.reinstall() }) { Text(stringResource(R.string.action_redownload)) }
                    } else if (model is ModelManager.State.Missing || model is ModelManager.State.Failed) {
                        Button(onClick = { app.modelManager.download() }) { Text(stringResource(R.string.action_download_model)) }
                    }
                }
            }

            if (model is ModelManager.State.Ready) TestTranslation()
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Label(@StringRes title: Int, @StringRes body: Int?) {
    Column {
        Text(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
        if (body != null) Hint(stringResource(body))
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(@StringRes title: Int, @StringRes body: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) { Label(title, body) }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(title: String, selected: Boolean, body: String? = null, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Hint(body)
        }
    }
}

/** A slider that saves only when the finger lifts, not on every step of the drag. */
@Composable
private fun SliderRow(
    label: @Composable (Float) -> String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onCommit: (Float) -> Unit,
) {
    var current by remember { mutableFloatStateOf(value) }
    LaunchedEffect(value) { current = value }
    Column {
        Text(text = label(current), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onCommit(current) },
            valueRange = range,
            steps = steps,
        )
    }
}

/** A text field to try the model directly; shows the translation and how long it took. */
@Composable
private fun TestTranslation() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sample = stringResource(R.string.test_sample)
    var input by rememberSaveable { mutableStateOf(sample) }
    var result by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Section(R.string.test_title) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.test_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !busy && input.isNotBlank(),
            onClick = {
                busy = true
                scope.launch {
                    val started = SystemClock.elapsedRealtime()
                    result = runCatching { context.services.repository.translateText(input) }
                        .map { "$it\n" + context.getString(R.string.test_time, SystemClock.elapsedRealtime() - started) }
                        .getOrElse { context.getString(R.string.test_failed, it.javaClass.simpleName) }
                    busy = false
                }
            },
        ) {
            Text(stringResource(R.string.action_translate))
        }
        if (result.isNotEmpty()) {
            Text(text = result, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
