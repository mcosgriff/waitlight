package io.cosgriff.waitlight.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.VanRowStatus
import io.cosgriff.waitlight.domain.VanStatus
import io.cosgriff.waitlight.ui.TimeText

/**
 * A row's status ([io.cosgriff.waitlight.domain.vanRowStatus]) as it reads, and as it's heard: "Scheduled today"
 * when the page can't be read, "Scheduled" heard as "Scheduled Monday" for another day.
 */
@Composable
fun rowStatusText(status: VanRowStatus, spoken: Boolean = false): String = when (status) {
    is VanRowStatus.Plain -> vanStatusText(status.status)
    VanRowStatus.ScheduledToday -> stringResource(R.string.van_status_scheduled_today)
    is VanRowStatus.ScheduledOn ->
        if (spoken) stringResource(R.string.van_status_scheduled_on, TimeText.weekday(status.date)) else vanStatusText(VanStatus.Scheduled)
}
