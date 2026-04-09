#include <jni.h>
#include <string>
#include <cstring>
#include <cstddef>
#include <android/log.h>
#include <sys/system_properties.h>
#include <dlfcn.h>

#define LOG_TAG "VRHUDRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define PROP_VALUE_MAX 92

// OpenXR基础类型定义
typedef int32_t XrResult;
typedef uint64_t XrFlags64;
typedef uint64_t XrSpace;
typedef uint64_t XrSession;
typedef uint64_t XrInstance;
typedef uint64_t XrSwapchain;
typedef uint32_t XrStructureType;
typedef int64_t XrTime;
typedef uint64_t XrSystemId;
typedef uint32_t XrFormFactor;
typedef uint32_t XrViewConfigurationType;
typedef uint32_t XrEnvironmentBlendMode;
typedef uint32_t XrSessionState;
typedef uint64_t XrReferenceSpaceType;
typedef int64_t XrFormat;

// 结果代码
#define XR_SUCCESS 0
#define XR_ERROR_VALIDATION_FAILURE -1
#define XR_ERROR_RUNTIME_FAILURE -2
#define XR_ERROR_OUT_OF_MEMORY -3
#define XR_ERROR_INSTANCE_LOST -4
#define XR_ERROR_SESSION_LOST -5
#define XR_ERROR_FUNCTION_UNSUPPORTED -6
#define XR_ERROR_FORM_FACTOR_UNSUPPORTED -7
#define XR_ERROR_SESSION_NOT_RUNNING -8

// 特殊值
#define XR_NULL_HANDLE 0

// API版本
#define XR_CURRENT_API_VERSION_MAJOR 1
#define XR_CURRENT_API_VERSION_MINOR 0
#define XR_CURRENT_API_VERSION_PATCH 0
#define XR_CURRENT_API_VERSION ((uint64_t)XR_CURRENT_API_VERSION_MAJOR << 48) | ((uint64_t)XR_CURRENT_API_VERSION_MINOR << 32) | XR_CURRENT_API_VERSION_PATCH

// 扩展
#define XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME "XR_KHR_opengl_es_enable"
#define XR_KHR_LOADER_INIT_EXTENSION_NAME "XR_KHR_loader_init"
#define XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME "XR_KHR_android_create_instance"

// 结构体类型
#define XR_TYPE_UNKNOWN 0
#define XR_TYPE_API_LAYER_PROPERTIES 1
#define XR_TYPE_EXTENSION_PROPERTIES 2
#define XR_TYPE_INSTANCE_CREATE_INFO 3
#define XR_TYPE_SYSTEM_GET_INFO 4
#define XR_TYPE_SYSTEM_PROPERTIES 5
#define XR_TYPE_SESSION_CREATE_INFO 6
#define XR_TYPE_SWAPCHAIN_CREATE_INFO 7
#define XR_TYPE_REFERENCE_SPACE_CREATE_INFO 8
#define XR_TYPE_COMPOSITION_LAYER_QUAD 9
#define XR_TYPE_COMPOSITION_LAYER_BASE_HEADER 10
#define XR_TYPE_SESSION_BEGIN_INFO 11
#define XR_TYPE_FRAME_STATE 12
#define XR_TYPE_EVENT_DATA_BUFFER 13
#define XR_TYPE_FRAME_END_INFO 14
#define XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO 15
#define XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO 16
#define XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO 17
#define XR_TYPE_LOADER_INIT_INFO_BASE_KHR 18
#define XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR 1000089000

// Layer flags
#define XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT 0x00000001

// 空间类型
#define XR_REFERENCE_SPACE_TYPE_LOCAL 0x00000001
#define XR_REFERENCE_SPACE_TYPE_STAGE 0x00000002

// Form Factor
#define XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY 1

// View Configuration
#define XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO 1

// Environment Blend Mode
#define XR_ENVIRONMENT_BLEND_MODE_OPAQUE 1

// Session State
#define XR_SESSION_STATE_UNKNOWN 0
#define XR_SESSION_STATE_IDLE 1
#define XR_SESSION_STATE_READY 2
#define XR_SESSION_STATE_SYNCHRONIZED 3
#define XR_SESSION_STATE_VISIBLE 4
#define XR_SESSION_STATE_FOCUSED 5
#define XR_SESSION_STATE_STOPPING 6
#define XR_SESSION_STATE_LOSS_PENDING 7
#define XR_SESSION_STATE_EXITING 8

// 基础结构体
typedef struct XrPosef {
    float orientation[4];
    float position[3];
} XrPosef;

typedef struct XrVector3f {
    float x, y, z;
} XrVector3f;

typedef struct XrQuaternionf {
    float x, y, z, w;
} XrQuaternionf;

typedef struct XrExtent2Di {
    int32_t width;
    int32_t height;
} XrExtent2Di;

typedef struct XrExtent2Df {
    float width;
    float height;
} XrExtent2Df;

typedef struct {
    struct { int32_t x, y; } offset;
    struct { int32_t width, height; } extent;
} XrRect2Di;

typedef struct {
    XrSwapchain swapchain;
    XrRect2Di imageRect;
    uint32_t imageArrayIndex;
} XrSwapchainSubImage;

