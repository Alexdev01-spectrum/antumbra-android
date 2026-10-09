package com.penumbra.antumbra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.screens.DevicesScreen
import com.penumbra.antumbra.ui.screens.HomeScreen
import com.penumbra.antumbra.ui.theme.AntumbraColors
import com.penumbra.antumbra.ui.theme.AntumbraTheme

enum class Screen { Home, Devices }

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.register()

        setContent {
            AntumbraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AntumbraColors.void
                ) {
                    var screen by remember { mutableStateOf(Screen.Home) }

                    when (screen) {
                        Screen.Home -> HomeScreen(
                            vm = vm,
                            onNavigateDevices = { screen = Screen.Devices }
                        )
                        Screen.Devices -> DevicesScreen(
                            vm = vm,
                            onBack = { screen = Screen.Home }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        vm.unregister()
        super.onDestroy()
    }
}
