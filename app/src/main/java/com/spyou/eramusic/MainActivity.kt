package com.spyou.eramusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.spyou.eramusic.ui.EraNavHost
import com.spyou.eramusic.ui.permission.PermissionGate
import com.spyou.eramusic.ui.theme.EraMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EraMusicTheme {
                PermissionGate {
                    EraNavHost()
                }
            }
        }
    }
}