typedef struct {
    XrStructureType type;
    void* next;
    XrFlags64 layerFlags;
    XrSpace space;
    int eyeVisibility;
    XrSwapchainSubImage subImage;
    XrPosef pose;
    XrExtent2Df size;
} XrCompositionLayerQuad;

typedef struct {
    XrStructureType type;
    void* next;
    XrFlags64 layerFlags;
} XrCompositionLayerBaseHeader;

typedef struct {
    XrStructureType type;
    void* next;
    XrFormFactor formFactor;
    uint32_t viewConfigType;
} XrSystemGetInfo;

typedef struct {
    XrStructureType type;
    void* next;
    uint32_t systemId;
    uint32_t vendorId;
    char systemName[256];
    struct {
        uint32_t maxSwapchainImageHeight;
        uint32_t maxSwapchainImageWidth;
        uint32_t maxLayerCount;
    } graphicsProperties;
} XrSystemProperties;

typedef struct {
    XrStructureType type;
    void* next;
    XrSystemId systemId;
    void* nextGraphicsBinding;
    uint32_t createFlags;
} XrSessionCreateInfo;

typedef struct {
    XrStructureType type;
    void* next;
    XrReferenceSpaceType referenceSpaceType;
    XrPosef poseInReferenceSpace;
} XrReferenceSpaceCreateInfo;

typedef struct {
    XrStructureType type;
    void* next;
    uint32_t createFlags;
    int64_t format;
    XrExtent2Di extent;
    uint32_t arraySize;
    uint32_t mipCount;
    uint32_t sampleCount;
    uint64_t usageFlags;
} XrSwapchainCreateInfo;

typedef struct {
    XrStructureType type;
    void* next;
    XrEnvironmentBlendMode environmentBlendMode;
    XrTime displayTime;
    XrSessionState state;
} XrFrameState;

typedef struct {
    XrStructureType type;
    void* next;
    XrSessionState state;
    XrTime time;
} XrEventDataSessionStateChanged;

typedef struct {
    XrStructureType type;
    void* next;
    XrTime displayTime;
    XrEnvironmentBlendMode environmentBlendMode;
} XrSessionBeginInfo;

typedef struct {
    XrStructureType type;
    void* next;
    XrEnvironmentBlendMode environmentBlendMode;
    XrTime displayTime;
    XrCompositionLayerBaseHeader* const* layers;
    uint32_t layerCount;
} XrFrameEndInfo;

typedef struct XrInstanceCreateInfo XrInstanceCreateInfo;

struct XrInstanceCreateInfo {
    XrStructureType type;
    void* next;
    char applicationName[256];
    uint32_t applicationVersion;
    char engineName[256];
    uint32_t engineVersion;
    uint32_t apiVersion;
    uint32_t enabledExtensionCount;
    const char** enabledExtensionNames;
    uint32_t enabledApiLayerCount;
    const char** enabledApiLayerNames;
};

typedef struct {
    XrStructureType type;
    void* next;
} XrSwapchainImageAcquireInfo;

typedef struct {
    XrStructureType type;
    void* next;
    XrTime timeout;
} XrSwapchainImageWaitInfo;

typedef struct {
    XrStructureType type;
    void* next;
} XrSwapchainImageReleaseInfo;

// Loader初始化结构
typedef struct {
    XrStructureType type;
    void* next;
} XrLoaderInitInfoBaseKHR;

// Android特定的loader初始化结构
typedef struct {
    XrStructureType type;
    void* next;
    void* application_vm;
    void* application_context;
} XrLoaderInitInfoAndroidKHR;

// OpenXR函数指针
typedef XrResult (*XrGetInstanceProcAddrFunc)(XrInstance instance, const char* name, void** function);
typedef XrResult (*XrCreateInstanceFunc)(const XrInstanceCreateInfo* createInfo, XrInstance* instance);
typedef XrResult (*XrDestroyInstanceFunc)(XrInstance instance);
typedef XrResult (*XrGetSystemFunc)(XrInstance instance, const XrSystemGetInfo* getInfo, XrSystemId* systemId);
typedef XrResult (*XrGetSystemPropertiesFunc)(XrInstance instance, XrSystemId systemId, XrSystemProperties* properties);
typedef XrResult (*XrCreateSessionFunc)(XrInstance instance, const XrSessionCreateInfo* createInfo, XrSession* session);
typedef XrResult (*XrDestroySessionFunc)(XrSession session);
typedef XrResult (*XrCreateReferenceSpaceFunc)(XrSession session, const XrReferenceSpaceCreateInfo* createInfo, XrSpace* space);
typedef XrResult (*XrCreateSwapchainFunc)(XrSession session, const XrSwapchainCreateInfo* createInfo, XrSwapchain* swapchain);
typedef XrResult (*XrDestroySwapchainFunc)(XrSwapchain swapchain);
typedef XrResult (*XrBeginSessionFunc)(XrSession session, const XrSessionBeginInfo* beginInfo);
typedef XrResult (*XrEndSessionFunc)(XrSession session);
typedef XrResult (*XrBeginFrameFunc)(XrSession session);
typedef XrResult (*XrEndFrameFunc)(XrSession session, const XrFrameEndInfo* frameEndInfo);
typedef XrResult (*XrPollEventFunc)(XrInstance instance, XrEventDataSessionStateChanged* eventData);
typedef XrResult (*XrAcquireSwapchainImageFunc)(XrSwapchain swapchain, const XrSwapchainImageAcquireInfo* acquireInfo, uint32_t* index);
typedef XrResult (*XrReleaseSwapchainImageFunc)(XrSwapchain swapchain, const XrSwapchainImageReleaseInfo* releaseInfo);
typedef XrResult (*XrWaitSwapchainImageFunc)(XrSwapchain swapchain, const XrSwapchainImageWaitInfo* waitInfo);
typedef XrResult (*XrEnumerateInstanceExtensionPropertiesFunc)(const char* layerName, uint32_t propertyCapacityInput, uint32_t* propertyCountOutput, void* properties);
typedef XrResult (*XrInitializeLoaderKHRFunc)(const XrLoaderInitInfoBaseKHR* loaderInfo);

