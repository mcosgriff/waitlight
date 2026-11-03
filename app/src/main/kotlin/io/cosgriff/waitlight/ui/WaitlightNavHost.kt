package io.cosgriff.waitlight.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.waitlight.ui.detail.StationDetailScreen
import io.cosgriff.waitlight.ui.list.StationListScreen
import io.cosgriff.waitlight.ui.location.LocationPromptScreen

/**
 * The app's screens. The list is the start once location is allowed or the prompt was answered, before that the
 * location prompt. Nothing is drawn for the moment it takes to read the settings, so the wrong screen never flashes.
 * [openStation] (a tap on a station in the widget) opens that station's detail over the list.
 */
@Composable
fun WaitlightNavHost(openStation: Int? = null) {
    val startViewModel: StartViewModel = hiltViewModel()
    val start by startViewModel.start.collectAsStateWithLifecycle()
    start?.let { WaitlightNavDisplay(it, openStation, onLocationPromptAnswered = startViewModel::locationPromptAnswered) }
}

@Composable
private fun WaitlightNavDisplay(start: NavKey, openStation: Int?, onLocationPromptAnswered: () -> Unit) {
    val backStack = if (start == StationList && openStation != null) {
        rememberNavBackStack(StationList, StationDetail(openStation))
    } else {
        rememberNavBackStack(start)
    }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<StationList> {
                StationListScreen(
                    onOpenStation = { backStack.add(StationDetail(it)) },
                    onTurnOnLocation = { backStack.add(LocationPrompt) },
                )
            }
            entry<StationDetail> { key -> StationDetailScreen(key.stationId, onBack = { backStack.removeLastOrNull() }) }
            entry<LocationPrompt> {
                LocationPromptScreen(
                    onDone = {
                        onLocationPromptAnswered()
                        if (StationList !in backStack) backStack.add(0, StationList)
                        backStack.remove(LocationPrompt)
                    },
                )
            }
        },
    )
}
