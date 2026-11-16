package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.security.configureAppCheck
import com.example.ui.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory: Configure Firebase App Check before Compose UI / AI / ViewModel creation
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AppNavigation()
        }
    }
}
