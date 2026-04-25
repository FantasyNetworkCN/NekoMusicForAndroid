# Neko云音乐安卓版
![](https://count.getloli.com/get/@:NekoMusicAndroid?theme=moebooru)

> [!TIP]
> 🐾 **移动端入口**：[点击这里查看 Neko云音乐 安卓版仓库](https://github.com/MinecraftNekoServer/NekoMusicForPc)

## Deep Link 外部唤醒

支持通过浏览器或其他 App 唤醒 Neko云音乐，并直接跳转到指定页面。

### 支持的 Scheme

| Scheme | 说明 |
|--------|------|
| `nekomusic://` | 自定义协议，适合 App 间调用 |

### 音乐播放页

仅需要音乐 ID，应用会自动通过 API 获取歌曲信息并打开播放页。

```
nekomusic://player/{musicId}
```

**示例：**
```bash
adb shell am start -a android.intent.action.VIEW -d "nekomusic://player/48" com.neko.music
```

### 歌单详情页

仅需要歌单 ID，应用会自动通过 API 获取歌单名称并打开歌单详情页。

```
nekomusic://playlist/{playlistId}
```

### 网页调用示例

在 HTML 中放置以下链接，用户点击即可唤醒应用：

```html
<a href="nekomusic://player/48">播放歌曲</a>
<a href="nekomusic://playlist/1">打开歌单</a>
```