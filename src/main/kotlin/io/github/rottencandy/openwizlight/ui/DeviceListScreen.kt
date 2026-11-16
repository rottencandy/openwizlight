package io.github.rottencandy.openwizlight.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rottencandy.openwizlight.wiz.WizDevice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    state: ScanState,
    onScan: () -> Unit,
    onDeviceClick: (WizDevice) -> Unit,
    onSigningKeyChange: (String?) -> Boolean,
    readSigningKey: suspend (Uri) -> Result<String>,
) {
    var editingKey by rememberSaveable { mutableStateOf(false) }
    if (editingKey) {
        SigningKeyDialog(
            current = state.signingKey,
            readSigningKey = readSigningKey,
            onSave = { key -> onSigningKeyChange(key).also { if (it) editingKey = false } },
            onDismiss = { editingKey = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenWiZLight") },
                actions = {
                    TextButton(onClick = { editingKey = true }) { Text("Key") }
                    TextButton(onClick = onScan, enabled = !state.scanning) { Text("Scan") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { StatusText(it, isError = true) }
            if (!state.scanning && state.error == null && state.devices.isEmpty()) {
                StatusText("No devices found")
            }
            LazyColumn {
                items(state.devices, key = { it.mac }) { device ->
                    DeviceRow(device = device, onClick = { onDeviceClick(device) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: WizDevice, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.displayName()) },
        supportingContent = { Text(stateSummary(device)) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Name shown for a device: its model once known, otherwise its MAC. */
internal fun WizDevice.displayName(): String = moduleName ?: mac

/** One-line state, e.g. "On · 80%", "Off" or "Unknown" before the device answers. */
private fun stateSummary(device: WizDevice): String =
    device.pilot
        ?.let { pilot -> if (pilot.on) listOfNotNull("On", pilot.dimming?.let { "$it%" }).joinToString(" · ") else "Off" }
        ?: "Unknown"

/**
 * Edits the home's `udp_signing_key`. [onSave] receives the entered text, or null to clear
 * the key, and returns false if the key was rejected as invalid.
 */
@Composable
private fun SigningKeyDialog(
    current: String?,
    readSigningKey: suspend (Uri) -> Result<String>,
    onSave: (String?) -> Boolean,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Exports may not be labelled application/json by every file provider, so accept any file.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            readSigningKey(uri).fold(
                onSuccess = { text = it; error = null },
                onFailure = { error = it.message },
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signing key") },
        text = {
            Column {
                Text(
                    "Newer firmware only accepts signed commands. Export your home for local " +
                        "integration in the WiZ app, then import that file or paste its " +
                        "udp_signing_key here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    label = { Text("udp_signing_key") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import from file") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onSave(text.ifBlank { null })) error = "Must be 32 hex characters"
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
internal fun StatusText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
