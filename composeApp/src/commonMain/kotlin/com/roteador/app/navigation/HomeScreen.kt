package com.roteador.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.roteador.core.designsystem.RoteadorIcons
import com.roteador.feature.dashboard.DashboardScreen
import com.roteador.feature.devices.DevicesScreen
import com.roteador.feature.settings.SettingsScreen
import com.roteador.feature.signal.SignalScreen
import com.roteador.feature.wifi.WifiScreen

/** Bottom-bar tabs. Adding one = a feature module + an entry here. */
enum class Tab(val label: String, val title: String) {
    Dashboard("Início", "Visão geral"),
    Devices("Aparelhos", "Aparelhos conectados"),
    Wifi("Wi-Fi", "Redes Wi-Fi"),
    Signal("Sinal", "Sinal do celular"),
    Settings("Ajustes", "Ajustes"),
    ;

    val icon: ImageVector
        get() = when (this) {
            Dashboard -> Icons.Filled.Home
            Devices -> RoteadorIcons.Devices
            Wifi -> RoteadorIcons.Wifi
            Signal -> RoteadorIcons.SignalBars
            Settings -> Icons.Filled.Settings
        }
}

/**
 * Tabs are swapped in place (no nested NavHost): each tab's ViewModel lives as
 * long as this screen, and its polling stops while the tab is not shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLoggedOut: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(tab.title) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.Dashboard -> DashboardScreen(content)
            Tab.Devices -> DevicesScreen(content)
            Tab.Wifi -> WifiScreen(content)
            Tab.Signal -> SignalScreen(content)
            Tab.Settings -> SettingsScreen(onLoggedOut = onLoggedOut, modifier = content)
        }
    }
}
