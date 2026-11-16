package io.github.rottencandy.openwizlight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rottencandy.openwizlight.ui.DeviceListScreen
import io.github.rottencandy.openwizlight.ui.DeviceListViewModel
import io.github.rottencandy.openwizlight.ui.theme.OpenWizLightTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DeviceListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenWizLightTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DeviceListScreen(state = state, onScan = viewModel::scan)
            }
        }
    }
}
