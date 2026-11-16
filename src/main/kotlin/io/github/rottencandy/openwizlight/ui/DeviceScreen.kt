package io.github.rottencandy.openwizlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.github.rottencandy.openwizlight.wiz.MAX_DIMMING
import io.github.rottencandy.openwizlight.wiz.MIN_DIMMING
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.displayName()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(BackArrow, contentDescription = "Back") }
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

            SectionHeader("Controls")
            val pilot = device.pilot
            if (pilot == null) {
                StatusText("State unavailable; the device didn't answer")
            } else {
                ListItem(
                    headlineContent = { Text("Power") },
                    trailingContent = { Switch(checked = pilot.on, onCheckedChange = onPowerChange) },
                )
                pilot.dimming?.let { BrightnessSlider(it, onBrightnessChange) }
            }

            SectionHeader("Device")
            InfoRow("Model", device.moduleName)
            InfoRow("Firmware", device.fwVersion)
            InfoRow("IP address", device.ip)
            InfoRow("MAC address", device.mac)
            device.systemConfig
                .filterKeys { it !in SHOWN_CONFIG_FIELDS }
                .toSortedMap()
                .forEach { (name, value) -> InfoRow(name, value) }
        }
    }
}

/**
 * Tracks the drag position locally and only sends the final value, so dragging doesn't
 * flood the device with requests. Resyncs whenever the device's reported [dimming] changes.
 */
@Composable
private fun BrightnessSlider(dimming: Int, onBrightnessChange: (Int) -> Unit) {
    var value by remember(dimming) { mutableFloatStateOf(dimming.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Brightness ${value.roundToInt()}%", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onBrightnessChange(value.roundToInt()) },
            valueRange = MIN_DIMMING.toFloat()..MAX_DIMMING.toFloat(),
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
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

/** Material "arrow_back", inlined to avoid depending on the icons library for one icon. */
private val BackArrow: ImageVector =
    ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f, autoMirror = true)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
        .build()
