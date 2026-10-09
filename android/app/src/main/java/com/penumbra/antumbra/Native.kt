package com.penumbra.antumbra

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint

object Native {
    init {
        System.loadLibrary("antumbra")
        init()
    }

    external fun init()

    external fun openDevice(
        connection: UsbDeviceConnection,
        epIn: UsbEndpoint,
        epOut: UsbEndpoint,
        connType: Int,
        vid: Int,
        pid: Int
    ): Long

    external fun closeDevice(handle: Long)

    external fun getPortName(handle: Long): String?

    external fun getConnectionType(handle: Long): Int

    /** Real penumbra Device::init handshake. 0 = ok. */
    external fun probe(handle: Long): Int

    external fun getHwCode(handle: Long): Int

    external fun isInitialized(handle: Long): Int
}
