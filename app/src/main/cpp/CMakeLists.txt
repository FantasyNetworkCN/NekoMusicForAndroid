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

# 按函数/数据分节，配合链接期 --gc-sections 丢弃未引用代码，减小产物体积
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections")

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

# 为LrcParser设置链接器选项
target_link_options(LrcParser PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
    LINKER:--exclude-libs,ALL
)

# 体积优化：C 代码体积优先
target_compile_options(LrcParser PRIVATE -Os)

target_link_libraries(
        DesktopLyricRenderer
        ${log-lib}
)

# 为DesktopLyricRenderer设置链接器选项
target_link_options(DesktopLyricRenderer PRIVATE
    LINKER:-z,max-page-size=16384
    LINKER:-z,separate-loadable-segments
    LINKER:--gc-sections
    LINKER:--exclude-libs,ALL
)

# DesktopLyricRenderer 为纯 C 实现（不依赖 libc++），配合以下选项进一步压体积：
# -Os 体积优先；-fno-exceptions/-fno-rtti 去掉异常与 RTTI 元数据；
# -fvisibility=hidden 隐藏内部符号，避免导出 std/内部函数撑大 dynsym。
target_compile_options(DesktopLyricRenderer PRIVATE
    -Os
    -fno-exceptions
    -fno-rtti
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
)
