package io.github.rottencandy.openwizlight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.rottencandy.openwizlight.wiz.WizDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(state: ScanState, onScan: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenWiZLight") },
                actions = {
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
                items(state.devices, key = { it.mac }) { DeviceRow(it) }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: WizDevice) {
    ListItem(
        headlineContent = { Text(device.moduleName ?: device.mac) },
        supportingContent = {
            Text(
                listOfNotNull(device.ip, device.mac, device.fwVersion?.let { "fw $it" })
                    .joinToString(" · "),
            )
        },
    )
}

@Composable
private fun StatusText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
