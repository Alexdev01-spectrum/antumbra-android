package com.penumbra.antumbra

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbHelper(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val actionUsbPermission = "${context.packageName}.USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != actionUsbPermission) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            } ?: return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            onPermissionResult?.invoke(device, granted)
        }
    }

    var onPermissionResult: ((UsbDevice, Boolean) -> Unit)? = null

    fun register() {
        val filter = IntentFilter(actionUsbPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }
    }

    fun unregister() {
        try { context.unregisterReceiver(permissionReceiver) } catch (_: Exception) {}
    }

    fun listMtkDevices(): List<UsbDevice> {
        return usbManager.deviceList.values.filter { isMtk(it) }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pi = PendingIntent.getBroadcast(context, 0, Intent(actionUsbPermission), flags)
        usbManager.requestPermission(device, pi)
    }

    /**
     * Opens the device, claims the first interface that has bulk IN+OUT,
     * and returns a native handle (or null on failure).
     */
    fun openAndClaim(device: UsbDevice): Long? {
        val conn = usbManager.openDevice(device) ?: run {
            Log.e(TAG, "openDevice returned null")
            return null
        }

        // find bulk endpoints
        var epIn: android.hardware.usb.UsbEndpoint? = null
        var epOut: android.hardware.usb.UsbEndpoint? = null
        var claimedIntf: android.hardware.usb.UsbInterface? = null

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var foundIn: android.hardware.usb.UsbEndpoint? = null
            var foundOut: android.hardware.usb.UsbEndpoint? = null
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) foundIn = ep
                if (ep.direction == UsbConstants.USB_DIR_OUT) foundOut = ep
            }
            if (foundIn != null && foundOut != null) {
                if (conn.claimInterface(intf, true)) {
                    epIn = foundIn
                    epOut = foundOut
                    claimedIntf = intf
                    break
                }
            }
        }

        if (epIn == null || epOut == null) {
            Log.e(TAG, "no bulk IN/OUT pair found")
            conn.close()
            return null
        }

        val connType = when (device.productId) {
            0x0003 -> 0 // Brom
            0x2001 -> 2 // Da
            else -> 1   // Preloader
        }

        val handle = Native.openDevice(conn, epIn, epOut, connType)
        if (handle < 0) {
            claimedIntf?.let { conn.releaseInterface(it) }
            conn.close()
            return null
        }
        // keep connection open for the lifetime of the handle
        // (store conn somewhere if you need to close later)
        return handle
    }

    companion object {
        private const val TAG = "UsbHelper"

        private val MTK_VIDS = setOf(0x0E8D, 0x1004, 0x22D9, 0x0FCE)

        fun isMtk(device: UsbDevice): Boolean = device.vendorId in MTK_VIDS
    }
}
