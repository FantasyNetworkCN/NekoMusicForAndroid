# Neko歌姬计划安卓版
![](https://count.getloli.com/get/@:NekoMusicAndroid?theme=moebooru)

> [!TIP]
> 🐾 **pc端入口**：[点击这里查看 Neko歌姬计划 pc版仓库](https://github.com/MinecraftNekoServer/NekoMusicForPc)
> 🐾 **后端**：[点击这里查看 Neko歌姬计划 后端仓库](https://github.com/FantasyNetworkCN/NekoMusic)

# 本平台唯一官网 https://music.nekocore.cn
- ## 请勿通过非官方渠道获取安装包。本站被大量非法黑产生成盗版客户端诈骗，如你通过第三方平台获取安装包导致被骗本站拒绝一切赔偿

<p align="center">
  <img src="docs/images/readme/android-hero.png" alt="Neko 歌姬计划 Android 首页、播放页与听歌识曲" width="100%">
</p>

## 项目介绍

Neko 歌姬计划 Android 客户端基于 Kotlin 与 Jetpack Compose 构建，接入 Neko 自托管曲库与
统一账号体系。应用支持在线播放、每日推荐、收藏与歌单、逐字歌词、一起听、后台听歌识曲，
并可从网易云音乐、QQ 音乐、酷狗音乐导入外部歌单。

- **同一账号与曲库**：通过系统浏览器完成 NekoAuth 登录，播放、收藏和歌单与 Web / PC 同步。
- **后台实时识曲**：通过 WebSocket 持续发送 16 kHz PCM，在自有曲库中实时匹配。
- **完整播放体验**：逐字歌词、播放模式、倍速、定时关闭、桌面歌词与通知控制。
- **多人一起听**：创建或加入房间，由服务端统一同步播放状态。
- **歌单迁移**：支持外部平台歌单链接 / ID 导入并匹配站内曲库。
- **安装包安全**：只从官方站点或本仓库发布页获取 APK。

### NekoAuth 登录

Android 使用系统浏览器完成 NekoAuth 授权，注册、账号中心与找回密码入口也直接打开系统浏览器。
登录完成后，`https://music.nekocore.cn/sso/native-complete` 或 `nekomusic://sso/complete` 拉起应用，
客户端接收一次性 handoff 并建立登录态。
登录回跳只携带一次性 handoff，不会把账号访问令牌放进链接。

### 播放音量与音源分离

播放页底部左侧显示音量图标，点击后打开音频模态框，可在“音量”和“EQ”之间切换。
普通音量与手机音量键和系统音量面板同步；EQ 提供 31 Hz–16 kHz 十段增益、
±12 dB 预增益、平坦/流行/摇滚/爵士/古典/人声/重低音/高音预设和重置，设置会持久化。
EQ 位于人声/伴奏混音之后，只处理最终音频；调节 EQ 或切换 EQ 开关不会重新请求分离结果。
设置页「播放与存储」中的「人声与伴奏调节」开启后，底部只显示人声、伴奏两路滑条。
开关和两路音量保存在本地，进程重启后恢复；一起听期间也可使用，仅影响本机声音。
分离设置和两路音量仅在本机使用，房间仍负责同步播放进度。

- **服务端分离**：需要 VIP。客户端通过 SSE 请求服务端分离任务，服务端按约 30 秒分片返回人声和伴奏地址。
  客户端只下载伴奏分片并解码为 PCM，与播放器解码出的人声/原曲同步混音；未收到当前分片时继续播放原曲。
  已到达的分片保留在当前歌曲内存中，切歌或关闭功能立即释放；分片解码和网络请求不在音频线程执行。
  两路音量在输出小块音频时应用，EQ 位于混音之后；修改滑条不会重新请求服务端，也不重建播放管线。
  当前版本不再下载本地 AI 模型，不包含 ONNX Runtime、JTransforms 或设备端神经网络推理。

### 空间管理

设置页「播放与存储」中的「空间管理」统计本软件的安装包、音乐缓存、封面与歌词缓存、分离分片缓存、
数据库、应用设置、本地文件、其他缓存、其他应用数据及外部缓存，并按本软件总占用展示每一项比例。
页面同时显示设备总容量、已用空间和可用空间；音乐缓存列表仍可在同一页面查看和清理。

### 界面预览

| 首页与每日推荐 | 播放页与逐字歌词 |
| --- | --- |
| ![Android 首页](docs/images/readme/android-home.png) | ![Android 播放页](docs/images/readme/android-player.png) |

| 播放菜单与定时关闭 | 后台听歌识曲 |
| --- | --- |
| ![Android 播放菜单](docs/images/readme/android-player-menu.png) | ![Android 听歌识曲](docs/images/readme/android-recognition.png) |

### 获取外部歌单api
获取qq歌单列表`https://music.nekocore.cn/loser1/getSongListDetail?disstid=歌单id`
获取网易云歌单列表`https://music.nekocore.cn/loser/playlist/track/all?id=歌单id`

## Deep Link 外部唤醒

支持通过浏览器或其他 App 唤醒 Neko歌姬计划，并直接跳转到指定页面。

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

### 一起听房间

分享网页 `/together/{token}` 只负责拉起客户端。Android 处理以下深链后会加入对应房间：

```
nekomusic://together/{token}
```

客户端内在播放页右上角分享菜单选择「一起听」即可创建或添加到房间；创建后会自动复制随机房间 URL。

### 网页调用示例

在 HTML 中放置以下链接，用户点击即可唤醒应用：

```html
<a href="nekomusic://player/48">播放歌曲</a>
<a href="nekomusic://playlist/1">打开歌单</a>
```
