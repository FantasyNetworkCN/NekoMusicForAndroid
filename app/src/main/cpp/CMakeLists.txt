cmake_minimum_required(VERSION 3.4.1)

# 创建LrcParser库
add_library(
        LrcParser
        SHARED
        LrcParser.c
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