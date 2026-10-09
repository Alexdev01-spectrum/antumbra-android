package com.penumbra.antumbra.ui

import android.app.Application
import android.hardware.usb.UsbDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.penumbra.antumbra.Native
import com.penumbra.antumbra.OpenedDevice
import com.penumbra.antumbra.UsbHelper

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

    var connectionTypeLabel by mutableStateOf("—")
        private set

    var statusMessage by mutableStateOf("Waiting for device")
        private set

    val logs = mutableStateListOf<LogLine>()

    private var opened: OpenedDevice? = null

    init {
        usb.onPermissionResult = { device, granted ->
            if (granted) connect(device)
            else {
                statusMessage = "Permission denied"
                connState = ConnState.Error
                log("Permission denied for ${device.deviceName}", LogLine.Level.Error)
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
            statusMessage = "Requesting permission…"
            usb.requestPermission(device)
        }
    }

    private fun connect(device: UsbDevice) {
        connState = ConnState.Connecting
        statusMessage = "Opening…"
        log("Opening ${device.productName ?: device.deviceName}")

        val od = usb.openAndClaim(device)
        if (od == null) {
            connState = ConnState.Error
            statusMessage = "Open failed"
            log("openAndClaim failed", LogLine.Level.Error)
            return
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
        connState = ConnState.Connected
        statusMessage = "Connected · $connectionTypeLabel"
        log("Connected: $portName ($connectionTypeLabel)", LogLine.Level.Success)

        // probe
        val r = Native.probe(handle)
        log(if (r == 0) "Probe OK" else "Probe returned $r",
            if (r == 0) LogLine.Level.Success else LogLine.Level.Warn)
    }

    fun disconnect() {
        if (handle >= 0) {
            Native.closeDevice(handle)
            log("Closed handle $handle")
        }
        opened?.connection?.close()
        opened = null
        handle = -1
        portName = null
        connectionTypeLabel = "—"
        connState = ConnState.Disconnected
        statusMessage = "Disconnected"
        refreshDevices()
    }

    fun log(text: String, level: LogLine.Level = LogLine.Level.Info) {
        logs.add(0, LogLine(text, level))
        if (logs.size > 200) logs.removeAt(logs.lastIndex)
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
