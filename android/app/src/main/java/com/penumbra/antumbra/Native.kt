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
        connType: Int
    ): Long

    external fun closeDevice(handle: Long)

    external fun smokeTest(handle: Long): Int
}
