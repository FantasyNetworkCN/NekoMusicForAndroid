import os

path = 'VRHUDRenderer.cpp'
with open(path, 'r') as f:
    lines = f.readlines()

# 过滤掉之前所有失败的补丁行
clean_lines = []
skip = False
for line in lines:
    if 'XrGraphicsBindingVulkanKHR' in line or 'vulkanBinding' in line or 'memset' in line and 'vulkanBinding' in line:
        continue
    if '#define XR_USE_GRAPHICS_API_VULKAN' in line or '#include <openxr/openxr_platform.h>' in line:
        continue
    clean_lines.append(line)

# 重新插入正确的定义和逻辑
final_lines = []
header_done = False
for line in clean_lines:
    if not header_done and '#include' in line:
        final_lines.append('#ifndef XR_USE_GRAPHICS_API_VULKAN\n')
        final_lines.append('#define XR_USE_GRAPHICS_API_VULKAN\n')
        final_lines.append('#endif\n')
        final_lines.append('#include <openxr/openxr_platform.h>\n')
        final_lines.append(line)
        header_done = True
    elif 'sessionCreateInfo.nextGraphicsBinding = nullptr;' in line:
        final_lines.append('    static XrGraphicsBindingVulkanKHR vulkanBinding;\n')
        final_lines.append('    memset(&vulkanBinding, 0, sizeof(vulkanBinding));\n')
        final_lines.append('    vulkanBinding.type = XR_TYPE_GRAPHICS_BINDING_VULKAN_KHR;\n')
        final_lines.append('    sessionCreateInfo.nextGraphicsBinding = &vulkanBinding;\n')
    else:
        final_lines.append(line)

with open(path, 'w') as f:
    f.writelines(final_lines)
