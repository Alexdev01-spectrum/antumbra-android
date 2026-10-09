package com.penumbra.antumbra

import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var usbHelper: UsbHelper
    private var nativeHandle by mutableLongStateOf(-1L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usbHelper = UsbHelper(this)
        usbHelper.register()
        usbHelper.onPermissionResult = { device, granted ->
            if (granted) {
                openDevice(device)
            } else {
                Log.w(TAG, "permission denied for ${device.deviceName}")
            }
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DeviceScreen(
                        devices = remember { mutableStateListOf<UsbDevice>() }.also { list ->
                            list.addAll(usbHelper.listMtkDevices())
                        },
                        handle = nativeHandle,
                        onRefresh = {
                            // recompose trigger — real code would use a ViewModel
                        },
                        onSelect = { device ->
                            if (usbHelper.hasPermission(device)) {
                                openDevice(device)
                            } else {
                                usbHelper.requestPermission(device)
                            }
                        },
                        onSmokeTest = {
                            if (nativeHandle >= 0) {
                                val r = Native.smokeTest(nativeHandle)
                                Log.i(TAG, "smokeTest result=$r")
                            }
                        },
                        onClose = {
                            if (nativeHandle >= 0) {
                                Native.closeDevice(nativeHandle)
                                nativeHandle = -1
                            }
                        }
                    )
                }
            }
        }
    }

    private fun openDevice(device: UsbDevice) {
        val h = usbHelper.openAndClaim(device)
        if (h != null) {
            nativeHandle = h
            Log.i(TAG, "opened handle=$h")
        }
    }

    override fun onDestroy() {
        usbHelper.unregister()
        if (nativeHandle >= 0) {
            Native.closeDevice(nativeHandle)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun DeviceScreen(
    devices: List<UsbDevice>,
    handle: Long,
    onRefresh: () -> Unit,
    onSelect: (UsbDevice) -> Unit,
    onSmokeTest: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Antumbra", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (handle >= 0) "Connected (handle $handle)" else "No device open",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        Row {
            Button(onClick = onRefresh) { Text("Refresh") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSmokeTest, enabled = handle >= 0) { Text("Smoke test") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onClose, enabled = handle >= 0) { Text("Close") }
        }

        Spacer(Modifier.height(16.dp))
        Text("MTK devices", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(devices) { device ->
                Card(
                    onClick = { onSelect(device) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${device.manufacturerName ?: "?"} ${device.productName ?: ""}")
                        Text(
                            "VID=${String.format("%04X", device.vendorId)} " +
                            "PID=${String.format("%04X", device.productId)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
