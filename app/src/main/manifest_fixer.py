import re
path = 'AndroidManifest.xml'
with open(path, 'r') as f:
    content = f.read()

# 移除没用的 VR category，防止系统混淆
content = re.sub(r'<category android:name=com.google.intent.category.DAYDREAM />', '', content)
content = re.sub(r'<category android:name=com.oculus.intent.category.VR />', '', content)
# 确保 orientation 是 landscape 或者 unsensor，避免被系统拦截
content = re.sub(r'android:screenOrientation=.*?', 'android:screenOrientation=landscape', content)

with open(path, 'w') as f:
    f.write(content)