// VR HUD状态
namespace VRHUDState {
    bool isInitialized = false;
    bool isVisible = false;
    
    // OpenXR函数指针
    XrGetInstanceProcAddrFunc xrGetInstanceProcAddr = nullptr;
    XrCreateInstanceFunc xrCreateInstance = nullptr;
    XrDestroyInstanceFunc xrDestroyInstance = nullptr;
    XrGetSystemFunc xrGetSystem = nullptr;
    XrGetSystemPropertiesFunc xrGetSystemProperties = nullptr;
    XrCreateSessionFunc xrCreateSession = nullptr;
    XrDestroySessionFunc xrDestroySession = nullptr;
    XrCreateReferenceSpaceFunc xrCreateReferenceSpace = nullptr;
    XrCreateSwapchainFunc xrCreateSwapchain = nullptr;
    XrDestroySwapchainFunc xrDestroySwapchain = nullptr;
    XrBeginSessionFunc xrBeginSession = nullptr;
    XrEndSessionFunc xrEndSession = nullptr;
    XrBeginFrameFunc xrBeginFrame = nullptr;
    XrEndFrameFunc xrEndFrame = nullptr;
    XrPollEventFunc xrPollEvent = nullptr;
    XrAcquireSwapchainImageFunc xrAcquireSwapchainImage = nullptr;
    XrReleaseSwapchainImageFunc xrReleaseSwapchainImage = nullptr;
    XrWaitSwapchainImageFunc xrWaitSwapchainImage = nullptr;
    XrEnumerateInstanceExtensionPropertiesFunc xrEnumerateInstanceExtensionProperties = nullptr;
    XrInitializeLoaderKHRFunc xrInitializeLoaderKHR = nullptr;
    
    // OpenXR资源
    XrInstance instance = 0;
    XrSession session = 0;
    XrSpace localSpace = 0;
    XrSwapchain hudSwapchain = 0;
    XrSystemId systemId = 0;
    
    // HUD参数
    XrPosef hudPose = {{0, 0, 0, 1}, {0, 0, -2}}; // 位置：前方2米
    XrExtent2Df hudSize = {1.0f, 0.5f}; // 尺寸：1米宽，0.5米高
    
    // 歌词内容
    std::string currentLyric = "";
    std::string currentTranslation = "";
    
    // 动态库句柄
    void* openxrLoader = nullptr;
    
    // 是否使用简化模式（不使用OpenXR）
    bool useSimplifiedMode = false;
    
    // Android Context和VM
    JavaVM* javaVM = nullptr;
    jobject androidContext = nullptr;
}

