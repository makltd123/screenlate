package com.screenlate.app.ui

import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.screenlate.app.BuildConfig
import com.screenlate.app.R
import com.screenlate.app.model.ModelManager

private enum class StepState { Done, Todo, InProgress }

private class Step(
    @StringRes val title: Int,
    val body: String,
    val state: StepState,
    val showActionsWhenDone: Boolean = false,
    val actions: @Composable ColumnScope.() -> Unit = {},
)

private fun Boolean.toState() = if (this) StepState.Done else StepState.Todo

private const val MB = 1_000_000L

/** First-run checklist: every step shows whether it is done and links to the screen that does it. */
@Composable
fun SetupSteps(
    status: SetupStatus,
    model: ModelManager.State,
    vendorStepsDone: Boolean,
    onDownloadModel: () -> Unit,
    onReinstallModel: () -> Unit,
    onClearCache: () -> Unit,
    onVendorStepsDone: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val steps = buildList {
        add(modelStep(model, onDownloadModel, onReinstallModel, onClearCache))
        add(
            Step(R.string.step_service_title, stringResource(R.string.step_service_body), status.serviceEnabled.toState()) {
                Button(onClick = { SystemScreens.accessibilitySettings(context) }) {
                    Text(stringResource(R.string.action_open_accessibility))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Hint(stringResource(R.string.step_service_restricted))
                    OutlinedButton(onClick = { SystemScreens.appDetails(context) }) {
                        Text(stringResource(R.string.action_open_app_info))
                    }
                }
            },
        )
        add(
            Step(
                R.string.step_notifications_title,
                stringResource(R.string.step_notifications_body),
                status.notificationsEnabled.toState(),
            ) {
                Button(onClick = onRequestNotifications) { Text(stringResource(R.string.action_allow_notifications)) }
            },
        )
        add(
            Step(R.string.step_battery_title, stringResource(R.string.step_battery_body), status.batteryUnrestricted.toState()) {
                Button(onClick = { SystemScreens.batteryExemption(context) }) { Text(stringResource(R.string.action_battery)) }
            },
        )
        if (SystemScreens.needsVendorSteps) {
            add(
                Step(
                    R.string.step_vendor_title,
                    stringResource(R.string.step_vendor_body),
                    vendorStepsDone.toState(),
                    showActionsWhenDone = true,
                ) {
                    OutlinedButton(onClick = { SystemScreens.appDetails(context) }) {
                        Text(stringResource(R.string.action_open_app_info))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vendorStepsDone, onCheckedChange = onVendorStepsDone)
                        Text(stringResource(R.string.step_vendor_done))
                    }
                },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        steps.forEachIndexed { index, step -> StepCard(number = index + 1, step = step) }
    }
}

@Composable
private fun modelStep(
    model: ModelManager.State,
    onDownload: () -> Unit,
    onReinstall: () -> Unit,
    onClearCache: () -> Unit,
): Step = when (model) {
    ModelManager.State.Missing -> Step(
        R.string.step_model_title,
        stringResource(R.string.step_model_body_missing, BuildConfig.MODEL_BYTES / MB),
        StepState.Todo,
    ) {
        Button(onClick = onDownload) { Text(stringResource(R.string.action_download_model)) }
    }
    is ModelManager.State.Downloading -> Step(
        R.string.step_model_title,
        stringResource(R.string.step_model_body_downloading, model.downloaded / MB, model.total / MB),
        StepState.InProgress,
    ) {
        LinearProgressIndicator(
            progress = { if (model.total > 0) model.downloaded.toFloat() / model.total else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    ModelManager.State.Installing -> Step(
        R.string.step_model_title,
        stringResource(R.string.step_model_body_installing),
        StepState.InProgress,
    ) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    is ModelManager.State.Ready -> Step(
        R.string.step_model_title,
        stringResource(R.string.step_model_body_ready, model.bytes / MB),
        StepState.Done,
        showActionsWhenDone = true,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClearCache) { Text(stringResource(R.string.action_clear_cache)) }
            OutlinedButton(onClick = onReinstall) { Text(stringResource(R.string.action_redownload)) }
        }
    }
    is ModelManager.State.Failed -> Step(
        R.string.step_model_title,
        stringResource(
            when (model.reason) {
                ModelManager.Reason.Network -> R.string.step_model_failed_network
                ModelManager.Reason.NoSpace -> R.string.step_model_failed_space
                ModelManager.Reason.Checksum -> R.string.step_model_failed_checksum
                ModelManager.Reason.Storage -> R.string.step_model_failed_storage
            },
        ),
        StepState.Todo,
    ) {
        Button(onClick = onDownload) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepCard(number: Int, step: Step) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$number. ${stringResource(step.title)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StateBadge(step.state)
            }
            Hint(step.body)
            if (step.state != StepState.Done || step.showActionsWhenDone) step.actions(this)
        }
    }
}

@Composable
private fun StateBadge(state: StepState) {
    val colors = MaterialTheme.colorScheme
    val (label, container, content) = when (state) {
        StepState.Done -> Triple(R.string.state_done, colors.primaryContainer, colors.onPrimaryContainer)
        StepState.Todo -> Triple(R.string.state_todo, colors.errorContainer, colors.onErrorContainer)
        StepState.InProgress -> Triple(R.string.state_in_progress, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
