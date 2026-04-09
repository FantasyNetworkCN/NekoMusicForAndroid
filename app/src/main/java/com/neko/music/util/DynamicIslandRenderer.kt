package com.neko.music.util

/**
 * 灵动岛渲染器 - 使用C++实现的JNI接口
 * 提供高性能的悬浮窗拖动和交互功能
 */
object DynamicIslandRenderer {
    
    init {
        System.loadLibrary("DynamicIslandRenderer")
    }
    
    // 触摸事件类型
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2
    const val ACTION_CANCEL = 3
    
    // 处理结果类型
    const val RESULT_NOT_HANDLED = 0  // 未处理
    const val RESULT_CLICK = 1         // 点击
    const val RESULT_DRAG_END = 2     // 拖动结束
    const val RESULT_DRAGGING = 3     // 拖动中
    
    /**
     * 初始化灵动岛渲染器
     */
    external fun nativeInitialize()
    
    /**
     * 设置可见性
     * @param visible true为可见，false为隐藏
     */
    external fun nativeSetVisible(visible: Boolean)
    
    /**
     * 获取可见性
     * @return true为可见，false为隐藏
     */
    external fun nativeIsVisible(): Boolean
    
    /**
     * 处理触摸事件
     * @param action 触摸动作类型 (ACTION_DOWN, ACTION_UP, ACTION_MOVE, ACTION_CANCEL)
     * @param x x坐标
     * @param y y坐标
     * @return 处理结果 (RESULT_NOT_HANDLED, RESULT_CLICK, RESULT_DRAG_END, RESULT_DRAGGING)
     */
    external fun nativeHandleTouchEvent(action: Int, x: Float, y: Float): Int
    
    /**
     * 获取当前X坐标
     */
    external fun nativeGetX(): Int
    
    /**
     * 获取当前Y坐标
     */
    external fun nativeGetY(): Int
    
    /**
     * 设置位置
     * @param x x坐标
     * @param y y坐标
     */
    external fun nativeSetPosition(x: Int, y: Int)
    
    /**
     * 获取默认X坐标
     */
    external fun nativeGetDefaultX(): Int
    
    /**
     * 获取默认Y坐标
     */
    external fun nativeGetDefaultY(): Int
    
    /**
     * 获取宽度
     */
    external fun nativeGetWidth(): Int
    
    /**
     * 获取高度
     */
    external fun nativeGetHeight(): Int
    
    /**
     * 获取触摸阈值
     */
    external fun nativeGetTouchSlop(): Float
    
    /**
     * 获取点击超时时间（毫秒）
     */
    external fun nativeGetClickTimeout(): Int
    
    /**
     * 获取当前缩放比例
     */
    external fun nativeGetScale(): Float
    
    /**
     * 获取当前透明度
     */
    external fun nativeGetAlpha(): Float
    
    /**
     * 设置缩放比例
     * @param scale 缩放比例
     */
    external fun nativeSetScale(scale: Float)
    
    /**
     * 设置透明度
     * @param alpha 透明度（0.0-1.0）
     */
    external fun nativeSetAlpha(alpha: Float)
    
    /**
     * 重置状态
     */
    external fun nativeReset()
    
    /**
     * 获取状态信息（JSON格式）
     * @return JSON字符串，包含x, y, scale, alpha, visible, dragging等信息
     */
    external fun nativeGetStateInfo(): String
    
    /**
     * 清理资源
     */
    external fun nativeCleanup()
    
    /**
     * 初始化渲染器
     */
    fun initialize() {
        nativeInitialize()
    }
    
    /**
     * 显示/隐藏灵动岛
     */
    fun setVisible(visible: Boolean) {
        nativeSetVisible(visible)
    }
    
    /**
     * 检查是否可见
     */
    fun isVisible(): Boolean {
        return nativeIsVisible()
    }
    
    /**
     * 处理触摸事件（便捷方法）
     */
    fun handleTouchEvent(action: Int, x: Float, y: Float): TouchResult {
        return when (nativeHandleTouchEvent(action, x, y)) {
            RESULT_CLICK -> TouchResult.CLICK
            RESULT_DRAG_END -> TouchResult.DRAG_END
            RESULT_DRAGGING -> TouchResult.DRAGGING
            else -> TouchResult.NOT_HANDLED
        }
    }
    
    /**
     * 获取当前位置
     */
    fun getPosition(): Pair<Int, Int> {
        return Pair(nativeGetX(), nativeGetY())
    }
    
    /**
     * 设置当前位置
     */
    fun setPosition(x: Int, y: Int) {
        nativeSetPosition(x, y)
    }
    
    /**
     * 获取默认位置
     */
    fun getDefaultPosition(): Pair<Int, Int> {
        return Pair(nativeGetDefaultX(), nativeGetDefaultY())
    }
    
    /**
     * 获取尺寸
     */
    fun getSize(): Pair<Int, Int> {
        return Pair(nativeGetWidth(), nativeGetHeight())
    }
    
    /**
     * 重置到默认状态
     */
    fun resetToDefault() {
        nativeReset()
    }
    
    /**
     * 获取配置信息
     */
    fun getConfig(): Config {
        return Config(
            touchSlop = nativeGetTouchSlop(),
            clickTimeout = nativeGetClickTimeout(),
            width = nativeGetWidth(),
            height = nativeGetHeight(),
            defaultX = nativeGetDefaultX(),
            defaultY = nativeGetDefaultY()
        )
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        nativeCleanup()
    }
    
    /**
     * 触摸结果枚举
     */
    enum class TouchResult {
        NOT_HANDLED,  // 未处理
        CLICK,        // 点击
        DRAG_END,     // 拖动结束
        DRAGGING      // 拖动中
    }
    
    /**
     * 配置数据类
     */
    data class Config(
        val touchSlop: Float,
        val clickTimeout: Int,
        val width: Int,
        val height: Int,
        val defaultX: Int,
        val defaultY: Int
    )
}