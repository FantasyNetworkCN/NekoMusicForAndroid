package com.neko.music.util

import android.os.Build

/**
 * 设备类型枚举
 */
enum class DeviceType {
    NORMAL_PHONE,  // 普通手机
    VR_HEADSET     // VR头显（Quest、Pico等）
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

        val type = detectDeviceType()
        cachedDeviceType = type
        return type
    }

    /**
     * 检测设备类型
     */
    private fun detectDeviceType(): DeviceType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val device = Build.DEVICE.lowercase()

        // 检查是否为VR设备
        val isVRDevice = checkVRDevice(manufacturer, model, product, device)

        return if (isVRDevice) {
            DeviceType.VR_HEADSET
        } else {
            DeviceType.NORMAL_PHONE
        }
    }

    /**
     * 检查是否为VR设备
     */
    private fun checkVRDevice(
        manufacturer: String,
        model: String,
        product: String,
        device: String
    ): Boolean {
        // Meta/Oculus Quest系列
        if (manufacturer.contains("meta") ||
            manufacturer.contains("oculus") ||
            product.contains("quest") ||
            model.contains("quest") ||
            device.contains("quest")) {
            return true
        }

        // PICO系列
        if (manufacturer.contains("pico") ||
            product.contains("pico") ||
            model.contains("pico")) {
            return true
        }

        // 其他已知VR设备
        val vrKeywords = listOf(
            "htc_vive", "vive", "htc", // HTC Vive
            "hololens", // Microsoft HoloLens
            "magic_leap", // Magic Leap
            "daydream", // Google Daydream
            "cardboard", // Google Cardboard
            "vr_headset", "vr_head", "vr_device" // 通用VR标识
        )

        val combinedInfo = "$manufacturer $model $product $device".lowercase()
        return vrKeywords.any { combinedInfo.contains(it) }
    }

    /**
     * 是否为VR设备
     */
    fun isVRDevice(): Boolean {
        return getDeviceType() == DeviceType.VR_HEADSET
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