import re

with open('VRHUDRenderer.cpp', 'r') as f:
    content = f.read()

# 插入定义
defs = '''
#define XR_TYPE_GRAPHICS_BINDING_VULKAN_KHR 1000025000

typedef struct XrGraphicsBindingVulkanKHR {
    XrStructureType type;
    const void* next;
    void* instance;
    void* physicalDevice;
    void* device;
    uint32_t queueFamilyIndex;
    uint32_t queueIndex;
} XrGraphicsBindingVulkanKHR;
'''
if 'XrGraphicsBindingVulkanKHR' not in content:
    content = content.replace('#define XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR 1000089000', '#define XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR 1000089000' + defs)

# 替换 session 逻辑
logic = '''
    static XrGraphicsBindingVulkanKHR vulkanBinding;
    memset(&vulkanBinding, 0, sizeof(vulkanBinding));
    vulkanBinding.type = (XrStructureType)XR_TYPE_GRAPHICS_BINDING_VULKAN_KHR;
    sessionCreateInfo.nextGraphicsBinding = &vulkanBinding;
'''
content = re.sub(r'sessionCreateInfo\.nextGraphicsBinding = nullptr;', logic, content)

with open('VRHUDRenderer.cpp', 'w') as f:
    f.write(content)
