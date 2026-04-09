cmake_minimum_required(VERSION 3.4.1)

# 简化的 OpenXR Loader 构建配置
project(openxr_loader)

# 设置 C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android 特定配置
if(ANDROID)
    find_library(ANDROID_LIBRARY NAMES android)
    find_library(ANDROID_LOG_LIBRARY NAMES log)
endif()

# 查找线程库
find_package(Threads REQUIRED)

# 定义 OpenXR 宏
set(XR_USE_GRAPHICS_API_OPENGL FALSE)
set(XR_USE_GRAPHICS_API_OPENGL_ES FALSE)
set(XR_USE_GRAPHICS_API_VULKAN FALSE)
set(XR_USE_PLATFORM_ANDROID TRUE)

# OpenXR Loader 源文件
set(LOADER_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_core.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_instance.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_logger.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_logger_recorders.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_properties.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/runtime_interface.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/manifest_file.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/api_layer_interface.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_init_data.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/android_utilities.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/common/object_info.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/src/lib_json/json_reader.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/src/lib_json/json_value.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/src/lib_json/json_writer.cpp
)

# 头文件
set(LOADER_HEADERS
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_instance.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_logger.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_logger_recorders.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_properties.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/runtime_interface.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/manifest_file.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/api_layer_interface.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/loader_init_data.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/android_utilities.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader/exception_handling.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/common/object_info.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/common/hex_and_handles.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/common/platform_utils.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/include/json/json.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/include/json/reader.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/include/json/value.h
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/include/json/writer.h
)

# 创建共享库
add_library(openxr_loader SHARED ${LOADER_SOURCES})

# 包含目录
target_include_directories(openxr_loader PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/loader
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/common
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/jsoncpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../openxr_sdk/src/external/android-jni-wrappers
)

# 链接库
target_link_libraries(openxr_loader PRIVATE
    ${ANDROID_LIBRARY}
    ${ANDROID_LOG_LIBRARY}
    ${CMAKE_DL_LIBS}
    Threads::Threads
)

# 定义宏
target_compile_definitions(openxr_loader PRIVATE
    XRLOADER_DISABLE_EXCEPTION_HANDLING
    XR_USE_PLATFORM_ANDROID
    JSON_USE_EXCEPTION=0
)

# 16KB 对齐
target_link_options(openxr_loader PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
    LINKER:-z,noexecstack
)

# 编译选项
target_compile_options(openxr_loader PRIVATE
    -Wno-unused-parameter
    -Wno-missing-field-initializers
)

# 导出符号
target_compile_definitions(openxr_loader PRIVATE XRAPI_DLL_EXPORT)