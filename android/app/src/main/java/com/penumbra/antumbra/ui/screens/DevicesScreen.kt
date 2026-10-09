package com.penumbra.antumbra.ui.screens

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.theme.AntumbraColors

@Composable
fun DevicesScreen(vm: AppViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntumbraColors.void)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onBack) {
                Text("←", color = AntumbraColors.amber)
            }
            Text(
                "Devices",
                style = MaterialTheme.typography.headlineMedium,
                color = AntumbraColors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { vm.refreshDevices() }) {
                Text("Refresh", color = AntumbraColors.amber)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (vm.devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AntumbraColors.surface)
                    .border(1.dp, AntumbraColors.outline, RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No MTK device",
                        style = MaterialTheme.typography.titleMedium,
                        color = AntumbraColors.textPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Plug in a MediaTek phone in BROM or Preloader mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AntumbraColors.textSecondary
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.devices) { device ->
                    DeviceCard(device) { vm.requestOrConnect(device) }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: UsbDevice, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AntumbraColors.surface)
            .border(1.dp, AntumbraColors.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Text(
            device.productName ?: device.deviceName,
            style = MaterialTheme.typography.titleMedium,
            color = AntumbraColors.textPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            device.manufacturerName ?: "Unknown manufacturer",
            style = MaterialTheme.typography.bodyMedium,
            color = AntumbraColors.textSecondary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            String.format("VID %04X  ·  PID %04X", device.vendorId, device.productId),
            style = MaterialTheme.typography.labelSmall,
            color = AntumbraColors.amber
        )
    }
}
