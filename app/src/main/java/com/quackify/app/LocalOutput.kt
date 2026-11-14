package com.quackify.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

object LocalOutput {
    fun label(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return ""
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when {
            am.isBluetoothA2dpOn -> outputs.firstOrNull { isBluetooth(it.type) }
            am.isWiredHeadsetOn -> outputs.firstOrNull { isWired(it.type) }
            else -> null
        }?.productName?.toString().orEmpty()
    }

    private fun isBluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            type == AudioDeviceInfo.TYPE_BLE_SPEAKER

    private fun isWired(type: Int) = type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_ACCESSORY
}
