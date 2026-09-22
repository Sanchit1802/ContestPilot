package com.sanchit.contestpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sanchit.contestpilot.ui.ContestPilotApp
import com.sanchit.contestpilot.ui.theme.ContestPilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ContestPilotTheme {
                ContestPilotApp()
            }
        }
    }
}
