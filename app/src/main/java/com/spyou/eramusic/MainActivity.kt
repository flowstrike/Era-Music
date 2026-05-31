package com.spyou.eramusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyou.eramusic.data.SettingsStore
import com.spyou.eramusic.ui.EraNavHost
import com.spyou.eramusic.ui.permission.PermissionGate
import com.spyou.eramusic.ui.theme.EraMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsStore = (application as EraApp).container.settingsStore
            val darkMode by settingsStore.darkMode.collectAsStateWithLifecycle(
                initialValue = SettingsStore.DarkMode.SYSTEM
            )
            val darkTheme = when (darkMode) {
                SettingsStore.DarkMode.DARK -> true
                SettingsStore.DarkMode.LIGHT -> false
                SettingsStore.DarkMode.SYSTEM -> isSystemInDarkTheme()
            }
            EraMusicTheme(darkTheme = darkTheme) {
                PermissionGate {
                    EraNavHost()
                }
            }
        }
    }
}
