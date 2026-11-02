package io.cosgriff.waitlight.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The station list, nearest first. */
@Serializable
data object StationList : NavKey

/** One station, by AirCare's id for it. */
@Serializable
data class StationDetail(val stationId: Int) : NavKey