// 加载OpenXR库
bool loadOpenXRLibrary() {
    LOGI("Attempting to load OpenXR library...");

    // 在Pico设备上，应用无法直接加载系统库（权限限制）
    // 必须使用应用内打包的libopenxr_loader.so
    const char* systemLibraries[] = {
        "libopenxr_loader.so"  // 应用内打包的loader（必须使用）
    };

    const char* libraryNames[] = {
        "App bundled loader"
    };

    // 加载应用内loader
    for (int i = 0; i < 1; i++) {
        VRHUDState::openxrLoader = dlopen(systemLibraries[i], RTLD_LAZY);
        if (VRHUDState::openxrLoader) {
            LOGI("Loaded OpenXR library from: %s (%s)", systemLibraries[i], libraryNames[i]);
            break;
        }
    }

    if (!VRHUDState::openxrLoader) {
        const char* error = dlerror();
        LOGE("Failed to load libopenxr_loader.so: %s", error ? error : "unknown error");
        return false;
    }

    LOGI("OpenXR library loaded successfully, loading functions...");

    // 加载OpenXR核心函数
    VRHUDState::xrGetInstanceProcAddr = (XrGetInstanceProcAddrFunc)dlsym(VRHUDState::openxrLoader, "xrGetInstanceProcAddr");
    VRHUDState::xrCreateInstance = (XrCreateInstanceFunc)dlsym(VRHUDState::openxrLoader, "xrCreateInstance");
    VRHUDState::xrDestroyInstance = (XrDestroyInstanceFunc)dlsym(VRHUDState::openxrLoader, "xrDestroyInstance");
    VRHUDState::xrGetSystem = (XrGetSystemFunc)dlsym(VRHUDState::openxrLoader, "xrGetSystem");
    VRHUDState::xrGetSystemProperties = (XrGetSystemPropertiesFunc)dlsym(VRHUDState::openxrLoader, "xrGetSystemProperties");
    VRHUDState::xrCreateSession = (XrCreateSessionFunc)dlsym(VRHUDState::openxrLoader, "xrCreateSession");
    VRHUDState::xrDestroySession = (XrDestroySessionFunc)dlsym(VRHUDState::openxrLoader, "xrDestroySession");
    VRHUDState::xrCreateReferenceSpace = (XrCreateReferenceSpaceFunc)dlsym(VRHUDState::openxrLoader, "xrCreateReferenceSpace");
    VRHUDState::xrCreateSwapchain = (XrCreateSwapchainFunc)dlsym(VRHUDState::openxrLoader, "xrCreateSwapchain");
    VRHUDState::xrDestroySwapchain = (XrDestroySwapchainFunc)dlsym(VRHUDState::openxrLoader, "xrDestroySwapchain");
    VRHUDState::xrBeginSession = (XrBeginSessionFunc)dlsym(VRHUDState::openxrLoader, "xrBeginSession");
    VRHUDState::xrEndSession = (XrEndSessionFunc)dlsym(VRHUDState::openxrLoader, "xrEndSession");
    VRHUDState::xrBeginFrame = (XrBeginFrameFunc)dlsym(VRHUDState::openxrLoader, "xrBeginFrame");
    VRHUDState::xrEndFrame = (XrEndFrameFunc)dlsym(VRHUDState::openxrLoader, "xrEndFrame");
    VRHUDState::xrPollEvent = (XrPollEventFunc)dlsym(VRHUDState::openxrLoader, "xrPollEvent");
    VRHUDState::xrAcquireSwapchainImage = (XrAcquireSwapchainImageFunc)dlsym(VRHUDState::openxrLoader, "xrAcquireSwapchainImage");
    VRHUDState::xrReleaseSwapchainImage = (XrReleaseSwapchainImageFunc)dlsym(VRHUDState::openxrLoader, "xrReleaseSwapchainImage");
    VRHUDState::xrWaitSwapchainImage = (XrWaitSwapchainImageFunc)dlsym(VRHUDState::openxrLoader, "xrWaitSwapchainImage");
    VRHUDState::xrEnumerateInstanceExtensionProperties = (XrEnumerateInstanceExtensionPropertiesFunc)dlsym(VRHUDState::openxrLoader, "xrEnumerateInstanceExtensionProperties");

    // 检查关键函数是否加载成功
    if (!VRHUDState::xrCreateInstance || !VRHUDState::xrDestroyInstance ||
        !VRHUDState::xrGetSystem || !VRHUDState::xrCreateSession ||
        !VRHUDState::xrBeginSession || !VRHUDState::xrEndFrame) {
        LOGE("Failed to load all OpenXR functions");
        return false;
    }

    LOGI("All OpenXR functions loaded successfully");
    return true;
}

// 设置Android上下文
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetAndroidContext(JNIEnv* env, jclass clazz, jobject context) {
    LOGI("=== nativeSetAndroidContext called ===");
    
    if (context == nullptr) {
        LOGE("Received null context!");
        return;
    }
    
    LOGI("Original context: %p", context);
    LOGI("JNIEnv: %p", env);
    LOGI("Checking if context is valid...");
    
    // 验证原始 Context 是否有效
    jclass contextClass = env->GetObjectClass(context);
    if (contextClass == nullptr) {
        LOGE("Failed to get context class - context may be invalid!");
        return;
    }
    
    // 获取 Context 类名
    jmethodID getNameMethod = env->GetMethodID(contextClass, "getClass", "()Ljava/lang/Class;");
    if (getNameMethod != nullptr) {
        jobject classObj = env->CallObjectMethod(context, getNameMethod);
        if (classObj != nullptr) {
            jmethodID getSimpleNameMethod = env->GetMethodID(env->GetObjectClass(classObj), "getSimpleName", "()Ljava/lang/String;");
            if (getSimpleNameMethod != nullptr) {
                jstring name = (jstring)env->CallObjectMethod(classObj, getSimpleNameMethod);
                if (name != nullptr) {
                    const char* nameStr = env->GetStringUTFChars(name, nullptr);
                    LOGI("Context type: %s", nameStr);
                    env->ReleaseStringUTFChars(name, nameStr);
                }
            }
            env->DeleteLocalRef(classObj);
        }
    }
    
    env->DeleteLocalRef(contextClass);
    
    // 删除旧的 GlobalRef（如果存在）
    if (VRHUDState::androidContext != nullptr) {
        LOGI("Deleting old GlobalRef: %p", VRHUDState::androidContext);
        env->DeleteGlobalRef(VRHUDState::androidContext);
        VRHUDState::androidContext = nullptr;
    }
    
    // 创建新的 GlobalRef
    LOGI("Creating new GlobalRef...");
    jobject globalRef = env->NewGlobalRef(context);
    
    if (globalRef == nullptr) {
        LOGE("Failed to create GlobalRef for Android context!");
        VRHUDState::androidContext = nullptr;
    } else {
        VRHUDState::androidContext = globalRef;
        LOGI("GlobalRef created successfully: %p", VRHUDState::androidContext);
        
        // 验证 GlobalRef 是否有效
        jclass globalRefClass = env->GetObjectClass(globalRef);
        if (globalRefClass == nullptr) {
            LOGE("GlobalRef is invalid!");
        } else {
            LOGI("GlobalRef is valid");
            env->DeleteLocalRef(globalRefClass);
        }
    }
    
    LOGI("=== nativeSetAndroidContext completed ===");
}

