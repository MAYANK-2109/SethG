package com.sethg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.sethg.app.data.local.AppPreferences
import com.sethg.app.ui.navigation.SethGNavHost
import com.sethg.app.ui.theme.SethGTheme
import com.sethg.app.util.ProvideAppLocale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val languageCode by appPreferences.languageFlow.collectAsState(initial = "en")

            ProvideAppLocale(languageCode = languageCode) {
                SethGTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SethGNavHost()
                    }
                }
            }
        }
    }
}
