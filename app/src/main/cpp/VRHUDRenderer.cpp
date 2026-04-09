#include <jni.h>
#include <string>
#include <cstring>
#include <android/log.h>

#define LOG_TAG "VRHUDRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局HUD状态
namespace VRHUDState {
    static bool isInitialized = false;
    static bool isVisible = false;
    static int layerZ = 999999; // 最上层
    static float posX = 0.5f;  // 屏幕中心X (0-1)
    static float posY = 0.3f;  // 上方30%位置 (0-1)
    static int width = 600;
    static int height = 120;
}

// JNI方法：初始化VR HUD
extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeInitialize(JNIEnv* env, jclass clazz, jint displayWidth, jint displayHeight) {
    if (VRHUDState::isInitialized) {
        LOGI("VR HUD already initialized");
        return JNI_TRUE;
    }

    // 注意：SurfaceControl API需要系统权限或root权限
    // 普通应用无法使用，这里只做初始化检查
    // 实际的全局HUD功能需要在AccessibilityService中实现

    VRHUDState::isInitialized = true;
    LOGI("VR HUD initialized (Note: Full functionality requires system privileges or AccessibilityService)");
    return JNI_TRUE;
}

// JNI方法：更新歌词
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeUpdateLyric(JNIEnv* env, jclass clazz, jstring lyric, jstring translation) {
    if (!VRHUDState::isInitialized || !VRHUDState::isVisible) return;

    // SurfaceControl实现需要系统权限，这里只做占位
    // 实际的歌词更新在AccessibilityService中处理

    const char* lyricStr = env->GetStringUTFChars(lyric, nullptr);
    if (lyricStr) {
        LOGI("Lyric update: %s", lyricStr);
        env->ReleaseStringUTFChars(lyric, lyricStr);
    }
}

// JNI方法：显示/隐藏HUD
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetVisible(JNIEnv* env, jclass clazz, jboolean visible) {
    VRHUDState::isVisible = (visible == JNI_TRUE);
    LOGI("VR HUD visibility: %s", visible ? "true" : "false");
}

// JNI方法：设置位置
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetPosition(JNIEnv* env, jclass clazz, jfloat x, jfloat y) {
    VRHUDState::posX = x;
    VRHUDState::posY = y;
    LOGI("VR HUD position: %.2f, %.2f", x, y);
}

// JNI方法：清理资源
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeCleanup(JNIEnv* env, jclass clazz) {
    VRHUDState::isInitialized = false;
    VRHUDState::isVisible = false;
    LOGI("VR HUD cleaned up");
}

// JNI方法：检查是否支持全局HUD
extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeIsGlobalHUDSupported(JNIEnv* env, jclass clazz) {
    // 检查是否有系统权限或调试模式
    // 普通应用返回false，表示只能使用应用内HUD或AccessibilityService
    
    // 使用prop获取系统属性
    char prop[32] = {0};
    __system_property_get("ro.debuggable", prop);
    bool isDebuggable = (strcmp(prop, "1") == 0);

    __system_property_get("ro.product.device", prop);
    bool isVRDevice = (strstr(prop, "quest") != nullptr || strstr(prop, "pico") != nullptr);

    LOGI("Global HUD support: debuggable=%s, vr_device=%s", 
         isDebuggable ? "true" : "false",
         isVRDevice ? "true" : "false");

    // SurfaceControl需要系统权限，普通应用无法使用
    // 返回false，建议使用AccessibilityService
    return JNI_FALSE;
}