// 获取JavaVM
extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    VRHUDState::javaVM = vm;
    LOGI("JNI_OnLoad: JavaVM captured");
    return JNI_VERSION_1_6;
}

// 初始化VR HUD
extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeInitialize(JNIEnv* env, jclass clazz, jint displayWidth, jint displayHeight) {
    if (VRHUDState::isInitialized) {
        LOGI("VR HUD already initialized");
        return JNI_TRUE;
    }
    
    LOGI("===== Starting VR HUD Initialization =====");
    LOGI("Display size: %dx%d", displayWidth, displayHeight);
    
    // 加载OpenXR库
    if (!loadOpenXRLibrary()) {
        LOGE("Initialization failed: Cannot load OpenXR library");
        return JNI_FALSE;
    }
    
    // 在Pico设备上，必须在调用任何OpenXR函数之前先调用xrInitializeLoaderKHR
    // 直接从loader获取函数指针，而不是通过xrGetInstanceProcAddr
    if (VRHUDState::openxrLoader && VRHUDState::javaVM && VRHUDState::androidContext) {
        LOGI("Initializing OpenXR loader with Android context...");
        LOGI("JavaVM: %p, Context: %p", VRHUDState::javaVM, VRHUDState::androidContext);

        // 获取当前线程的JNIEnv
        JNIEnv* env = nullptr;
        jint attachResult = VRHUDState::javaVM->GetEnv((void**)&env, JNI_VERSION_1_6);

        if (attachResult == JNI_EDETACHED) {
            LOGI("Current thread detached from JVM, attaching...");
            if (VRHUDState::javaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                LOGE("Failed to attach current thread to JVM");
            } else {
                LOGI("Successfully attached current thread to JVM");
            }
        }

        // 直接从loader获取xrInitializeLoaderKHR函数指针
        XrInitializeLoaderKHRFunc initializeLoader = (XrInitializeLoaderKHRFunc)dlsym(VRHUDState::openxrLoader, "xrInitializeLoaderKHR");

        LOGI("Direct dlsym result: %p", initializeLoader);

        if (initializeLoader != nullptr) {
            // 使用正确的结构体对齐方式初始化
            XrLoaderInitInfoAndroidKHR loaderInitInfo;
            memset(&loaderInitInfo, 0, sizeof(loaderInitInfo));
            loaderInitInfo.type = XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR;
            loaderInitInfo.next = nullptr;
            loaderInitInfo.application_vm = VRHUDState::javaVM;
            loaderInitInfo.application_context = VRHUDState::androidContext;

            LOGI("Calling xrInitializeLoaderKHR directly...");
            LOGI("  Structure size: %zu bytes", sizeof(loaderInitInfo));
            LOGI("  type: %d", loaderInitInfo.type);
            LOGI("  application_vm: %p", loaderInitInfo.application_vm);
            LOGI("  application_context: %p", loaderInitInfo.application_context);

            XrResult result = initializeLoader(reinterpret_cast<XrLoaderInitInfoBaseKHR*>(&loaderInitInfo));

            LOGI("xrInitializeLoaderKHR returned: %d", result);

            if (result != XR_SUCCESS) {
                LOGE("Failed to initialize OpenXR loader: %d", result);
                if (result == XR_ERROR_VALIDATION_FAILURE) {
                    LOGE("XR_ERROR_VALIDATION_FAILURE: Invalid parameters or structure type");
                } else if (result == XR_ERROR_RUNTIME_FAILURE) {
                    LOGE("XR_ERROR_RUNTIME_FAILURE: Runtime failure");
                } else if (result == XR_ERROR_FUNCTION_UNSUPPORTED) {
                    LOGE("XR_ERROR_FUNCTION_UNSUPPORTED: Function not supported");
                }
                LOGW("Continuing without loader initialization - may fail");
            } else {
                LOGI("OpenXR loader initialized successfully");
            }
        } else {
            LOGE("Failed to get xrInitializeLoaderKHR function via dlsym");
            const char* error = dlerror();
            if (error) {
                LOGE("dlerror: %s", error);
            }
            LOGW("Continuing without loader initialization - may fail");
        }
    } else {
        LOGW("OpenXR loader or Android context not available");
        if (!VRHUDState::openxrLoader) {
            LOGE("openxrLoader is NULL");
        }
        if (!VRHUDState::javaVM) {
            LOGE("javaVM is NULL");
        }
        if (!VRHUDState::androidContext) {
            LOGE("androidContext is NULL");
        }
    }

    // 创建XrInstance
    LOGI("Creating XrInstance...");
    XrInstanceCreateInfo instanceCreateInfo;
    memset(&instanceCreateInfo, 0, sizeof(instanceCreateInfo));
    instanceCreateInfo.type = XR_TYPE_INSTANCE_CREATE_INFO;
    instanceCreateInfo.applicationVersion = 1;
    instanceCreateInfo.apiVersion = (uint32_t)XR_CURRENT_API_VERSION;
    strncpy((char*)instanceCreateInfo.applicationName, "NekoMusic VR HUD", 256);
    instanceCreateInfo.enabledExtensionCount = 0;
    instanceCreateInfo.enabledApiLayerCount = 0;
    
    XrResult result = VRHUDState::xrCreateInstance(&instanceCreateInfo, &VRHUDState::instance);
    if (result != XR_SUCCESS) {
        LOGE("Failed to create XrInstance: %d", result);
        // 如果创建实例失败，使用简化模式
        VRHUDState::useSimplifiedMode = true;
        LOGI("Falling back to simplified mode (HUD simulation)");
        return JNI_TRUE; // 仍然返回成功，但使用简化模式
    }
    LOGI("XrInstance created successfully");
    
    // 获取XrSystemId
    LOGI("Getting XrSystemId...");
    XrSystemGetInfo systemGetInfo;
    memset(&systemGetInfo, 0, sizeof(systemGetInfo));
    systemGetInfo.type = XR_TYPE_SYSTEM_GET_INFO;
    systemGetInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
    systemGetInfo.viewConfigType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    
    result = VRHUDState::xrGetSystem(VRHUDState::instance, &systemGetInfo, &VRHUDState::systemId);
    if (result != XR_SUCCESS) {
        LOGE("Failed to get XrSystemId: %d", result);
        if (result == XR_ERROR_FORM_FACTOR_UNSUPPORTED) {
            LOGE("VR device not detected - form factor unsupported");
        }
        return JNI_FALSE;
    }
    LOGI("XrSystemId obtained: %llu", (unsigned long long)VRHUDState::systemId);
    
    // 获取系统属性
    LOGI("Getting system properties...");
    XrSystemProperties systemProperties;
    memset(&systemProperties, 0, sizeof(systemProperties));
    systemProperties.type = XR_TYPE_SYSTEM_PROPERTIES;
    
    result = VRHUDState::xrGetSystemProperties(VRHUDState::instance, VRHUDState::systemId, &systemProperties);
    if (result != XR_SUCCESS) {
        LOGE("Failed to get system properties: %d", result);
        return JNI_FALSE;
    }
    LOGI("System properties: %s, max layers: %u", systemProperties.systemName, systemProperties.graphicsProperties.maxLayerCount);
    
    // 创建XrSession
    LOGI("Creating XrSession...");
    XrSessionCreateInfo sessionCreateInfo;
    memset(&sessionCreateInfo, 0, sizeof(sessionCreateInfo));
    sessionCreateInfo.type = XR_TYPE_SESSION_CREATE_INFO;
    sessionCreateInfo.systemId = VRHUDState::systemId;
    sessionCreateInfo.nextGraphicsBinding = nullptr;
    sessionCreateInfo.createFlags = 0;
    
    result = VRHUDState::xrCreateSession(VRHUDState::instance, &sessionCreateInfo, &VRHUDState::session);
    if (result != XR_SUCCESS) {
        LOGE("Failed to create XrSession: %d", result);
        return JNI_FALSE;
    }
    LOGI("XrSession created successfully");
    
    // 创建本地参考空间
    LOGI("Creating local reference space...");
    XrReferenceSpaceCreateInfo spaceCreateInfo;
    memset(&spaceCreateInfo, 0, sizeof(spaceCreateInfo));
    spaceCreateInfo.type = XR_TYPE_REFERENCE_SPACE_CREATE_INFO;
    spaceCreateInfo.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
    spaceCreateInfo.poseInReferenceSpace.orientation[0] = 0;
    spaceCreateInfo.poseInReferenceSpace.orientation[1] = 0;
    spaceCreateInfo.poseInReferenceSpace.orientation[2] = 0;
    spaceCreateInfo.poseInReferenceSpace.orientation[3] = 1;
    spaceCreateInfo.poseInReferenceSpace.position[0] = 0;
    spaceCreateInfo.poseInReferenceSpace.position[1] = 0;
    spaceCreateInfo.poseInReferenceSpace.position[2] = 0;
    
    result = VRHUDState::xrCreateReferenceSpace(VRHUDState::session, &spaceCreateInfo, &VRHUDState::localSpace);
    if (result != XR_SUCCESS) {
        LOGE("Failed to create local space: %d", result);
        return JNI_FALSE;
    }
    LOGI("Local space created successfully");
    
    // 开始会话
    LOGI("Beginning session...");
    XrSessionBeginInfo sessionBeginInfo;
    memset(&sessionBeginInfo, 0, sizeof(sessionBeginInfo));
    sessionBeginInfo.type = XR_TYPE_SESSION_BEGIN_INFO;
    sessionBeginInfo.displayTime = 0;
    sessionBeginInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    
    result = VRHUDState::xrBeginSession(VRHUDState::session, &sessionBeginInfo);
    if (result != XR_SUCCESS) {
        LOGE("Failed to begin session: %d", result);
        return JNI_FALSE;
    }
    LOGI("Session begun successfully");
    
    VRHUDState::isInitialized = true;
    LOGI("===== VR HUD Initialization Complete =====");
    
    return JNI_TRUE;
}

