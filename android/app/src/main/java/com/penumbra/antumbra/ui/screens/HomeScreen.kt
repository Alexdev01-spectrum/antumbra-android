package com.penumbra.antumbra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.ConnState
import com.penumbra.antumbra.ui.LogLine
import com.penumbra.antumbra.ui.theme.AntumbraColors

@Composable
fun HomeScreen(vm: AppViewModel, onNavigateDevices: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntumbraColors.void)
            .padding(20.dp)
    ) {
        // header
        Text(
            "ANTUMBRA",
            style = MaterialTheme.typography.displayLarge,
            color = AntumbraColors.textPrimary
        )
        Text(
            "MediaTek flash tool",
            style = MaterialTheme.typography.bodyMedium,
            color = AntumbraColors.textSecondary
        )

        Spacer(Modifier.height(28.dp))

        // status card
        StatusCard(vm)

        Spacer(Modifier.height(20.dp))

        // quick actions
        Text(
            "ACTIONS",
            style = MaterialTheme.typography.labelLarge,
            color = AntumbraColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip("Devices", onNavigateDevices, Modifier.weight(1f))
            ActionChip(
                if (vm.connState == ConnState.Connected) "Disconnect" else "Connect",
                {
                    if (vm.connState == ConnState.Connected) vm.disconnect()
                    else onNavigateDevices()
                },
                Modifier.weight(1f),
                accent = vm.connState == ConnState.Connected
            )
        }

        Spacer(Modifier.height(24.dp))

        // live log
        Text(
            "LOG",
            style = MaterialTheme.typography.labelLarge,
            color = AntumbraColors.textSecondary
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(AntumbraColors.surface)
                .border(1.dp, AntumbraColors.outline, RoundedCornerShape(12.dp))
                .padding(12.dp),
            reverseLayout = false
        ) {
            items(vm.logs) { line ->
                LogRow(line)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun StatusCard(vm: AppViewModel) {
    val dotColor = when (vm.connState) {
        ConnState.Connected -> AntumbraColors.success
        ConnState.Connecting -> AntumbraColors.amber
        ConnState.Error -> AntumbraColors.danger
        ConnState.Disconnected -> AntumbraColors.textSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AntumbraColors.surface)
            .border(1.dp, AntumbraColors.outline, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                vm.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                color = AntumbraColors.textPrimary
            )
        }

        if (vm.connState == ConnState.Connected) {
            Spacer(Modifier.height(12.dp))
            Divider(color = AntumbraColors.outline)
            Spacer(Modifier.height(12.dp))
            MetaRow("Port", vm.portName ?: "—")
            MetaRow("Mode", vm.connectionTypeLabel)
            MetaRow("Handle", vm.handle.toString())
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AntumbraColors.textSecondary)
        Text(value, style = MaterialTheme.typography.labelSmall, color = AntumbraColors.textPrimary)
    }
}

@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) AntumbraColors.amber else AntumbraColors.surfaceHigh,
            contentColor = if (accent) AntumbraColors.void else AntumbraColors.textPrimary
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val color = when (line.level) {
        LogLine.Level.Success -> AntumbraColors.success
        LogLine.Level.Warn -> AntumbraColors.amber
        LogLine.Level.Error -> AntumbraColors.danger
        LogLine.Level.Info -> AntumbraColors.textSecondary
    }
    Text(
        line.text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}
