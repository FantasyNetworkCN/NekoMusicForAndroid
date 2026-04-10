cmake_minimum_required(VERSION 3.4.1)

# 项目声明
project(NekoMusicNative)

# 配置 STL 为 c++_shared
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 设置 STL 为 shared 模式（Android Gradle 插件通过 -DANDROID_STL 传递）
if(DEFINED ANDROID_STL)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DANDROID_STL=${ANDROID_STL}")
endif()

# 设置编译选项以确保16KB对齐
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fdata-sections -ffunction-sections -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fdata-sections -ffunction-sections -ffunction-sections -fdata-sections")

# 设置链接器选项确保16KB对齐
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,separate-loadable-segments")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,noexecstack")

# 创建LrcParser库
add_library(
        LrcParser
        SHARED
        LrcParser.c
)

# 创建DesktopLyricRenderer库
add_library(
        DesktopLyricRenderer
        SHARED
        DesktopLyricRenderer.cpp
)

# 创建DynamicIslandRenderer库
add_library(
        DynamicIslandRenderer
        SHARED
        DynamicIslandRenderer.cpp
)

# 创建VRHUDRenderer库（用于真正的VR全局HUD）
add_library(
        VRHUDRenderer
        SHARED
        VRHUDRenderer.cpp
)

# 查找OpenXR库
find_library(OPENXR_LOADER
    NAMES openxr_loader
    PATHS
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}
    DOC "OpenXR loader library"
)

# 尝试从源代码构建OpenXR loader（以确保xrInitializeLoaderKHR被正确导出）
set(OPENXR_BUILD_FROM_SOURCE TRUE)

if(OPENXR_BUILD_FROM_SOURCE)
    message(STATUS "Building OpenXR loader from source to ensure proper exports")

    # 设置OpenXR构建选项
    set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_API_LAYERS OFF CACHE BOOL "" FORCE)
    set(BUILD_CONFORMANCE_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_LOADER_TESTS OFF CACHE BOOL "" FORCE)
    set(BUILD_LOADER_WITH_EXCEPTION_HANDLING OFF CACHE BOOL "" FORCE)
    set(DYNAMIC_LOADER ON CACHE BOOL "" FORCE)
    set(XR_USE_PLATFORM_ANDROID ON CACHE BOOL "" FORCE)
    set(XR_USE_GRAPHICS_API_OPENGL_ES ON CACHE BOOL "" FORCE)
    set(BUILD_WITH_SYSTEM_JSONCPP OFF CACHE BOOL "" FORCE)
    set(OPENXR_USE_BUILT_IN_JSONCPP ON CACHE BOOL "" FORCE)
    set(BUILD_LOADER_WITH_PYTHON OFF CACHE BOOL "" FORCE)

    # 定义JSON_USE_EXCEPTIONS以避免编译错误
    add_definitions(-DJSON_USE_EXCEPTIONS=0)

    # 将OpenXR SDK作为子目录添加
    add_subdirectory(${CMAKE_SOURCE_DIR}/openxr_sdk ${CMAKE_BINARY_DIR}/openxr_sdk EXCLUDE_FROM_ALL)

    # 强制导出xrInitializeLoaderKHR函数
    target_compile_definitions(openxr_loader PRIVATE XRAPI_DLL_EXPORT)

    # 复制构建的OpenXR loader到输出目录
    add_custom_command(TARGET VRHUDRenderer POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
        $<TARGET_FILE:openxr_loader>
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopenxr_loader.so
        COMMENT "Copying built OpenXR loader to output directory"
    )

    target_compile_definitions(VRHUDRenderer PRIVATE OPENXR_AVAILABLE=1)
    target_include_directories(VRHUDRenderer PRIVATE
        ${CMAKE_SOURCE_DIR}/openxr_sdk/include
    )
elseif(OPENXR_LOADER)
    message(STATUS "Found OpenXR loader: ${OPENXR_LOADER}")
    
    # 将OpenXR库文件复制到构建输出目录
    add_custom_command(TARGET VRHUDRenderer POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy_if_different
        ${OPENXR_LOADER}
        ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libopenxr_loader.so
        COMMENT "Copying OpenXR loader to output directory"
    )
    
    target_compile_definitions(VRHUDRenderer PRIVATE OPENXR_AVAILABLE=1)
else()
    message(WARNING "OpenXR loader not found - 3D spatial HUD will use fallback")
    target_compile_definitions(VRHUDRenderer PRIVATE OPENXR_AVAILABLE=0)
endif()

# 查找系统库
find_library(
        log-lib
        log
)

# 查找OVR库（Oculus VR SDK）
# OVR SDK通常位于：${CMAKE_SOURCE_DIR}/../jniLibs 或 ${ANDROID_NDK}/sources/android/native_app_glue
# 这里我们尝试从Oculus SDK的标准位置查找
find_path(OVR_INCLUDE_DIR
    NAMES OVR_CAPI.h
    PATHS
    ${CMAKE_SOURCE_DIR}/../jniLibs/include
    ${CMAKE_SOURCE_DIR}/include
    ${ANDROID_NDK}/sources/third_party/vr/libovr/include
    ${ANDROID_NDK}/sources/android/native_app_glue
    DOC "OVR include directory"
)

find_library(OVR_LIB
    NAMES ovr
    PATHS
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}
    ${CMAKE_SOURCE_DIR}/../jniLibs
    ${ANDROID_NDK}/sources/third_party/vr/libovr/libs/${ANDROID_ABI}
    DOC "OVR library"
)

# 添加OVR包含目录
if(OVR_INCLUDE_DIR)
    target_include_directories(VRHUDRenderer PRIVATE ${OVR_INCLUDE_DIR})
    message(STATUS "Found OVR include directory: ${OVR_INCLUDE_DIR}")
else()
    message(WARNING "OVR include directory not found - VR HUD features may be limited")
endif()

# 链接库
target_link_libraries(
        LrcParser
        ${log-lib}
)

# 为LrcParser设置链接器选项
target_link_options(LrcParser PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
)

target_link_libraries(
        DesktopLyricRenderer
        ${log-lib}
)

# 为DesktopLyricRenderer设置链接器选项
target_link_options(DesktopLyricRenderer PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
)

target_link_libraries(
        DynamicIslandRenderer
        ${log-lib}
)

# 为DynamicIslandRenderer设置链接器选项
target_link_options(DynamicIslandRenderer PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
)

target_link_libraries(
        VRHUDRenderer
        ${log-lib}
        dl
        android
        EGL
        GLESv3
        openxr_loader
)

# 为VRHUDRenderer设置链接器选项
target_link_options(VRHUDRenderer PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
    LINKER:-z,noexecstack
)

# 如果找到OVR库，则链接它
if(OVR_LIB)
    target_link_libraries(VRHUDRenderer PRIVATE ${OVR_LIB})
    message(STATUS "Found OVR library: ${OVR_LIB}")
    target_compile_definitions(VRHUDRenderer PRIVATE OVR_SDK_AVAILABLE=1)
else()
    message(WARNING "OVR library not found - VR HUD will use fallback implementation")
    target_compile_definitions(VRHUDRenderer PRIVATE OVR_SDK_AVAILABLE=0)
endif()

# 确保在所有情况下都正确设置OVR_SDK_AVAILABLE
if(NOT DEFINED OVR_LIB)
    target_compile_definitions(VRHUDRenderer PRIVATE OVR_SDK_AVAILABLE=0)
endif()