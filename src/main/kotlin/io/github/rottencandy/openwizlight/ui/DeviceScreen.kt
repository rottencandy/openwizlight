package io.github.rottencandy.openwizlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.rottencandy.openwizlight.wiz.MAX_DIMMING
import io.github.rottencandy.openwizlight.wiz.MIN_DIMMING
import io.github.rottencandy.openwizlight.wiz.WizCapabilities
import io.github.rottencandy.openwizlight.wiz.WizDevice
import kotlin.math.roundToInt

/** Config fields already shown in their own rows, so they're left out of the raw listing. */
private val SHOWN_CONFIG_FIELDS = setOf("mac", "moduleName", "fwVersion")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    device: WizDevice,
    error: String?,
    onBack: () -> Unit,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.displayName()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showDetails = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "Device details")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            error?.let { StatusText(it, isError = true) }

            ControlsCard(device, onPowerChange, onBrightnessChange)
        }
    }

    if (showDetails) {
        DeviceDetailsSheet(device, onDismiss = { showDetails = false })
    }
}

/** Bottom sheet listing the device's identifying info and raw system config. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailsSheet(device: WizDevice, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader("Device details")
            InfoRow("Model", device.moduleName)
            InfoRow("Firmware", device.fwVersion)
            device.capabilities?.let { caps ->
                InfoRow("Type", caps.deviceClass.label)
                InfoRow("Supports", featureSummary(caps))
                InfoRow("Colour temperature", caps.kelvinRange?.let { "${it.min}–${it.max} K" })
                InfoRow("Minimum brightness", "${caps.minDimming}%")
            }
            InfoRow("IP address", device.ip)
            InfoRow("MAC address", device.mac)
            device.systemConfig
                .filterKeys { it !in SHOWN_CONFIG_FIELDS }
                .toSortedMap()
                .forEach { (name, value) -> InfoRow(name, value) }
        }
    }
}

/** Groups the interactive controls on a filled card, setting them apart from the info list. */
@Composable
private fun ControlsCard(
    device: WizDevice,
    onPowerChange: (Boolean) -> Unit,
    onBrightnessChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        val pilot = device.pilot
        if (pilot == null) {
            StatusText("State unavailable; the device didn't answer")
        } else {
            ListItem(
                headlineContent = { Text("Power") },
                trailingContent = { Switch(checked = pilot.on, onCheckedChange = onPowerChange) },
                // Let the card's colour show through instead of the list item's surface.
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            val caps = device.capabilities
            if (caps?.deviceClass?.brightness != false) {
                pilot.dimming?.let { BrightnessSlider(it, caps?.minDimming ?: MIN_DIMMING, onBrightnessChange) }
            }
        }
    }
}

/** Comma-separated list of the controls a device supports, e.g. "Brightness, colour, effects". */
private fun featureSummary(caps: WizCapabilities): String =
    listOfNotNull(
        "Brightness".takeIf { caps.deviceClass.brightness },
        "Colour temperature".takeIf { caps.deviceClass.colorTemp },
        "Colour".takeIf { caps.deviceClass.color },
        "Effects".takeIf { caps.effects },
        "Dual head".takeIf { caps.dualHead },
        "Fan".takeIf { caps.deviceClass.fan },
    ).ifEmpty { listOf("On/off only") }
        .joinToString(", ")
        .lowercase()
        .replaceFirstChar(Char::uppercase)

/**
 * Tracks the drag position locally and only sends the final value, so dragging doesn't
 * flood the device with requests. Resyncs whenever the device's reported [dimming] changes.
 */
@Composable
private fun BrightnessSlider(dimming: Int, minDimming: Int, onBrightnessChange: (Int) -> Unit) {
    var value by remember(dimming) { mutableFloatStateOf(dimming.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Brightness ${value.roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onBrightnessChange(value.roundToInt()) },
            valueRange = minDimming.toFloat()..MAX_DIMMING.toFloat(),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

/** A label/value row; omitted while the value is still unknown. */
@Composable
private fun InfoRow(label: String, value: String?) {
    value ?: return
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
    )
}
