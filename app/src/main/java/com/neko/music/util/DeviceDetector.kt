package com.neko.music.util

import android.os.Build

/**
 * 设备类型枚举
 */
enum class DeviceType {
    NORMAL_PHONE  // 普通手机
}

/**
 * 设备检测工具
 * 用于判断当前设备类型
 */
object DeviceDetector {
    private var cachedDeviceType: DeviceType? = null

    /**
     * 获取当前设备类型
     */
    fun getDeviceType(): DeviceType {
        cachedDeviceType?.let { return it }

        cachedDeviceType = DeviceType.NORMAL_PHONE
        return DeviceType.NORMAL_PHONE
    }

    /**
     * 是否为普通手机
     */
    fun isNormalPhone(): Boolean {
        return getDeviceType() == DeviceType.NORMAL_PHONE
    }

    /**
     * 获取设备信息（用于调试）
     */
    fun getDeviceInfo(): String {
        return """
            Manufacturer: ${Build.MANUFACTURER}
            Model: ${Build.MODEL}
            Product: ${Build.PRODUCT}
            Device: ${Build.DEVICE}
            Brand: ${Build.BRAND}
            Device Type: ${getDeviceType()}
        """.trimIndent()
    }
}