package io.cosgriff.waitlight.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.waitlight.ui.detail.StationDetailScreen
import io.cosgriff.waitlight.ui.list.StationListScreen
import io.cosgriff.waitlight.ui.location.LocationPromptScreen

/** The app's screens. The list is the start, after the location prompt while location isn't allowed. */
@Composable
fun WaitlightNavHost() {
    val context = LocalContext.current
    val located = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    val backStack = rememberNavBackStack(if (located) StationList else LocationPrompt)
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
                        if (StationList !in backStack) backStack.add(0, StationList)
                        backStack.remove(LocationPrompt)
                    },
                )
            }
        },
    )
}
