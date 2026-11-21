package io.cosgriff.waitlight.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.AirCareHours
import io.cosgriff.waitlight.ui.TimeText
import java.time.Instant

/** "Fri, Nov 20, 7:15 AM": a drive-by's moment, in Denver; with the year when it isn't this year's. */
@Composable
fun driveByWhen(at: Instant, now: Instant): String {
    val date = at.atZone(AirCareHours.zone).toLocalDate()
    val day = if (date.year == now.atZone(AirCareHours.zone).year) TimeText.shortDate(date) else TimeText.shortDateWithYear(date)
    return stringResource(R.string.driveby_when, day, TimeText.time(at))
}
