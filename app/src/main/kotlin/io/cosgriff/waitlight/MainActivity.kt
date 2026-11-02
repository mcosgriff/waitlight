package io.cosgriff.waitlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.waitlight.ui.WaitlightNavHost
import io.cosgriff.waitlight.ui.theme.WaitlightTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaitlightTheme {
                WaitlightNavHost()
            }
        }
    }
}
