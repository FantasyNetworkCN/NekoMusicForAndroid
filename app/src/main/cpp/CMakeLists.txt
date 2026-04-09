cmake_minimum_required(VERSION 3.4.1)

# 项目声明
project(NekoMusicNative)

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

# 查找系统库
find_library(
        log-lib
        log
)

# 链接库
target_link_libraries(
        LrcParser
        ${log-lib}
)

target_link_libraries(
        DesktopLyricRenderer
        ${log-lib}
)

target_link_libraries(
        DynamicIslandRenderer
        ${log-lib}
)

target_link_libraries(
        VRHUDRenderer
        ${log-lib}
        dl
)