// 更新歌词
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeUpdateLyric(JNIEnv* env, jclass clazz, jstring lyric, jstring translation) {
    if (!VRHUDState::isInitialized) {
        LOGD("Cannot update lyric - HUD not initialized");
        return;
    }
    
    const char* lyricStr = env->GetStringUTFChars(lyric, nullptr);
    const char* translationStr = env->GetStringUTFChars(translation, nullptr);
    
    VRHUDState::currentLyric = lyricStr ? lyricStr : "";
    VRHUDState::currentTranslation = translationStr ? translationStr : "";
    
    env->ReleaseStringUTFChars(lyric, lyricStr);
    env->ReleaseStringUTFChars(translation, translationStr);
    
    LOGI("Updated lyric: %s", VRHUDState::currentLyric.c_str());
}

// 设置可见性
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetVisible(JNIEnv* env, jclass clazz, jboolean visible) {
    VRHUDState::isVisible = (visible == JNI_TRUE);
    LOGI("HUD visibility: %s", VRHUDState::isVisible ? "true" : "false");
    
    if (VRHUDState::isVisible && VRHUDState::isInitialized) {
        LOGI("HUD is now visible - should be rendering in VR space");
    }
}

// 设置3D空间位置
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetPosition(JNIEnv* env, jclass clazz, jfloat x, jfloat y, jfloat z) {
    VRHUDState::hudPose.position[0] = x;
    VRHUDState::hudPose.position[1] = y;
    VRHUDState::hudPose.position[2] = z;
    LOGI("Set position: (%.2f, %.2f, %.2f) meters", x, y, z);
}

