import os

path = 'MainActivity.kt'
with open(path, 'r') as f:
    content = f.read()

# 彻底删除对 glSurfaceView 的生命周期调用，防止 NPE
content = content.replace('glSurfaceView?.onResume()', '// glSurfaceView.onResume()')
content = content.replace('glSurfaceView?.onPause()', '// glSurfaceView.onPause()')
# 确保 glSurfaceView 变量本身即使为 null 也不会在其他地方被强制解包
content = content.replace('glSurfaceView!!', 'glSurfaceView')

with open(path, 'w') as f:
    f.write(content)
