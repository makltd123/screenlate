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
import com.screenlate.app.R

private enum class StepState { Done, Todo, Later }

private class Step(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val state: StepState,
    val showActionsWhenDone: Boolean = false,
    val actions: @Composable ColumnScope.() -> Unit = {},
)

private fun Boolean.toState() = if (this) StepState.Done else StepState.Todo

/** First-run checklist: every step shows whether it is done and links to the system screen that does it. */
@Composable
fun SetupSteps(
    status: SetupStatus,
    vendorStepsDone: Boolean,
    onVendorStepsDone: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val steps = buildList {
        add(Step(R.string.step_model_title, R.string.step_model_body, StepState.Later))
        add(
            Step(R.string.step_service_title, R.string.step_service_body, status.serviceEnabled.toState()) {
                Button(onClick = { SystemScreens.accessibilitySettings(context) }) {
                    Text(stringResource(R.string.action_open_accessibility))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        text = stringResource(R.string.step_service_restricted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { SystemScreens.appDetails(context) }) {
                        Text(stringResource(R.string.action_open_app_info))
                    }
                }
            },
        )
        add(
            Step(R.string.step_notifications_title, R.string.step_notifications_body, status.notificationsEnabled.toState()) {
                Button(onClick = onRequestNotifications) { Text(stringResource(R.string.action_allow_notifications)) }
            },
        )
        add(
            Step(R.string.step_battery_title, R.string.step_battery_body, status.batteryUnrestricted.toState()) {
                Button(onClick = { SystemScreens.batteryExemption(context) }) { Text(stringResource(R.string.action_battery)) }
            },
        )
        if (SystemScreens.needsVendorSteps) {
            add(
                Step(
                    R.string.step_vendor_title,
                    R.string.step_vendor_body,
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
            Text(
                text = stringResource(step.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        StepState.Later -> Triple(R.string.state_later, colors.surfaceVariant, colors.onSurfaceVariant)
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
