#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>
#include <cmath>
#include <ctime>

#define LOG_TAG "DynamicIslandRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 辅助函数：获取当前时间（毫秒）
static long getCurrentTime() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

// 灵动岛配置常量
namespace DynamicIslandConfig {
    // 位置配置
    constexpr int DEFAULT_X = 0;
    constexpr int DEFAULT_Y = 80;
    constexpr int WIDTH = 300;
    constexpr int HEIGHT = 100;
    
    // 触摸配置
    constexpr float TOUCH_SLOP = 8.0f;      // 触摸阈值
    constexpr float DRAG_THRESHOLD = 10.0f; // 拖动阈值
    constexpr int CLICK_TIMEOUT = 300;       // 点击超时时间
    
    // 动画配置
    constexpr int ANIMATION_DURATION = 200;  // 动画持续时间
    constexpr float MIN_SCALE = 0.95f;       // 最小缩放比例
    constexpr float MAX_SCALE = 1.05f;       // 最大缩放比例
}

// 灵动岛状态管理类
class DynamicIslandState {
public:
    // 位置状态
    int x = DynamicIslandConfig::DEFAULT_X;
    int y = DynamicIslandConfig::DEFAULT_Y;
    int initialX = 0;
    int initialY = 0;
    
    // 触摸状态
    float startX = 0.0f;
    float startY = 0.0f;
    float currentX = 0.0f;
    float currentY = 0.0f;
    bool isDragging = false;
    bool hasMoved = false;
    long actionDownTime = 0;
    
    // 动画状态
    float scale = 1.0f;
    float alpha = 1.0f;
    bool isAnimating = false;
    
    // 可见性状态
    bool isVisible = false;
    
    void reset() {
        x = DynamicIslandConfig::DEFAULT_X;
        y = DynamicIslandConfig::DEFAULT_Y;
        initialX = 0;
        initialY = 0;
        startX = 0.0f;
        startY = 0.0f;
        currentX = 0.0f;
        currentY = 0.0f;
        isDragging = false;
        hasMoved = false;
        actionDownTime = 0;
        scale = 1.0f;
        alpha = 1.0f;
        isAnimating = false;
    }
    
    bool shouldHandleClick() const {
        if (hasMoved || isDragging) return false;
        long duration = getCurrentTime() - actionDownTime;
        return duration < DynamicIslandConfig::CLICK_TIMEOUT;
    }
    
    bool shouldStartDrag(float dx, float dy) const {
        return (std::abs(dx) > DynamicIslandConfig::DRAG_THRESHOLD || 
                std::abs(dy) > DynamicIslandConfig::DRAG_THRESHOLD);
    }
};

// 全局状态实例
static DynamicIslandState g_state;

// JNI方法：初始化灵动岛
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeInitialize(JNIEnv* env, jclass clazz) {
    LOGI("DynamicIslandRenderer initialized");
    g_state.reset();
}

// JNI方法：设置可见性
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeSetVisible(JNIEnv* env, jclass clazz, jboolean visible) {
    g_state.isVisible = (visible == JNI_TRUE);
    LOGI("DynamicIsland visibility set to: %s", visible ? "true" : "false");
}

// JNI方法：获取可见性
extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeIsVisible(JNIEnv* env, jclass clazz) {
    return g_state.isVisible ? JNI_TRUE : JNI_FALSE;
}

