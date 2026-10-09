package com.penumbra.antumbra.ui

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.penumbra.antumbra.Native
import com.penumbra.antumbra.OpenedDevice
import com.penumbra.antumbra.UsbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConnState { Disconnected, Connecting, Connected, Error }

data class LogLine(val text: String, val level: Level = Level.Info) {
    enum class Level { Info, Warn, Error, Success }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val usb = UsbHelper(app)

    var devices = mutableStateListOf<UsbDevice>()
        private set

    var connState by mutableStateOf(ConnState.Disconnected)
        private set

    var handle by mutableLongStateOf(-1L)
        private set

    var portName by mutableStateOf<String?>(null)
        private set

    var connectionTypeLabel by mutableStateOf("\u2014")
        private set

    var hwCodeLabel by mutableStateOf("\u2014")
        private set

    var statusMessage by mutableStateOf("Waiting for device")
        private set

    var flashProgress by mutableFloatStateOf(0f)
        private set

    var daBytes: ByteArray? = null
    var imageBytes: ByteArray? = null

    val logs = mutableStateListOf<LogLine>()

    private var opened: OpenedDevice? = null

    init {
        usb.onPermissionResult = { device, granted ->
            if (granted) connect(device)
            else {
                statusMessage = "Permission denied"
                connState = ConnState.Error
                log("Permission denied", LogLine.Level.Error)
            }
        }
        usb.onDeviceAttached = { device ->
            log("USB attached: ${device.productName ?: device.deviceName}")
            refreshDevices()
            // Auto-reconnect path for re-enum style transitions
            if (connState == ConnState.Error || connState == ConnState.Disconnected) {
                if (usb.hasPermission(device)) connect(device)
            }
        }
        refreshDevices()
    }

    fun register() = usb.register()
    fun unregister() = usb.unregister()

    fun refreshDevices() {
        devices.clear()
        devices.addAll(usb.listMtkDevices())
        if (devices.isEmpty() && connState == ConnState.Disconnected) {
            statusMessage = "No MTK device"
        }
    }

    fun requestOrConnect(device: UsbDevice) {
        if (usb.hasPermission(device)) connect(device)
        else {
            statusMessage = "Requesting permission\u2026"
            usb.requestPermission(device)
        }
    }

    private fun connect(device: UsbDevice) {
        connState = ConnState.Connecting
        statusMessage = "Opening\u2026"
        log("Opening ${device.productName ?: device.deviceName}")

        viewModelScope.launch {
            val od = withContext(Dispatchers.IO) { usb.openAndClaim(device) }
            if (od == null) {
                connState = ConnState.Error
                statusMessage = "Open failed"
                log("openAndClaim failed", LogLine.Level.Error)
                return@launch
            }
            opened = od
            handle = od.handle
            portName = Native.getPortName(handle)
            connectionTypeLabel = when (Native.getConnectionType(handle)) {
                0 -> "BROM"
                1 -> "Preloader"
                2 -> "DA"
                else -> "Unknown"
            }
            log("USB open: $portName ($connectionTypeLabel)")
            statusMessage = "Handshake\u2026"

            val r = withContext(Dispatchers.IO) { Native.probe(handle) }
            if (r == 0) {
                val hw = Native.getHwCode(handle)
                hwCodeLabel = if (hw != 0) String.format("0x%04X", hw) else "\u2014"
                connState = ConnState.Connected
                statusMessage = "Connected \u00b7 $connectionTypeLabel"
                log("Handshake OK  hw=$hwCodeLabel", LogLine.Level.Success)
            } else {
                val err = Native.getLastError(handle) ?: "code $r"
                connState = ConnState.Error
                statusMessage = "Handshake failed"
                log("Handshake failed: $err", LogLine.Level.Error)
            }
        }
    }

    fun disconnect() {
        if (handle >= 0) Native.closeDevice(handle)
        opened?.connection?.close()
        opened = null
        handle = -1
        portName = null
        connectionTypeLabel = "\u2014"
        hwCodeLabel = "\u2014"
        flashProgress = 0f
        connState = ConnState.Disconnected
        statusMessage = "Disconnected"
        refreshDevices()
        log("Disconnected")
    }

    fun setDa(bytes: ByteArray) {
        daBytes = bytes
        log("DA loaded (${bytes.size} bytes)", LogLine.Level.Success)
    }

    fun setImage(bytes: ByteArray) {
        imageBytes = bytes
        log("Image loaded (${bytes.size} bytes)", LogLine.Level.Success)
    }

    fun startFlash() {
        val da = daBytes
        val img = imageBytes
        if (da == null || img == null || handle < 0) {
            log("Need DA + image + connection", LogLine.Level.Error)
            return
        }
        viewModelScope.launch {
            statusMessage = "Flashing\u2026"
            flashProgress = 0.05f
            val r = withContext(Dispatchers.IO) { Native.flash(handle, da, img) }
            flashProgress = Native.getProgress(handle) / 100f
            if (r == 0) {
                log("Flash OK", LogLine.Level.Success)
                statusMessage = "Flash complete"
                flashProgress = 1f
            } else {
                val err = Native.getLastError(handle) ?: "code $r"
                log("Flash: $err", LogLine.Level.Warn)
                statusMessage = "Flash pending full Device path"
            }
        }
    }

    fun startUnlock() {
        if (handle < 0) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Native.unlock(handle) }
            val err = Native.getLastError(handle)
            if (r == 0) log("Unlock OK", LogLine.Level.Success)
            else log("Unlock: ${err ?: r}", LogLine.Level.Warn)
        }
    }

    fun startRelock() {
        if (handle < 0) return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { Native.relock(handle) }
            log(if (r == 0) "Relock OK" else "Relock: ${Native.getLastError(handle)}",
                if (r == 0) LogLine.Level.Success else LogLine.Level.Warn)
        }
    }

    fun exportLog(): File? {
        return try {
            val dir = getApplication<Application>().getExternalFilesDir(null) ?: return null
            val f = File(dir, "antumbra-${System.currentTimeMillis()}.log")
            f.writeText(logs.reversed().joinToString("\n") { "${it.level}: ${it.text}" })
            log("Log exported: ${f.absolutePath}", LogLine.Level.Success)
            f
        } catch (e: Exception) {
            log("Export failed: ${e.message}", LogLine.Level.Error)
            null
        }
    }

    fun log(text: String, level: LogLine.Level = LogLine.Level.Info) {
        logs.add(0, LogLine(text, level))
        if (logs.size > 300) logs.removeAt(logs.lastIndex)
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