// 设置HUD位置（用户前方）
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetInFront(JNIEnv* env, jclass clazz, jfloat distance, jfloat yOffset) {
    VRHUDState::hudPose.position[0] = 0.0f;
    VRHUDState::hudPose.position[1] = yOffset;
    VRHUDState::hudPose.position[2] = -distance;
    LOGI("Set HUD in front at %.2f meters, Y offset %.2f meters", distance, yOffset);
}

// 设置HUD旋转
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetOrientation(JNIEnv* env, jclass clazz, jfloat w, jfloat x, jfloat y, jfloat z) {
    VRHUDState::hudPose.orientation[0] = x;
    VRHUDState::hudPose.orientation[1] = y;
    VRHUDState::hudPose.orientation[2] = z;
    VRHUDState::hudPose.orientation[3] = w;
    LOGI("Set orientation: (%.2f, %.2f, %.2f, %.2f)", w, x, y, z);
}

// 设置HUD尺寸
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetSize(JNIEnv* env, jclass clazz, jfloat width, jfloat height) {
    VRHUDState::hudSize.width = width;
    VRHUDState::hudSize.height = height;
    LOGI("Set size: %.2f x %.2f meters", width, height);
}

// 设置HUD旋转（四元数）
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeSetRotation(JNIEnv* env, jclass clazz, jfloat qx, jfloat qy, jfloat qz, jfloat qw) {
    Java_com_neko_music_util_VRHUDRenderer_nativeSetOrientation(env, clazz, qw, qx, qy, qz);
}

// 清理资源
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeCleanup(JNIEnv* env, jclass clazz) {
    if (!VRHUDState::isInitialized) return;
    
    LOGI("Cleaning up VR HUD");
    
    // 结束会话
    if (VRHUDState::session != 0 && VRHUDState::xrEndSession) {
        XrResult result = VRHUDState::xrEndSession(VRHUDState::session);
        if (result != XR_SUCCESS) {
            LOGE("Failed to end session: %d", result);
        }
    }
    
    // 销毁swapchain
    if (VRHUDState::hudSwapchain != 0 && VRHUDState::xrDestroySwapchain) {
        XrResult result = VRHUDState::xrDestroySwapchain(VRHUDState::hudSwapchain);
        if (result != XR_SUCCESS) {
            LOGE("Failed to destroy swapchain: %d", result);
        }
        VRHUDState::hudSwapchain = 0;
    }
    
    // 销毁session
    if (VRHUDState::session != 0 && VRHUDState::xrDestroySession) {
        XrResult result = VRHUDState::xrDestroySession(VRHUDState::session);
        if (result != XR_SUCCESS) {
            LOGE("Failed to destroy session: %d", result);
        }
        VRHUDState::session = 0;
    }
    
    // 销毁instance
    if (VRHUDState::instance != 0 && VRHUDState::xrDestroyInstance) {
        XrResult result = VRHUDState::xrDestroyInstance(VRHUDState::instance);
        if (result != XR_SUCCESS) {
            LOGE("Failed to destroy instance: %d", result);
        }
        VRHUDState::instance = 0;
    }
    
    if (VRHUDState::openxrLoader) {
        dlclose(VRHUDState::openxrLoader);
        VRHUDState::openxrLoader = nullptr;
    }
    
    // 释放 Android Context 的 Global Reference
    if (VRHUDState::androidContext != nullptr && env) {
        env->DeleteGlobalRef(VRHUDState::androidContext);
        VRHUDState::androidContext = nullptr;
        LOGI("Released Android context GlobalRef");
    }
    
    VRHUDState::isInitialized = false;
    VRHUDState::isVisible = false;
}

