package io.cosgriff.waitlight.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The station list, nearest first. */
@Serializable
data object StationList : NavKey

/** One station, by AirCare's id for it. */
@Serializable
data class StationDetail(val stationId: Int) : NavKey

/** Asks for location, before the list on first launch or from the list's "Location off". */
@Serializable
data object LocationPrompt : NavKey
