package com.penumbra.antumbra.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.penumbra.antumbra.ui.AppViewModel
import com.penumbra.antumbra.ui.theme.AntumbraColors

@Composable
fun FlashScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var daName by remember { mutableStateOf<String?>(null) }
    var imageName by remember { mutableStateOf<String?>(null) }

    val daPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input ->
            vm.setDa(input.readBytes())
            daName = uri.lastPathSegment
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input ->
            vm.setImage(input.readBytes())
            imageName = uri.lastPathSegment
        }
    }

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

        FileCard("DA file", daName ?: "Required") {
            daPicker.launch(arrayOf("*/*"))
        }
        Spacer(Modifier.height(12.dp))
        FileCard("Firmware / scatter", imageName ?: "Image or scatter") {
            imagePicker.launch(arrayOf("*/*"))
        }

        Spacer(Modifier.height(24.dp))

        if (vm.flashProgress > 0f) {
            LinearProgressIndicator(
                progress = { vm.flashProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = AntumbraColors.amber,
                trackColor = AntumbraColors.outline
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            onClick = { vm.startFlash() },
            enabled = daName != null && imageName != null,
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
            "Files are read via SAF into memory, then passed to native flash(). Full DA upload + partition write completes when Device session path is finalized on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = AntumbraColors.textSecondary
        )
    }
}

@Composable
private fun FileCard(title: String, subtitle: String, onPick: () -> Unit) {
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
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AntumbraColors.textSecondary)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onPick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AntumbraColors.surfaceHigh,
                contentColor = AntumbraColors.textPrimary
            )
        ) { Text("Choose", style = MaterialTheme.typography.labelLarge) }
    }
}
