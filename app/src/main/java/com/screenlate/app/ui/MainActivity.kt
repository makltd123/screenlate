package com.screenlate.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScreenLateTheme {
                var showDiagnostics by rememberSaveable { mutableStateOf(false) }
                if (showDiagnostics) {
                    BackHandler { showDiagnostics = false }
                    DiagnosticsScreen(onBack = { showDiagnostics = false })
                } else {
                    HomeScreen(onOpenDiagnostics = { showDiagnostics = true })
                }
            }
        }
    }
}
