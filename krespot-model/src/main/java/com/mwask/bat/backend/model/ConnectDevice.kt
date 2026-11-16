/*
 *  _   __                          _   
 * | | / /                         | |  
 * | |/ / _ __ ___  ___ _ __   ___ | |_ 
 * |    \| '__/ _ \/ __| '_ \ / _ \| __|
 * | |\  \ | |  __/\__ \ |_) | (_) | |_ 
 * \_| \_/_|  \___||___/ .__/ \___/ \__|
 *                     | |              
 *                     |_|              
 *
 * Copyright (c) 2026 23fpsz (SpotiBat). All Rights Reserved.
 * Original repository: https://github.com/Pro-mwas1234/SpotiBat
 *
 * PROPRIETARY / SOURCE-AVAILABLE MODULE:
 * You may view and audit this source code for personal or educational inspection.
 * Copying, modifying, distributing, or integrating this module (in whole or in part)
 * into any other software or project is strictly prohibited without prior written
 * permission from the author (23fpsz).
 */

package com.mwask.bat.backend.model

enum class DeviceKind {
    PHONE,
    TABLET,
    COMPUTER,
    SPEAKER,
    TV,
    CAR,
    CONSOLE,
    RECEIVER,
    STICK,
    DONGLE,
    CAST,
    HEADPHONES,
    UNKNOWN,
}

data class ConnectDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val canPlay: Boolean,
    val volume: Int,
    val volumeSteps: Int,
    val isControllable: Boolean,
    val disableVolume: Boolean,
    val supportsTransfer: Boolean,
    val supportsLogout: Boolean,
    val supportsCommandRequest: Boolean,
    val supportsSleepTimer: Boolean,
    val isObservable: Boolean,
    val audioQuality: String,
    val brand: String,
    val model: String,
    val softwareVersion: String,
    val license: String,
    val active: Boolean = false,
    val activating: Boolean = false,
    val local: Boolean = false,
) {
    val volumeFraction: Float
        get() = if (volumeSteps > 0) (volume.toFloat() / volumeSteps.toFloat()).coerceIn(0f, 1f) else 0f

    val canControlVolume: Boolean get() = isControllable && !disableVolume && volumeSteps > 0

    val canTransfer: Boolean get() = canPlay && isControllable
}
