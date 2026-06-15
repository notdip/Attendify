package com.dip.attendify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dip.attendify.ui.navigation.AttendifyNavHost
import com.dip.attendify.ui.theme.AttendifyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force dark system bars to match dark-only theme.
        // Without this, enableEdgeToEdge() may render light icons
        // on dark backgrounds on some devices.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(
                android.graphics.Color.TRANSPARENT
            ),
        )

        setContent {
            AttendifyTheme {
                AttendifyNavHost()
            }
        }
    }
}