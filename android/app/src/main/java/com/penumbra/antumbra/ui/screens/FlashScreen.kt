package com.penumbra.antumbra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.theme.AntumbraColors

@Composable
fun FlashScreen(vm: AppViewModel, onBack: () -> Unit) {
    var daPicked by remember { mutableStateOf(false) }
    var imagePicked by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AntumbraColors.void)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("\u2190", color = AntumbraColors.amber) }
            Text("Flash", style = MaterialTheme.typography.headlineMedium, color = AntumbraColors.textPrimary)
        }

        Spacer(Modifier.height(20.dp))

        InfoCard(
            "DA file",
            if (daPicked) "Selected" else "Required for full flash",
            if (daPicked) AntumbraColors.success else AntumbraColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                // SAF picker hooks here
                daPicked = true
                vm.log("DA selected (wire SAF in next pass)")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.surfaceHigh,
                contentColor = AntumbraColors.textPrimary
            )
        ) { Text("Choose DA", style = MaterialTheme.typography.labelLarge) }

        Spacer(Modifier.height(16.dp))

        InfoCard(
            "Firmware / scatter",
            if (imagePicked) "Selected" else "Image or scatter file",
            if (imagePicked) AntumbraColors.success else AntumbraColors.textSecondary
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                imagePicked = true
                vm.log("Image selected (wire SAF in next pass)")
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.surfaceHigh,
                contentColor = AntumbraColors.textPrimary
            )
        ) { Text("Choose image", style = MaterialTheme.typography.labelLarge) }

        Spacer(Modifier.height(28.dp))

        if (progress > 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AntumbraColors.amber,
                trackColor = AntumbraColors.outline
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = {
                vm.log("Flash starts when DA upload + partition write JNI lands")
                progress = 0.15f
            },
            enabled = daPicked && imagePicked,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.amber,
                contentColor = AntumbraColors.void,
                disabledContainerColor = AntumbraColors.surface,
                disabledContentColor = AntumbraColors.textSecondary
            )
        ) { Text("Start flash", style = MaterialTheme.typography.titleMedium) }

        Spacer(Modifier.height(16.dp))
        Text(
            "Full flash path: Device::init \u2192 upload DA \u2192 switch to DA mode \u2192 write partitions. JNI for upload/write is next after probe is solid on device.",
            style = MaterialTheme.typography.bodyMedium,
            color = AntumbraColors.textSecondary
        )
    }
}

@Composable
private fun InfoCard(title: String, subtitle: String, subtitleColor: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AntumbraColors.surface)
            .border(1.dp, AntumbraColors.outline, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AntumbraColors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = subtitleColor)
    }
}
