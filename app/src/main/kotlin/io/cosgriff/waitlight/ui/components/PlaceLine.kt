package io.cosgriff.waitlight.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.PlaceLine

/**
 * Where "here" is, as [io.cosgriff.waitlight.domain.placeLine] decides it, on both tabs: a typed ZIP code's place
 * and "Location off" open the location prompt, to change or clear the ZIP or turn location on. Drawn inside the
 * header's row.
 */
@Composable
fun PlaceLine(line: PlaceLine, onChangeLocation: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (line) {
        is PlaceLine.Zip -> PromptLink(
            Icons.Outlined.LocationOn,
            stringResource(R.string.list_place, line.place.place, line.place.zip),
            stringResource(R.string.list_change_zip),
            onChangeLocation,
        )
        PlaceLine.LocationOff -> PromptLink(
            Icons.Outlined.LocationOff,
            stringResource(R.string.list_location_off),
            stringResource(R.string.list_location_turn_on),
            onChangeLocation,
        )
        is PlaceLine.Device, PlaceLine.NearYou, PlaceLine.Finding -> {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = muted, modifier = Modifier.size(16.dp))
            val text = when (line) {
                is PlaceLine.Device -> stringResource(R.string.list_place, line.place.place, line.place.zip)
                PlaceLine.NearYou -> stringResource(R.string.list_near_you)
                else -> stringResource(R.string.list_finding_location)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = muted)
        }
    }
}

/** A tappable place line, in the primary colour, that opens the location prompt. */
@Composable
private fun PromptLink(icon: ImageVector, text: String, clickLabel: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(min = 48.dp).clickable(onClickLabel = clickLabel, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}
