package io.github.rottencandy.openwizlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import io.github.rottencandy.openwizlight.ui.DeviceListScreen
import io.github.rottencandy.openwizlight.ui.DeviceListViewModel
import io.github.rottencandy.openwizlight.ui.DeviceScreen
import io.github.rottencandy.openwizlight.ui.theme.OpenWizLightTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Push updates only while visible; stopping lets device registrations lapse.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.receivePushUpdates() }
        }
        setContent {
            OpenWizLightTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Two screens don't warrant a navigation library: the open device's MAC is the back stack.
                var selectedMac by rememberSaveable { mutableStateOf<String?>(null) }
                // Falls back to the list if the device vanishes, e.g. after a rescan.
                val selected = state.devices.find { it.mac == selectedMac }

                if (selected == null) {
                    DeviceListScreen(
                        state = state,
                        onScan = viewModel::scan,
                        onDeviceClick = { selectedMac = it.mac },
                        onSigningKeyChange = viewModel::setSigningKey,
                        readSigningKey = viewModel::readSigningKey,
                    )
                } else {
                    BackHandler { selectedMac = null }
                    LaunchedEffect(selected.mac) { viewModel.refresh(selected) }
                    DeviceScreen(
                        device = selected,
                        error = state.error,
                        onBack = { selectedMac = null },
                        onPowerChange = { viewModel.setPower(selected, it) },
                        onBrightnessChange = { viewModel.setBrightness(selected, it) },
                        onColorTempChange = { viewModel.setColorTemp(selected, it) },
                        onColorChange = { viewModel.setColor(selected, it) },
                        onSceneChange = { viewModel.setScene(selected, it) },
                        onSpeedChange = { viewModel.setSpeed(selected, it) },
                        onReboot = { viewModel.reboot(selected) },
                    )
                }
            }
        }
    }
}
