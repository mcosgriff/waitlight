package io.cosgriff.waitlight

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.net.toUri
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.waitlight.domain.Stations
import io.cosgriff.waitlight.ui.WaitlightNavHost
import io.cosgriff.waitlight.ui.theme.WaitlightTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Any app can start this activity, so the id is checked against the stations before it's used.
        val openStation = intent.getIntExtra(EXTRA_STATION, NO_STATION).takeIf { Stations[it] != null }
        setContent {
            WaitlightTheme {
                // Gives every screen the theme's background and ink as its default colours.
                Surface(color = MaterialTheme.colorScheme.background) {
                    WaitlightNavHost(openStation)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_STATION = "io.cosgriff.waitlight.STATION"
        private const val NO_STATION = -1

        /** Opens the app on the list, afresh, as a tap on the widget does. */
        fun listIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        /** Opens the app on [stationId]'s detail, over the list. Its own data, so each is its own PendingIntent. */
        fun stationIntent(context: Context, stationId: Int): Intent = listIntent(context)
            .setData("waitlight://station/$stationId".toUri())
            .putExtra(EXTRA_STATION, stationId)
    }
}
