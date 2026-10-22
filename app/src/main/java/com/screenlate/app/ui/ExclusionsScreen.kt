package com.screenlate.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import com.screenlate.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private class InstalledApp(val label: String, val packageName: String)

/** Apps the user does not want translated, picked from the installed apps with a search field. */
@Composable
fun ExclusionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository.get(context) }
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = null)
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val excluded = settings?.excludedPackages.orEmpty()
    val shown = apps.orEmpty().filter { app ->
        query.isBlank() || app.label.contains(query, ignoreCase = true) || app.packageName.contains(query, ignoreCase = true)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    Text(text = stringResource(R.string.exclusions_title), style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.exclusions_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (apps == null) {
                item { Text(stringResource(R.string.exclusions_loading), style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in excluded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { value ->
                                scope.launch {
                                    repository.update {
                                        it.copy(
                                            excludedPackages = if (value) it.excludedPackages + app.packageName else it.excludedPackages - app.packageName,
                                        )
                                    }
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Apps with a launcher icon; visible to us through the <queries> entry in the manifest. */
private fun launchableApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val activities = packageManager.queryIntentActivities(launcher, 0)
    return activities
        .map { InstalledApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}
