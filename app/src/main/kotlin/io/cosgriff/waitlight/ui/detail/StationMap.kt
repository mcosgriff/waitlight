package io.cosgriff.waitlight.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Badge
import io.cosgriff.waitlight.domain.Station
import io.cosgriff.waitlight.ui.components.colors
import io.cosgriff.waitlight.ui.theme.waitColors

/** The map's height, from the design; the sheet below covers its bottom [SHEET_OVERLAP]. */
internal val MAP_HEIGHT = 230.dp
internal val SHEET_OVERLAP = 28.dp

private val PIN_SIZE = 42.dp
private val PIN_ICON = 20.dp

/** How far the pin's tip is below its centre: half the square's diagonal, less what its 4dp corner rounds off. */
private val PIN_TIP = 28.dp

private const val OSM_COPYRIGHT = "https://www.openstreetmap.org/copyright"

/**
 * The station's bundled map (ADR-0006), centred on the station, with a pin at the centre in the badge's colour
 * holding a car (the test station), and the OpenStreetMap credit above the part [bottomInset] hides. The wait is in text below, so the map only
 * says where it is. A station without an image (none, while every station has one) gets a plain block.
 */
@Composable
fun StationMap(station: Station, badge: Badge, bottomInset: Dp, modifier: Modifier = Modifier) {
    val image = stationMap(station.id)
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        return
    }
    val description = stringResource(R.string.detail_map, station.name)
    Box(modifier) {
        Box(Modifier.matchParentSize().clearAndSetSemantics { contentDescription = description; role = Role.Image }) {
            Image(
                painterResource(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            val pin = badge.colors(MaterialTheme.waitColors)
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = -PIN_TIP)
                    .size(PIN_SIZE)
                    .rotate(-45f)
                    .background(pin.container, PIN_SHAPE)
                    .border(3.dp, MaterialTheme.colorScheme.surface, PIN_SHAPE),
                contentAlignment = Alignment.Center,
            ) {
                // Turned back upright inside the turned teardrop.
                Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = pin.content, modifier = Modifier.rotate(45f).size(PIN_ICON))
            }
        }
        // A small credit, but a 48dp place to tap it (the whole corner above the sheet).
        val uriHandler = LocalUriHandler.current
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomInset)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button) { uriHandler.openUri(OSM_COPYRIGHT) }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.detail_map_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** A teardrop: a square with three round corners, turned so the sharp one points down. */
private val PIN_SHAPE = RoundedCornerShape(topStart = 21.dp, topEnd = 21.dp, bottomEnd = 21.dp, bottomStart = 4.dp)
