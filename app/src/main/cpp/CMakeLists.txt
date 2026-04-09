cmake_minimum_required(VERSION 3.4.1)

# 创建LrcParser库（C语言）
add_library(
        LrcParser
        SHARED
        LrcParser.c
)

# 创建DesktopLyricRenderer库（C++语言）
add_library(
        DesktopLyricRenderer
        SHARED
        DesktopLyricRenderer.cpp
)

# 创建DynamicIslandRenderer库（C++语言）
add_library(
        DynamicIslandRenderer
        SHARED
        DynamicIslandRenderer.cpp
)

# 查找系统库
find_library(
        log-lib
        log
)

# 链接LrcParser库
target_link_libraries(
        LrcParser
        ${log-lib}
)

# 链接DesktopLyricRenderer库
target_link_libraries(
        DesktopLyricRenderer
        ${log-lib}
)

# 链接DynamicIslandRenderer库
target_link_libraries(
        DynamicIslandRenderer
        ${log-lib}
)