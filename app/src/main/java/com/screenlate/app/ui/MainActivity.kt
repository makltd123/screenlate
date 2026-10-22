package com.screenlate.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

private enum class Screen { Home, Settings, Exclusions, Diagnostics }

class MainActivity : ComponentActivity() {
    /** A screen asked for by an intent, e.g. "Settings" from the floating button's menu. */
    private var requested by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requested = intent?.getStringExtra(EXTRA_SCREEN)
        setContent {
            ScreenLateTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                LaunchedEffect(requested) {
                    if (requested == SCREEN_SETTINGS) screen = Screen.Settings
                    requested = null
                }
                BackHandler(enabled = screen != Screen.Home) {
                    screen = if (screen == Screen.Exclusions) Screen.Settings else Screen.Home
                }
                when (screen) {
                    Screen.Home -> HomeScreen(
                        onOpenSettings = { screen = Screen.Settings },
                        onOpenDiagnostics = { screen = Screen.Diagnostics },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = { screen = Screen.Home },
                        onOpenExclusions = { screen = Screen.Exclusions },
                    )
                    Screen.Exclusions -> ExclusionsScreen(onBack = { screen = Screen.Settings })
                    Screen.Diagnostics -> DiagnosticsScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requested = intent.getStringExtra(EXTRA_SCREEN)
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_SETTINGS = "settings"
    }
}
