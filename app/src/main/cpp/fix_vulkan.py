import sys
import os

path = 'VRHUDRenderer.cpp'
with open(path, 'r') as f:
    content = f.read()

# 1. 添加头文件和定义
if '#define XR_USE_GRAPHICS_API_VULKAN' not in content:
    header_insertion = 
