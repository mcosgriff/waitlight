package io.cosgriff.waitlight.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.waitlight.R
import io.cosgriff.waitlight.domain.Stations

/** Asks for approximate location or a ZIP code; either answer, or skipping, goes on to the list. */
@Composable
fun LocationPromptScreen(onDone: () -> Unit) {
    val viewModel: LocationPromptViewModel = hiltViewModel()
    val zip by viewModel.ui.collectAsStateWithLifecycle()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    LocationPromptContent(
        zip = zip,
        onUseLocation = { request.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onZipChange = viewModel::onZipChange,
        onGo = { viewModel.go(onSaved = onDone) },
        onClearZip = viewModel::clearZip,
        onSkip = onDone,
    )
}

/** `design/png/Permission.png`: location, or a ZIP code and **Go**. A saved ZIP can be cleared here. */
@Composable
fun LocationPromptContent(
    zip: ZipFieldUi,
    onUseLocation: () -> Unit,
    onZipChange: (String) -> Unit,
    onGo: () -> Unit,
    onClearZip: () -> Unit,
    onSkip: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.location_title),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.location_body),
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
            color = muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onUseLocation, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.location_use), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Or()
        Spacer(Modifier.height(12.dp))
        ZipField(zip, onZipChange, onGo)
        if (zip.saved != null) {
            TextButton(onClick = onClearZip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.location_zip_clear, zip.saved), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.location_skip), fontSize = 15.sp)
        }
        Text(
            pluralStringResource(R.plurals.location_skip_note, Stations.all.size, Stations.all.size),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

/** "or", between two rules. */
@Composable
private fun Or() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        Text(stringResource(R.string.location_or), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun ZipField(zip: ZipFieldUi, onZipChange: (String) -> Unit, onGo: () -> Unit) {
    val error = when (zip.error) {
        null -> null
        ZipError.INCOMPLETE -> stringResource(R.string.location_zip_incomplete)
        ZipError.UNKNOWN -> stringResource(R.string.location_zip_unknown, zip.text)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = zip.text,
            onValueChange = onZipChange,
            label = { Text(stringResource(R.string.location_zip_label)) },
            placeholder = { Text(stringResource(R.string.location_zip_hint)) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onGo,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 20.dp),
            // Level with the field's box, below its floating label.
            modifier = Modifier.padding(top = 8.dp).height(56.dp),
        ) {
            Text(stringResource(R.string.location_zip_go), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