// 检查是否支持3D空间HUD
extern "C" JNIEXPORT jboolean JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeIsSpatialHUDSupported(JNIEnv* env, jclass clazz) {
    LOGI("Checking if 3D spatial HUD is supported...");
    
    char prop[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.product.device", prop) > 0) {
        std::string device(prop);
        bool isVRDevice = (device.find("quest") != std::string::npos || 
                          device.find("pico") != std::string::npos ||
                          device.find("vr") != std::string::npos ||
                          device.find("sparrow") != std::string::npos); // 添加sparrow识别
        
        LOGI("Detected device: %s, is VR: %s", prop, isVRDevice ? "true" : "false");
        
        if (isVRDevice) {
            LOGI("3D spatial HUD supported on VR device: %s", prop);
            return JNI_TRUE;
        }
    }
    
    LOGI("3D spatial HUD not supported - not a VR device");
    return JNI_FALSE;
}

// 获取当前显示时间
extern "C" JNIEXPORT jdouble JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeGetDisplayTime(JNIEnv* env, jclass clazz) {
    return 0.0;
}

// 渲染当前帧
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_VRHUDRenderer_nativeRenderFrame(JNIEnv* env, jclass clazz) {
    if (!VRHUDState::isInitialized || !VRHUDState::isVisible) return;
    
    if (VRHUDState::useSimplifiedMode) {
        // 简化模式：定期输出状态信息
        static int frameCount = 0;
        frameCount++;
        if (frameCount % 300 == 0) { // 每5秒输出一次（假设60fps）
            LOGI("Simplified mode active - HUD simulation running");
            LOGI("  Visible: %s", VRHUDState::isVisible ? "true" : "false");
            LOGI("  Position: (%.2f, %.2f, %.2f)", 
                 VRHUDState::hudPose.position[0],
                 VRHUDState::hudPose.position[1], 
                 VRHUDState::hudPose.position[2]);
            LOGI("  Lyric: %s", VRHUDState::currentLyric.c_str());
        }
        return;
    }
    
    // 正常OpenXR模式
    // 开始帧
    XrResult result = VRHUDState::xrBeginFrame(VRHUDState::session);
    if (result != XR_SUCCESS) {
        LOGD("Failed to begin frame: %d (session may not be running)", result);
        return;
    }
    
    // 创建HUD层
    if (VRHUDState::hudSwapchain != 0) {
        XrCompositionLayerQuad hudLayer;
        memset(&hudLayer, 0, sizeof(hudLayer));
        hudLayer.type = XR_TYPE_COMPOSITION_LAYER_QUAD;
        hudLayer.layerFlags = XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT;
        hudLayer.space = VRHUDState::localSpace;
        hudLayer.eyeVisibility = 0; // XR_EYE_VISIBILITY_BOTH
        hudLayer.subImage.swapchain = VRHUDState::hudSwapchain;
        hudLayer.subImage.imageRect.offset.x = 0;
        hudLayer.subImage.imageRect.offset.y = 0;
        hudLayer.subImage.imageRect.extent.width = 1024;
        hudLayer.subImage.imageRect.extent.height = 512;
        hudLayer.subImage.imageArrayIndex = 0;
        hudLayer.pose = VRHUDState::hudPose;
        hudLayer.size = VRHUDState::hudSize;
        
        XrCompositionLayerBaseHeader* layers[] = {(XrCompositionLayerBaseHeader*)&hudLayer};
        
        XrFrameEndInfo frameEndInfo;
        memset(&frameEndInfo, 0, sizeof(frameEndInfo));
        frameEndInfo.type = XR_TYPE_FRAME_END_INFO;
        frameEndInfo.displayTime = 0;
        frameEndInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        frameEndInfo.layerCount = 1;
        frameEndInfo.layers = layers;
        
        result = VRHUDState::xrEndFrame(VRHUDState::session, &frameEndInfo);
        if (result != XR_SUCCESS) {
            LOGD("Failed to end frame: %d", result);
        }
    } else {
        // 没有swapchain，直接结束帧
        XrFrameEndInfo frameEndInfo;
        memset(&frameEndInfo, 0, sizeof(frameEndInfo));
        frameEndInfo.type = XR_TYPE_FRAME_END_INFO;
        frameEndInfo.displayTime = 0;
        frameEndInfo.environmentBlendMode = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
        frameEndInfo.layerCount = 0;
        frameEndInfo.layers = nullptr;
        
        VRHUDState::xrEndFrame(VRHUDState::session, &frameEndInfo);
    }
    
    LOGD("Render frame with HUD layer");
}