// JNI方法：处理触摸事件
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeHandleTouchEvent(JNIEnv* env, jclass clazz, 
                                                                       jint action, jfloat x, jfloat y) {
    int result = 0; // 0 = 未处理, 1 = 点击, 2 = 拖动, 3 = 拖动中
    
    switch (action) {
        case 0: // ACTION_DOWN
            g_state.startX = x;
            g_state.startY = y;
            g_state.currentX = x;
            g_state.currentY = y;
            g_state.initialX = g_state.x;
            g_state.initialY = g_state.y;
            g_state.isDragging = false;
            g_state.hasMoved = false;
            g_state.actionDownTime = ::getCurrentTime();
            g_state.scale = DynamicIslandConfig::MIN_SCALE;
            result = 0;
            break;
            
        case 1: // ACTION_UP
            if (g_state.isDragging) {
                result = 2; // 拖动结束
            } else if (g_state.shouldHandleClick()) {
                result = 1; // 点击
            } else {
                result = 0; // 未处理
            }
            g_state.isDragging = false;
            g_state.scale = 1.0f;
            break;
            
        case 2: // ACTION_MOVE
            g_state.currentX = x;
            g_state.currentY = y;
            
            if (!g_state.isDragging) {
                float dx = x - g_state.startX;
                float dy = y - g_state.startY;
                
                if (g_state.shouldStartDrag(dx, dy)) {
                    g_state.isDragging = true;
                    g_state.hasMoved = true;
                    LOGI("Drag started: dx=%.2f, dy=%.2f", dx, dy);
                }
            }
            
            if (g_state.isDragging) {
                g_state.x = g_state.initialX + static_cast<int>(x - g_state.startX);
                g_state.y = g_state.initialY + static_cast<int>(y - g_state.startY);
                result = 3; // 拖动中
            }
            break;
            
        case 3: // ACTION_CANCEL
            g_state.isDragging = false;
            g_state.scale = 1.0f;
            result = 0;
            break;
    }
    
    return result;
}

// JNI方法：获取当前位置
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetX(JNIEnv* env, jclass clazz) {
    return g_state.x;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetY(JNIEnv* env, jclass clazz) {
    return g_state.y;
}

// JNI方法：设置位置
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeSetPosition(JNIEnv* env, jclass clazz, jint x, jint y) {
    g_state.x = x;
    g_state.y = y;
}

// JNI方法：获取默认位置
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetDefaultX(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::DEFAULT_X;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetDefaultY(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::DEFAULT_Y;
}

// JNI方法：获取尺寸
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetWidth(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::WIDTH;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetHeight(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::HEIGHT;
}

// JNI方法：获取触摸阈值
extern "C" JNIEXPORT jfloat JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetTouchSlop(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::TOUCH_SLOP;
}

// JNI方法：获取点击超时
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetClickTimeout(JNIEnv* env, jclass clazz) {
    return DynamicIslandConfig::CLICK_TIMEOUT;
}

// JNI方法：获取当前缩放
extern "C" JNIEXPORT jfloat JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetScale(JNIEnv* env, jclass clazz) {
    return g_state.scale;
}

// JNI方法：获取当前透明度
extern "C" JNIEXPORT jfloat JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetAlpha(JNIEnv* env, jclass clazz) {
    return g_state.alpha;
}

// JNI方法：设置缩放
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeSetScale(JNIEnv* env, jclass clazz, jfloat scale) {
    g_state.scale = scale;
}

// JNI方法：设置透明度
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeSetAlpha(JNIEnv* env, jclass clazz, jfloat alpha) {
    g_state.alpha = alpha;
}

// JNI方法：重置状态
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeReset(JNIEnv* env, jclass clazz) {
    g_state.reset();
}

// JNI方法：获取状态信息（JSON格式）
extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeGetStateInfo(JNIEnv* env, jclass clazz) {
    char buffer[256];
    snprintf(buffer, sizeof(buffer),
             "{\"x\":%d,\"y\":%d,\"scale\":%.2f,\"alpha\":%.2f,\"visible\":%s,\"dragging\":%s}",
             g_state.x, g_state.y, g_state.scale, g_state.alpha,
             g_state.isVisible ? "true" : "false",
             g_state.isDragging ? "true" : "false");
    return env->NewStringUTF(buffer);
}

// JNI方法：清理资源
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DynamicIslandRenderer_nativeCleanup(JNIEnv* env, jclass clazz) {
    g_state.reset();
    LOGI("DynamicIslandRenderer cleaned up");
}