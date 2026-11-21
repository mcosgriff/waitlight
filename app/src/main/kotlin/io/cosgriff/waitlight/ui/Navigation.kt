package io.cosgriff.waitlight.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The home screen: the station list, nearest first, and the roadside vans, under a bottom navigation bar. */
@Serializable
data object StationList : NavKey

/** One van site on one day, by its site key (`VanSite.key`) and the day as an ISO date. */
@Serializable
data class VanDetail(val siteKey: String, val date: String) : NavKey

/** One station, by AirCare's id for it. */
@Serializable
data class StationDetail(val stationId: Int) : NavKey

/** Asks for location or a ZIP code, before the list on first launch or from the list's "Location off" or ZIP. */
@Serializable
data object LocationPrompt : NavKey

/** The user's drive-by log. */
@Serializable
data object DriveBys : NavKey
