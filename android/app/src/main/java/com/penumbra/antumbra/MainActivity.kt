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
import com.penumbra.antumbra.ui.screens.*
import com.penumbra.antumbra.ui.theme.AntumbraColors
import com.penumbra.antumbra.ui.theme.AntumbraTheme

enum class Screen { Home, Devices, Flash, Unlock }

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.register()

        setContent {
            AntumbraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AntumbraColors.void) {
                    var screen by remember { mutableStateOf(Screen.Home) }

                    when (screen) {
                        Screen.Home -> HomeScreen(
                            vm = vm,
                            onNavigateDevices = { screen = Screen.Devices },
                            onNavigateFlash = { screen = Screen.Flash },
                            onNavigateUnlock = { screen = Screen.Unlock },
                        )
                        Screen.Devices -> DevicesScreen(vm = vm, onBack = { screen = Screen.Home })
                        Screen.Flash -> FlashScreen(vm = vm, onBack = { screen = Screen.Home })
                        Screen.Unlock -> UnlockScreen(vm = vm, onBack = { screen = Screen.Home })
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
