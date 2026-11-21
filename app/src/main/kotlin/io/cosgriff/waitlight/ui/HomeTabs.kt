package io.cosgriff.waitlight.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.ui.list.StationListScreen
import io.cosgriff.waitlight.ui.theme.waitColors
import io.cosgriff.waitlight.ui.vans.VansScreen
import java.time.LocalDate

/** The bottom bar's tabs. */
enum class HomeTab { WAITS, VANS }

/**
 * The home screen, as drawn in `design/vans/source/EntryTabs.dc.html`: the wait list or the roadside vans, with a
 * Material 3 navigation bar under them. The vans tab's badge counts the vans reading cars now.
 */
@Composable
fun HomeTabs(onOpenStation: (Int) -> Unit, onOpenVanSite: (String, LocalDate) -> Unit, onChangeLocation: () -> Unit, onOpenDriveBys: () -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val reading by viewModel.readingNow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(HomeTab.WAITS) }
    HomeTabsContent(tab, reading, onSelect = { tab = it }) {
        when (tab) {
            HomeTab.WAITS -> StationListScreen(onOpenStation = onOpenStation, onChangeLocation = onChangeLocation)
            HomeTab.VANS -> VansScreen(onOpenSite = onOpenVanSite, onChangeLocation = onChangeLocation, onOpenDriveBys = onOpenDriveBys)
        }
    }
}

@Composable
fun HomeTabsContent(tab: HomeTab, readingNow: Int, onSelect: (HomeTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        // The screens pad for the status bar themselves; the bar takes the navigation bar's inset.
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(
                    selected = tab == HomeTab.WAITS,
                    onClick = { onSelect(HomeTab.WAITS) },
                    icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_waits)) },
                    colors = colors,
                )
                val readingLabel = pluralStringResource(R.plurals.nav_vans_reading, readingNow, readingNow)
                NavigationBarItem(
                    selected = tab == HomeTab.VANS,
                    onClick = { onSelect(HomeTab.VANS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (readingNow > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.waitColors.short.container,
                                        contentColor = MaterialTheme.waitColors.short.content,
                                        modifier = Modifier.semantics { contentDescription = readingLabel },
                                    ) { Text(readingNow.toString()) }
                                }
                            },
                        ) { Icon(Icons.Rounded.LocalShipping, contentDescription = null) }
                    },
                    label = { Text(stringResource(R.string.nav_vans)) },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) { content() }
    }
}
