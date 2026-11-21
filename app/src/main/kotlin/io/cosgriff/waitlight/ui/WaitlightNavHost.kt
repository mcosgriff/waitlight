package io.cosgriff.waitlight.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.cosgriff.waitlight.ui.detail.StationDetailScreen
import io.cosgriff.waitlight.ui.location.LocationPromptScreen
import io.cosgriff.waitlight.ui.vans.DriveByLogScreen
import io.cosgriff.waitlight.ui.vans.VanDetailScreen
import io.cosgriff.waitlight.ui.vehicles.EditVehicleScreen
import io.cosgriff.waitlight.ui.vehicles.VehicleDetailScreen
import io.cosgriff.waitlight.ui.vehicles.VehiclesScreen
import java.time.LocalDate

/**
 * The app's screens. The list is the start once location is allowed or the prompt was answered, before that the
 * location prompt. Nothing is drawn for the moment it takes to read the settings, so the wrong screen never flashes.
 * [openStation] (a tap on a station in the widget) opens that station's detail over the list; [openVehicle] (a tap
 * on a reminder) that vehicle's window over the vans tab.
 */
@Composable
fun WaitlightNavHost(openStation: Int? = null, openVehicle: Long? = null) {
    val startViewModel: StartViewModel = hiltViewModel()
    val start by startViewModel.start.collectAsStateWithLifecycle()
    start?.let { WaitlightNavDisplay(it, openStation, openVehicle, onLocationPromptAnswered = startViewModel::locationPromptAnswered) }
}

@Composable
private fun WaitlightNavDisplay(start: NavKey, openStation: Int?, openVehicle: Long?, onLocationPromptAnswered: () -> Unit) {
    val backStack = when {
        start == StationList && openVehicle != null -> rememberNavBackStack(StationList, Vehicles, VehicleDetail(openVehicle))
        start == StationList && openStation != null -> rememberNavBackStack(StationList, StationDetail(openStation))
        else -> rememberNavBackStack(start)
    }
    var tab by rememberSaveable { mutableStateOf(if (start == StationList && openVehicle != null) HomeTab.VANS else HomeTab.WAITS) }
    val back: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<StationList> {
                HomeTabs(
                    tab = tab,
                    onSelectTab = { tab = it },
                    onOpenStation = { backStack.add(StationDetail(it)) },
                    onOpenVanSite = { key, date -> backStack.add(VanDetail(key, date.toString())) },
                    onChangeLocation = { backStack.add(LocationPrompt) },
                    onOpenDriveBys = { backStack.add(DriveBys) },
                    onOpenVehicles = { backStack.add(Vehicles) },
                )
            }
            entry<Vehicles> {
                VehiclesScreen(onBack = back, onOpen = { backStack.add(VehicleDetail(it)) }, onAdd = { backStack.add(EditVehicle()) })
            }
            entry<VehicleDetail> { key ->
                VehicleDetailScreen(
                    key.vehicleId,
                    onBack = back,
                    onEdit = { backStack.add(EditVehicle(key.vehicleId)) },
                    onFindVan = {
                        tab = HomeTab.VANS
                        while (backStack.size > 1 && backStack.last() != StationList) backStack.removeAt(backStack.lastIndex)
                    },
                )
            }
            entry<EditVehicle> { key ->
                EditVehicleScreen(
                    key.vehicleId,
                    onClose = back,
                    // The new vehicle's window, in place of the form.
                    onAdded = { id ->
                        backStack.remove(key)
                        backStack.add(VehicleDetail(id))
                    },
                    // Back past the deleted vehicle's own screen too.
                    onDeleted = {
                        backStack.remove(key)
                        backStack.removeAll { it == VehicleDetail(key.vehicleId ?: return@removeAll false) }
                    },
                )
            }
            entry<DriveBys> { DriveByLogScreen(onBack = { backStack.removeLastOrNull() }) }
            entry<VanDetail> { key ->
                VanDetailScreen(key.siteKey, LocalDate.parse(key.date), onBack = { backStack.removeLastOrNull() })
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
