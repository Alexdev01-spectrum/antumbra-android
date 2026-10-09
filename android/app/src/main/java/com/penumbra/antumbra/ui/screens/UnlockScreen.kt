package com.penumbra.antumbra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.theme.AntumbraColors

@Composable
fun UnlockScreen(vm: AppViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntumbraColors.void)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = AntumbraColors.amber) }
            Text("Unlock", style = MaterialTheme.typography.headlineMedium, color = AntumbraColors.textPrimary)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Bootloader unlock / relock uses upstream exploit paths (Carbonara, HeapB8, linecode) after DA is up. Available on vulnerable devices only.",
            style = MaterialTheme.typography.bodyLarge,
            color = AntumbraColors.textSecondary
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { vm.log("Unlock: wire Device unlock API after DA session is stable") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.amber,
                contentColor = AntumbraColors.void
            )
        ) { Text("Unlock bootloader", style = MaterialTheme.typography.titleMedium) }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { vm.log("Relock requested") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.surfaceHigh,
                contentColor = AntumbraColors.textPrimary
            )
        ) { Text("Relock", style = MaterialTheme.typography.titleMedium) }
    }
}
