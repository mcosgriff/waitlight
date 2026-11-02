package io.cosgriff.waitlight.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Stations

/** The app's screens. The list is the start; a station opens over it. */
@Composable
fun WaitlightNavHost() {
    val backStack = rememberNavBackStack(StationList)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<StationList> { ScreenFrame(stringResource(R.string.list_title)) }
            entry<StationDetail> { key -> ScreenFrame(Stations[key.stationId]?.name.orEmpty()) }
        },
    )
}

/** Until the screens are built: the screen's title and nothing else. */
@Composable
private fun ScreenFrame(title: String) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
    }
}
