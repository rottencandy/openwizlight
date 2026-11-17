package io.github.rottencandy.openwizlight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableIntStateOf
import io.github.rottencandy.openwizlight.wiz.HueSat
import io.github.rottencandy.openwizlight.wiz.MAX_SPEED
import io.github.rottencandy.openwizlight.wiz.MIN_SPEED
import io.github.rottencandy.openwizlight.wiz.SCENE_NAMES
import io.github.rottencandy.openwizlight.wiz.scenesFor
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
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
    onColorTempChange: (Int) -> Unit,
    onColorChange: (HueSat) -> Unit,
    onSceneChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onReboot: () -> Unit,
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

            ControlsCard(
                device,
                onPowerChange,
                onBrightnessChange,
                onColorTempChange,
                onColorChange,
                onSceneChange,
                onSpeedChange,
            )
        }
    }

    if (showDetails) {
        DeviceDetailsSheet(device, onReboot, onDismiss = { showDetails = false })
    }
}

/**
 * Bottom sheet listing the device's identifying info and raw system config, with a reboot
 * action. Rebooting closes the sheet so any error shows on the device screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailsSheet(device: WizDevice, onReboot: () -> Unit, onDismiss: () -> Unit) {
    var confirmingReboot by rememberSaveable { mutableStateOf(false) }
    if (confirmingReboot) {
        AlertDialog(
            onDismissRequest = { confirmingReboot = false },
            icon = { Icon(Icons.Filled.RestartAlt, contentDescription = null) },
            title = { Text("Reboot ${device.displayName()}?") },
            text = { Text("The device will be unreachable until it reconnects to Wi-Fi.") },
            confirmButton = {
                TextButton(onClick = { confirmingReboot = false; onReboot(); onDismiss() }) { Text("Reboot") }
            },
            dismissButton = { TextButton(onClick = { confirmingReboot = false }) { Text("Cancel") } },
        )
    }
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
            OutlinedButton(
                onClick = { confirmingReboot = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Reboot")
            }
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
    onColorTempChange: (Int) -> Unit,
    onColorChange: (HueSat) -> Unit,
    onSceneChange: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
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
                pilot.dimming?.let { dimming ->
                    ControlSlider(
                        label = { "Brightness ${it ?: dimming}%" },
                        value = dimming,
                        range = (caps?.minDimming ?: MIN_DIMMING)..MAX_DIMMING,
                        onValueChange = onBrightnessChange,
                    )
                }
            }
            val kelvinRange = caps?.kelvinRange
            if (caps?.deviceClass?.colorTemp == true && kelvinRange != null) {
                ControlSlider(
                    // Without a reported temp the light is showing a colour or scene.
                    label = { kelvin -> kelvin?.let { "Colour temperature $it K" } ?: "Colour temperature" },
                    value = pilot.temp,
                    range = kelvinRange.min..kelvinRange.max,
                    step = KELVIN_STEP,
                    onValueChange = onColorTempChange,
                )
            }
            if (caps?.deviceClass?.color == true) {
                ColorSliders(pilot.color, onColorChange)
            }
            val scenes = caps?.takeIf { it.effects }?.let { scenesFor(it.deviceClass) }.orEmpty()
            if (scenes.isNotEmpty()) {
                ScenePicker(pilot.scene, scenes, onSceneChange)
                if (pilot.scene != null) {
                    ControlSlider(
                        label = { speed -> speed?.let { "Speed $it" } ?: "Speed" },
                        value = pilot.speed,
                        range = MIN_SPEED..MAX_SPEED,
                        default = DEFAULT_SPEED,
                        onValueChange = onSpeedChange,
                    )
                }
            }
        }
    }
}

/** Speed slider position when the device doesn't report a speed. */
private const val DEFAULT_SPEED = 100

/**
 * Dropdown of [scenes] (scene IDs). Shows the running [current] scene's name even if it isn't
 * selectable here, e.g. a Rhythm schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenePicker(current: Int?, scenes: List<Int>, onSceneChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = current?.let { SCENE_NAMES[it] ?: "Scene $it" } ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Scene") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            scenes.forEach { id ->
                DropdownMenuItem(
                    text = { Text(SCENE_NAMES.getValue(id)) },
                    onClick = { expanded = false; onSceneChange(id) },
                )
            }
        }
    }
}

/**
 * Hue and saturation sliders. Each sends the full colour, combining its own value with the
 * other slider's; while the light shows no colour, they start from full-saturation red.
 */
@Composable
private fun ColorSliders(color: HueSat?, onColorChange: (HueSat) -> Unit) {
    val current = color ?: HueSat(hue = 0, saturation = 100)
    // Follows the hue thumb while dragging, so the saturation track previews the new hue.
    var liveHue by remember(color) { mutableIntStateOf(current.hue) }
    ControlSlider(
        label = { hue -> hue?.let { "Hue $it°" } ?: "Hue" },
        value = color?.hue,
        default = current.hue,
        range = 0..359,
        onDrag = { liveHue = it },
        onValueChange = { onColorChange(current.copy(hue = it)) },
        trackBrush = HUE_BRUSH,
    )
    ControlSlider(
        label = { saturation -> saturation?.let { "Saturation $it%" } ?: "Saturation" },
        value = color?.saturation,
        default = current.saturation,
        range = 0..100,
        onValueChange = { onColorChange(HueSat(liveHue, it)) },
        trackBrush = Brush.horizontalGradient(listOf(Color.White, Color.hsv(liveHue.toFloat(), 1f, 1f))),
    )
}

/** Full-saturation rainbow, red to red, for the hue slider's track. */
private val HUE_BRUSH = Brush.horizontalGradient((0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) })

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
 * A labelled slider that tracks the drag position locally and only sends the final value, so
 * dragging doesn't flood the device with requests. Resyncs whenever the reported [value]
 * changes. A null [value] (unknown) parks the thumb at [default] until the user drags it.
 *
 * @param label text for the current position, or for null before an unknown value is dragged
 * @param step granularity of the sent value; values are rounded to it and kept within [range]
 * @param onDrag receives the snapped position on every drag movement, for live previews
 * @param trackBrush paints the whole track instead of the default active/inactive colours
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlSlider(
    label: (Int?) -> String,
    value: Int?,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    default: Int = (range.first + range.last) / 2,
    onDrag: (Int) -> Unit = {},
    trackBrush: Brush? = null,
) {
    val snap = { raw: Float -> ((raw / step).roundToInt() * step).coerceIn(range) }
    var position by remember(value) { mutableFloatStateOf((value ?: default).toFloat()) }
    var dragged by remember(value) { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label(if (value != null || dragged) snap(position) else null), style = MaterialTheme.typography.bodyLarge)
        val valueRange = range.first.toFloat()..range.last.toFloat()
        val onSliderChange = { raw: Float -> position = raw; dragged = true; onDrag(snap(raw)) }
        val onFinished = { onValueChange(snap(position)) }
        if (trackBrush == null) {
            Slider(
                value = position,
                onValueChange = onSliderChange,
                onValueChangeFinished = onFinished,
                valueRange = valueRange,
            )
        } else {
            Slider(
                value = position,
                onValueChange = onSliderChange,
                onValueChangeFinished = onFinished,
                valueRange = valueRange,
                track = { GradientTrack(trackBrush) },
            )
        }
    }
}

@Composable
private fun GradientTrack(brush: Brush) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(CircleShape)
            .background(brush),
    )
}

/** Colour temperature slider granularity; finer steps aren't visibly different. */
private const val KELVIN_STEP = 50

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
