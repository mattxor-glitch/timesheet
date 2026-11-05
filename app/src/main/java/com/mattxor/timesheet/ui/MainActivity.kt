package com.mattxor.timesheet.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw edge-to-edge explicitly so Android 15+ doesn't apply its own inset handling.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TimesheetTheme {
                TimesheetApp()
            }
        }
    }
}
