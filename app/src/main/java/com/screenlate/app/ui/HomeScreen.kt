package com.screenlate.app.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.screenlate.app.AppVersion
import com.screenlate.app.R
import com.screenlate.app.accessibility.ServiceStatus
import com.screenlate.app.model.ModelManager
import com.screenlate.app.services
import com.screenlate.app.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { context.services }
    val settings = remember { SettingsRepository.get(context) }
    val enabled by settings.enabled.collectAsStateWithLifecycle(initialValue = true)
    val vendorStepsDone by settings.vendorStepsDone.collectAsStateWithLifecycle(initialValue = false)
    val running by ServiceStatus.running.collectAsStateWithLifecycle()
    val model by app.modelManager.state.collectAsStateWithLifecycle()

    var status by remember { mutableStateOf(SetupStatus.read(context)) }
    LifecycleResumeEffect(Unit) {
        status = SetupStatus.read(context)
        app.modelManager.refresh()
        onPauseOrDispose { }
    }

    // After one refusal Android stops showing the permission dialog, so later taps open the settings page.
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = SetupStatus.read(context)
    }
    val modelReady = model is ModelManager.State.Ready

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

            TranslationToggle(
                enabled = enabled,
                status = translationStatus(status.serviceEnabled, running, modelReady, enabled),
                onToggle = { value -> scope.launch { settings.setEnabled(value) } },
            )

            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_settings))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = { SystemScreens.requestTile(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_tile))
                }
            }

            Text(text = stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            SetupSteps(
                status = status,
                model = model,
                vendorStepsDone = vendorStepsDone,
                onDownloadModel = { app.modelManager.download() },
                onReinstallModel = { app.modelManager.reinstall() },
                onClearCache = {
                    scope.launch {
                        app.repository.clear()
                        Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                    }
                },
                onVendorStepsDone = { value -> scope.launch { settings.setVendorStepsDone(value) } },
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAsked) {
                        notificationsAsked = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        SystemScreens.notificationSettings(context)
                    }
                },
            )

            // Long press opens the hidden diagnostics screen.
            Text(
                text = AppVersion.current,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(onOpenDiagnostics) { detectTapGestures(onLongPress = { onOpenDiagnostics() }) }
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun TranslationToggle(enabled: Boolean, status: String, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.translation_toggle), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun translationStatus(serviceEnabled: Boolean, running: Boolean, modelReady: Boolean, enabled: Boolean): String =
    stringResource(
        when {
            !serviceEnabled -> R.string.status_service_off
            !running -> R.string.status_service_not_running
            !modelReady -> R.string.status_model_missing
            enabled -> R.string.status_on
            else -> R.string.status_paused
        },
    )
