package com.upptagen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.upptagen.app.ui.UpptagenMainApp
import com.upptagen.app.ui.theme.UpptagenTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as UpptagenApplication
        val repository = app.repository

        setContent {
            UpptagenTheme {
                UpptagenMainApp(repository = repository)
            }
        }
    }
}
