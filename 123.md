# Neko云音乐 API 文档

English Documentation: [English API doc.md](README-EN.md)

### 使用本 API 需遵守本项目 LICENSE 协议，必须开源并保留 Neko云音乐 署名及源码链接！

#### 更新时间 2026年4月7日
## 概述

Neko云音乐提供完整的 RESTful API，支持音乐搜索、播放、用户认证、收藏等功能。所有 API 都基于 HTTP/HTTPS 协议，使用 JSON 格式进行数据交换。

**基础 URL:** `https://music.cnmsb.xin`

## 目录

- [认证说明](#认证说明)
- [用户相关 API](#用户相关-api)
- [歌单相关 API](#歌单相关-api)
- [歌手相关 API](#歌手相关-api)
- [音乐相关 API](#音乐相关-api)
- [错误码说明](#错误码说明)

---

## 认证说明

### 用户认证

所有需要用户登录的 API 都需要在请求头中包含用户 Token：

```
Authorization: <token>
```

Token 在用户登录时生成并返回给客户端。

**Token 有效期:** 30 天

---

## 用户相关 API

### 1. 用户注册

**端点:** `POST /api/user/register`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "username": "string",      // 用户名 (必填)
  "password": "string",      // 密码 (必填)
  "email": "string",         // 邮箱 (必填)
  "verificationCode": "string"  // 邮箱验证码 (必填)
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "user": {
      "id": 1,
      "username": "用户名",
      "email": "email@example.com",
      "createdAt": "2024-01-01T00:00:00"
    },
    "token": "64位十六进制字符串"
  }
}
```

### 2. 用户登录

**端点:** `POST /api/user/login`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "username": "string",  // 用户名或邮箱
  "password": "string"   // 密码
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "user": {
      "id": 1,
      "username": "用户名",
      "email": "email@example.com",
      "createdAt": "2024-01-01T00:00:00"
    },
    "token": "64位十六进制字符串"
  }
}
```

### 3. 发送邮箱验证码

**端点:** `POST /api/user/send-verification`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "email": "string"  // 邮箱地址
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "验证码已发送到您的邮箱"
}
```

### 4. 获取用户头像

**端点:** `GET /api/user/avatar/{userId}`

**路径参数:**
- `userId`: 用户 ID

**响应:** 图片文件 (PNG/JPG)

### 5. 获取收藏列表

**端点:** `GET /api/user/favorites`

**请求头:**
```
Authorization: <token>
```

**响应示例:**
```json
{
  "success": true,
  "favorites": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "filename": "song.mp3"
    }
  ]
}
```

### 6. 添加收藏

**端点:** `POST /api/user/favorites`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "musicId": 1  // 音乐 ID
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "收藏成功"
}
```

### 7. 删除收藏

**端点:** `DELETE /api/user/favorites/{musicId}`

**请求头:**
```
Authorization: <token>
```

**路径参数:**
- `musicId`: 音乐 ID

**响应示例:**
```json
{
  "success": true,
  "message": "取消收藏成功"
}
```

### 8. 获取收藏歌单列表

**端点:** `GET /api/user/favorite-playlists`

**请求头:**
```
Authorization: <token>
```

**响应示例:**
```json
{
  "success": true,
  "playlists": [
    {
      "id": 1,
      "name": "我的歌单",
      "description": "这是我的歌单描述",
      "musicCount": 5,
      "createdAt": 1706500800000,
      "updatedAt": 1706501100000,
      "favoriteTime": 1706501400000,
      "creator": {
        "id": 1,
        "username": "用户名"
      }
    }
  ]
}
```

**说明:**
- `createdAt`, `updatedAt`, `favoriteTime` 为 Unix 时间戳（毫秒）
- 只返回当前用户收藏的歌单列表
- 按收藏时间倒序排列

### 9. 收藏歌单

**端点:** `POST /api/user/favorite-playlists`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "playlistId": 1  // 歌单 ID
}
```

**响应示例:**
```json
{
  "success": true,
  "message": "收藏歌单成功"
}
```

**响应示例（已收藏）:**
```json
{
  "success": false,
  "message": "收藏歌单失败或已收藏"
}
```

### 10. 取消收藏歌单

**端点:** `DELETE /api/user/favorite-playlists/{playlistId}`

**请求头:**
```
Authorization: <token>
```

**路径参数:**
- `playlistId`: 歌单 ID

**响应示例:**
```json
{
  "success": true,
  "message": "取消收藏歌单成功"
}
```

### 11. 获取收藏歌单内音乐

**端点:** `GET /api/user/favorite-playlists/{playlistId}`

**请求头:**
```
Authorization: <token>
```

**路径参数:**
- `playlistId`: 歌单 ID

**响应示例:**
```json
{
  "success": true,
  "music": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "filename": "song.mp3",
      "position": 1
    }
  ]
}
```

**响应示例（未收藏）:**
```json
{
  "success": false,
  "message": "用户未收藏该歌单"
}
```

**说明:**
- 只有收藏过该歌单的用户才能查看歌单内的音乐
- 音乐按 position 字段升序排列

### 12. 上传用户头像

**端点:** `POST /api/user/avatar/upload`

**请求头:**
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数:**
- `avatar`: 图片文件 (multipart/form-data)
  - 支持格式：jpg, jpeg, png, gif, webp, bmp
  - 最大文件大小：50MiB

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "头像上传成功",
  "avatarPath": "avatars/1_550e8400-e29b-41d4-a716-446655440000.jpg"
}
```

**响应示例（失败）:**
```json
{
  "error": "未授权访问"
}
```

或

```json
{
  "error": "文件大小超过50MiB限制"
}
```

### 13. 用户上传音乐

**端点:** `POST /api/user/upload`

**请求头:**
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求参数:**
- `title`: 歌曲标题（必填）
- `artist`: 歌手名称（必填）
- `language`: 语言（必填）
  - 可选值：中文、粤语、上海语、英文、日语、韩语、法语、德语、俄语、纯音乐
- `album`: 专辑名称（可选）
- `tags`: 标签（可选）
- `duration`: 音乐时长，单位秒（必填）
- `uploadUserId`: 上传用户ID（必填）
- `musicFile`: 音乐文件（必填，multipart/form-data）
  - 支持格式：MP3、FLAC、WAV
- `coverFile`: 封面图片文件（可选，multipart/form-data）
  - 支持格式：jpg, jpeg, png, gif, webp, bmp
- `lyricsFile`: 歌词文件（可选，multipart/form-data）
  - 支持格式：lrc
  - 如果不提供歌词文件，系统会自动使用默认的空歌词文件

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "上传成功，等待审核",
  "data": {
    "id": 1,
    "status": "pending",
    "createdAt": "2026-02-12T16:30:00"
  }
}
```

**响应示例（重复音乐）:**
```json
{
  "success": false,
  "message": "已有重复音乐，请检查后重新上传"
}
```

**响应示例（未授权）:**
```json
{
  "success": false,
  "message": "未授权访问"
}
```

**说明:**
- 上传的音乐会进入待审核状态（`status: "pending"`）
- 管理员审核通过后，音乐会正式添加到音乐库
- 系统会自动检查是否有重复的音乐（相同的标题、歌手、专辑）
- 如果没有上传歌词文件，系统会自动创建一个空的歌词文件（no_lrc.lrc）
- 上传的文件会保存在 `user_upload/` 目录下，文件名格式为 `music_<timestamp>.<ext>`

**注意事项:**
- 标题、歌手、语言为必填项
- 音乐文件必须提供，且必须是MP3、FLAC或WAV格式
- 音乐时长需要用户手动输入或通过前端解析后传入
- 封面和歌词文件是可选的
- 上传成功后，用户需要等待管理员审核才能在音乐库中看到上传的音乐

**前端集成示例:**
```javascript
async function uploadMusic(formData) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('音乐上传成功，等待审核');
    console.log('上传记录ID:', data.data.id);
    console.log('状态:', data.data.status);
  } else {
    console.error('音乐上传失败:', data.message);
  }
  return data;
}

// 使用示例
const formData = new FormData();
formData.append('title', '歌曲标题');
formData.append('artist', '歌手名称');
formData.append('language', '中文');
formData.append('album', '专辑名称');
formData.append('tags', '流行,华语');
formData.append('duration', 235); // 音乐时长（秒）
formData.append('uploadUserId', 0);
formData.append('musicFile', musicFileObject);
formData.append('coverFile', coverFileObject); // 可选
formData.append('lyricsFile', lyricsFileObject); // 可选

uploadMusic(formData);
```

### 9. 修改用户密码

**端点:** `POST /api/user/password/change`

**请求头:**
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "oldPassword": "string",  // 原密码（必填）
  "newPassword": "string"   // 新密码（必填，长度不能少于6位）
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "密码修改成功"
}
```

**响应示例（失败）:**
```json
{
  "error": "原密码错误"
}
```

或

```json
{
  "error": "新密码长度不能少于6位"
}
```

或

```json
{
  "error": "新密码不能与原密码相同"
}
```

### 10. 获取用户上传审核通过的音乐

**端点:** `GET /api/user/uploaded-music`

**请求头:**
```
Authorization: <token>
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "获取用户上传审核通过的音乐列表成功",
  "userId": 123,
  "musicList": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "language": "中文",
      "tags": "流行,华语",
      "fileFormat": "mp3",
      "createdAt": "2026-02-16T10:00:00"
    }
  ],
  "total": 1
}
```

**响应示例（未登录）:**
```json
{
  "success": false,
  "message": "用户未登录或token无效"
}
```

**说明:**
- 此 API 需要登录才能访问
- 只返回当前用户上传的、审核通过的音乐列表
- 音乐按创建时间倒序排列（最新的在前面）
- 只包含审核状态为 `approved` 的音乐
- 每首音乐包含：
  - id, title, artist, album, duration
  - language, tags, fileFormat
  - createdAt: 创建时间

**使用场景:**
- 个人中心展示用户上传的作品
- 用户查看自己的音乐库
- 统计用户上传的音乐数量

**前端集成示例:**
```javascript
async function getUserUploadedMusic() {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/uploaded-music', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  const data = await response.json();
  if (data.success) {
    console.log(`获取到 ${data.total} 首审核通过的音乐:`, data.musicList);
    // data.musicList 是一个数组，包含用户上传的审核通过的音乐
    // 每首音乐包含完整的歌曲信息
  } else {
    console.error('获取用户上传音乐失败:', data.message);
  }
  return data;
}
```

---

## 歌单相关 API

- [搜索歌单](#1-搜索歌单)
- [获取歌单详情](#2-获取歌单详情)
- [获取歌单音乐列表](#3-获取歌单音乐列表)
- [创建歌单](#4-创建歌单)
- [获取歌单列表](#5-获取歌单列表)
- [更新歌单](#6-更新歌单)
- [删除歌单](#7-删除歌单)
- [添加音乐到歌单](#8-添加音乐到歌单)
- [从歌单中移除音乐](#9-从歌单中移除音乐)
- [歌单权限说明](#歌单权限说明)

### 1. 搜索歌单

**端点:** `POST /api/playlists/search`

**无需登录**

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "query": "string"  // 搜索关键词
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "搜索成功",
  "total": 2,
  "results": [
    {
      "id": 1,
      "userId": 1,
      "name": "我的歌单",
      "description": "这是我的歌单描述",
      "musicCount": 5,
      "createdAt": "2026-01-29 12:00:00",
      "updatedAt": "2026-01-29 12:05:00",
      "firstMusicId": 1,
      "firstMusicCover": "/path/to/cover.jpg"
    },
    {
      "id": 2,
      "userId": 2,
      "name": "流行音乐",
      "description": "收藏的流行歌曲",
      "musicCount": 10,
      "createdAt": "2026-01-28 10:00:00",
      "updatedAt": "2026-01-28 10:00:00",
      "firstMusicCover": "/api/user/avatar/default"
    }
  ]
}
```

**响应示例（失败）:**
```json
{
  "success": false,
  "message": "缺少搜索关键词"
}
```

**说明:**
- 此 API **无需登录**即可访问
- 搜索关键词会匹配歌单名称和描述
- 返回结果按创建时间倒序排列
- 每个结果包含：
  - 歌单基本信息（id, name, description, musicCount, createdAt, updatedAt）
  - 第一首音乐的 ID（firstMusicId）
  - 第一首音乐的封面 URL（firstMusicCover）
  - 如果歌单没有音乐，firstMusicCover 为默认头像 `/api/user/avatar/default`

**注意事项:**
- 搜索关键词不能为空
- 搜索是模糊匹配，使用 `LIKE %keyword%`
- 使用 POST 方式，参数在请求体中传递

### 2. 获取歌单详情

**端点:** `GET /api/playlist/{id}`

**路径参数:**
- `id`: 歌单 ID

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "获取歌单详情成功",
  "playlist": {
    "id": 1,
    "userId": 1,
    "name": "我的歌单",
    "description": "这是我的歌单描述",
    "musicCount": 5,
    "createdAt": "2026-01-29 12:00:00",
    "updatedAt": "2026-01-29 12:05:00"
  }
}
```

**响应示例（歌单不存在）:**
```json
{
  "success": false,
  "message": "歌单不存在"
}
```

**说明:**
- 此 API **无需登录**即可访问
- 任何用户（包括未登录用户）都可以查看歌单的基本信息
- 返回的歌单信息包含 `userId` 字段，可以识别歌单的创建者

### 3. 创建歌单

**端点:** `POST /api/user/playlist/create`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "name": "string",          // 歌单名称 (必填)
  "description": "string"   // 歌单描述 (可选)
}
```

**参数限制:**
- `name`: 长度不能超过 255 个字符
- `description`: 长度不能超过 500 个字符

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单创建成功",
  "playlist": {
    "id": 1,
    "name": "我的歌单",
    "description": "这是我的歌单描述",
    "musicCount": 0,
    "createdAt": "2026-01-29 12:00:00",
    "updatedAt": "2026-01-29 12:00:00"
  }
}
```

**响应示例（失败）:**
```json
{
  "success": false,
  "message": "歌单名称不能为空"
}
```

### 4. 获取歌单列表

**端点:** `GET /api/user/playlists`

**请求头:**
```
Authorization: <token>
```

**响应示例:**
```json
{
  "success": true,
  "message": "获取歌单列表成功",
  "playlists": [
    {
      "id": 1,
      "userId": 1,
      "name": "我的歌单",
      "description": "这是我的歌单描述",
      "musicCount": 5,
      "createdAt": "2026-01-29 12:00:00",
      "updatedAt": "2026-01-29 12:05:00"
    }
  ]
}
```

**说明:** 此 API 只返回当前登录用户创建的歌单列表，歌单按创建时间倒序排列。每个用户只能看到自己创建的歌单，不会看到其他用户的歌单。

**注意事项:**
- 此 API 需要登录才能访问
- 只返回当前用户的歌单，不会混合其他用户的歌单
- 如果要查看其他用户的歌单，请使用 `GET /api/playlist/{id}` API
- 歌单按创建时间倒序排列（最新的在前面）

### 5. 更新歌单

**端点:** `POST /api/user/playlist/update`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "id": 1,                   // 歌单 ID (必填)
  "name": "string",          // 歌单名称 (必填)
  "description": "string"   // 歌单描述 (可选，不修改则不传此字段或传null)
}
```

**参数说明:**
- `id`: 歌单 ID（必填），必须是当前用户创建的歌单
- `name`: 歌单名称（必填），长度不能超过 255 个字符
- `description`: 歌单描述（可选），长度不能超过 500 个字符
  - 如果只想修改描述，仍需传递 `name` 字段（使用当前名称）
  - 如果不修改描述，可以不传此字段或传 `null`

**使用场景:**
1. **同时修改名称和描述**：传递所有字段
2. **只修改描述**：传递 `id`、`name`（当前值）和新的 `description`
3. **只修改名称**：传递 `id` 和新的 `name`，不传 `description`

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单更新成功",
  "playlist": {
    "id": 1,
    "name": "更新的歌单名称",
    "description": "更新的描述",
    "musicCount": 5,
    "createdAt": "2026-01-29 12:00:00",
    "updatedAt": "2026-01-29 12:10:00"
  }
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限修改此歌单"
}
```

**说明:** 只有歌单的创建者（user_id 匹配）才能更新歌单信息。

### 6. 删除歌单

**端点:** `POST /api/user/playlist/delete`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "id": 1  // 歌单 ID (必填)
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "歌单删除成功"
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限删除此歌单"
}
```

**说明:**
- 只有歌单的创建者（user_id 匹配）才能删除歌单
- 删除歌单会级联删除 `playlist_music` 表中的所有关联记录
- 此操作不可恢复

### 7. 获取歌单音乐列表

**端点:** `GET /api/user/playlist/music/{playlistId}`

**请求头:**
```
Authorization: <token>
```

**路径参数:**
- `playlistId`: 歌单 ID

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "获取歌单音乐列表成功",
  "playlistId": 1,
  "total": 5,
  "musicList": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "coverPath": "/path/to/cover.jpg",
      "filePath": "/path/to/music.mp3",
      "fileFormat": "mp3",
      "language": "中文",
      "position": 1,
      "addedAt": "2026-01-29 12:00:00"
    }
  ]
}
```

**响应示例（歌单不存在）:**
```json
{
  "success": false,
  "message": "歌单不存在"
}
```

**说明:**
- 音乐列表按照 `position` 字段升序排列
- 返回的音乐信息包含完整的歌曲详情和添加时间
- 此 API **无需登录**即可访问（后端已移除 token 验证）
- 任何用户（包括未登录用户）都可以查看歌单内容

### 8. 添加音乐到歌单

**端点:** `POST /api/user/playlist/music/add`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "playlistId": 1,  // 歌单 ID (必填)
  "musicId": 1      // 音乐 ID (必填)
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "音乐添加到歌单成功"
}
```

**响应示例（失败）:**
```json
{
  "success": false,
  "message": "音乐添加到歌单失败或音乐已存在于歌单中"
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限修改此歌单"
}
```

**说明:**
- 只有歌单的创建者才能添加音乐到歌单
- 如果音乐已存在于歌单中，会返回失败
- 音乐会自动添加到歌单**最上面**（position = 1，其他音乐position + 1）
- 添加成功后会自动更新歌单的 `musicCount` 字段

### 9. 从歌单中移除音乐

**端点:** `POST /api/user/playlist/music/remove`

**请求头:**
```
Authorization: <token>
Content-Type: application/json
```

**请求体:**
```json
{
  "playlistId": 1,  // 歌单 ID (必填)
  "musicId": 1      // 音乐 ID (必填)
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "音乐从歌单中移除成功"
}
```

**响应示例（失败）:**
```json
{
  "success": false,
  "message": "音乐从歌单中移除失败或音乐不存在于歌单中"
}
```

**响应示例（权限错误）:**
```json
{
  "success": false,
  "message": "无权限修改此歌单"
}
```

**说明:**
- 只有歌单的创建者才能从歌单中移除音乐
- 如果音乐不存在于歌单中，会返回失败
- 移除音乐后会自动重新排序剩余音乐的 position（删除位置之后的position - 1）
- 移除成功后会自动更新歌单的 `musicCount` 字段

---
## 歌单权限说明

### 权限规则

| 操作 | 权限要求 |
|------|---------|
| 搜索歌单 | 无需登录（任何用户都可以搜索） |
| 获取歌单详情 | 无需登录（任何用户都可以查看） |
| 查看歌单列表 | 任何登录用户（只返回自己的歌单） |
| 查看歌单内容 | 无需登录（任何用户都可以查看歌单中的音乐） |
| 创建歌单 | 任何登录用户 |
| 更新歌单 | 只有歌单的创建者 |
| 删除歌单 | 只有歌单的创建者 |
| 添加音乐到歌单 | 只有歌单的创建者 |
| 从歌单中移除音乐 | 只有歌单的创建者 |

### 权限验证

- **查看权限**：所有用户（包括未登录用户）都可以搜索歌单、查看歌单详情和歌单内容，无需额外权限验证
- **歌单列表**：登录用户只能看到自己创建的歌单，不会看到其他用户的歌单
- **修改权限**：所有修改和删除操作都会验证用户是否是歌单的创建者（通过 token 识别用户身份）
- 如果用户尝试修改或删除不属于自己的歌单，服务器会返回 `403 Forbidden` 状态码和相应的错误信息

---

## 音乐相关 API

### 1. 搜索音乐

**端点:** `POST /api/music/search`

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "query": "string",  // 搜索关键词
  "page": 1,          // 页码 (可选，默认为 1)
  "pageSize": 20      // 每页数量 (可选，默认为 20)
}
```

**响应示例:**
```json
{
  "success": true,
  "data": {
    "total": 100,
    "page": 1,
    "pageSize": 20,
    "results": [
      {
        "id": 1,
        "title": "歌曲标题",
        "artist": "艺术家",
        "album": "专辑",
        "duration": 180,
        "coverUrl": "/api/music/cover/1"
      }
    ]
  }
}
```

### 2. 获取音乐信息

**端点:** `GET /api/music/info/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应示例:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "歌曲标题",
    "artist": "艺术家",
    "album": "专辑",
    "duration": 180,
    "coverUrl": "/api/music/cover/1",
    "fileUrl": "/api/music/file/1",
    "lyrics": "歌词内容"
  }
}
```

### 3. 获取音乐文件

**端点:** `GET /api/music/file/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应:** 音频文件 (MP3)

### 4. 获取音乐封面

**端点:** `GET /api/music/cover/{id}`

**路径参数:**
- `id`: 音乐 ID

**响应:** 图片文件 (PNG/JPG)

### 5. 获取歌词

**端点:** `GET /api/music/lyrics/{id}?t={时间戳}`

**路径参数:**
- `id`: 音乐 ID

**查询参数:**
- `t`: 时间戳（可选，用于避免缓存）

**响应示例:**
```json
{
  "success": true,
  "message": "获取歌词成功",
  "data": "歌词内容"
}
```

**说明:**
- 此 API **无需登录**即可访问
- 获取歌词时会自动增加该音乐的播放次数（play_count + 1）
- 建议在请求时添加时间戳参数以避免浏览器缓存

### 6. 获取播放次数排行榜

**端点:** `GET /api/music/ranking?t={时间戳}`

**无需登录**

**查询参数:**
- `limit`: 返回数量（可选，默认为 200，最大为 200）

**响应示例:**
```json
{
  "success": true,
  "message": "获取播放次数排行榜成功",
  "data": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "coverPath": "/path/to/cover.jpg",
      "coverUrl": "/path/to/cover.jpg",
      "language": "中文",
      "tags": "流行",
      "playCount": 100
    }
  ]
}
```

**说明:**
- 此 API **无需登录**即可访问
- 返回按播放次数从高到低排序的音乐列表
- 只返回播放次数大于 0 的音乐
- 默认返回前 200 首，最多支持返回 200 首
- 每首音乐包含：
  - id, title, artist, album, duration
  - coverPath: 封面文件路径
  - coverUrl: 封面访问 URL（如果封面不存在则为默认图标）
  - language: 语言
  - tags: 标签
  - playCount: 播放次数

**使用场景:**
- 首页展示热门音乐
- 音乐排行榜页面
- 推荐热门音乐给用户

**前端集成示例:**
```javascript
async function getMusicRanking(limit = 200) {
  const response = await fetch(`https://music.cnmsb.xin/api/music/ranking?limit=${limit}`, {
    method: 'GET'
  });

  const data = await response.json();
  if (data.success) {
    console.log(`获取到 ${data.data.length} 首热门音乐:`, data.data);
    // data.data 是一个数组，包含按播放次数排序的音乐
  } else {
    console.error('获取排行榜失败:', data.message);
  }
  return data;
}
```

### 7. 获取最新上传音乐

**端点:** `GET /api/music/latest?t={时间戳}`

**无需登录**

**查询参数:**
- `limit`: 返回数量（可选，默认为 300，最大为 500）
- `t`: 时间戳（可选，用于避免 CDN 缓存）

**响应示例:**
```json
{
  "success": true,
  "message": "获取最新音乐成功",
  "data": [
    {
      "id": 1,
      "title": "歌曲标题",
      "artist": "艺术家",
      "album": "专辑",
      "duration": 180,
      "coverPath": "/path/to/cover.jpg",
      "coverUrl": "/path/to/cover.jpg",
      "language": "中文",
      "tags": "流行",
      "fileFormat": "mp3",
      "createdAt": 1704067200000
    }
  ]
}
```

**说明:**
- 此 API **无需登录**即可访问
- 返回按上传时间从新到旧排序的音乐列表
- 默认返回最新 300 首音乐，最多支持返回 500 首
- 每首音乐包含：
  - id, title, artist, album, duration
  - coverPath: 封面文件路径
  - coverUrl: 封面访问 URL（如果封面不存在则为默认图标）
  - language: 语言
  - tags: 标签
  - fileFormat: 音频文件格式（mp3/flac/wav）
  - createdAt: 创建时间（Unix 时间戳，毫秒）

**使用场景:**
- 首页展示最新上线的音乐
- 新歌速递页面
- 推荐最新上传的音乐给用户

**前端集成示例:**
```javascript
async function getLatestMusic(limit = 300) {
  const timestamp = Date.now(); // 添加时间戳避免 CDN 缓存
  const response = await fetch(`https://music.cnmsb.xin/api/music/latest?limit=${limit}&t=${timestamp}`, {
    method: 'GET'
  });

  const data = await response.json();
  if (data.success) {
    console.log(`获取到 ${data.data.length} 首最新音乐:`, data.data);
    // data.data 是一个数组，包含按上传时间排序的音乐
  } else {
    console.error('获取最新音乐失败:', data.message);
  }
  return data;
}
```

---

## 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 请求成功 |
| 400 | 请求参数错误 |
| 401 | 未授权，需要登录或 Token 无效 |
| 403 | 禁止访问，权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 错误响应格式

```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 前端集成示例

### 用户登录

```javascript
async function login(username, password) {
  const response = await fetch('https://music.cnmsb.xin/api/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ username, password })
  });
  
  const data = await response.json();
  if (data.success) {
    localStorage.setItem('userToken', data.data.token);
    localStorage.setItem('userInfo', JSON.stringify(data.data.user));
  }
  return data;
}
```

### 搜索音乐

```javascript
async function searchMusic(query, page = 1, pageSize = 20) {
  const response = await fetch('https://music.cnmsb.xin/api/music/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ query, page, pageSize })
  });
  
  return await response.json();
}
```

### 获取收藏列表

```javascript
async function getFavorites() {
  const token = localStorage.getItem('userToken');
  const response = await fetch('https://music.cnmsb.xin/api/user/favorites', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  return await response.json();
}
```

### 上传用户头像

```javascript
async function uploadAvatar(avatarFile) {
  const token = localStorage.getItem('userToken');
  const formData = new FormData();
  formData.append('avatar', avatarFile);
  
  const response = await fetch('https://music.cnmsb.xin/api/user/avatar/upload', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('头像上传成功:', data.avatarPath);
  } else {
    console.error('头像上传失败:', data.error);
  }
  return data;
}
```

### 搜索歌单

```javascript
async function searchPlaylists(query) {
  const response = await fetch('https://music.cnmsb.xin/api/playlists/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      query: query
    })
  });

  const data = await response.json();
  if (data.success) {
    console.log(`搜索到 ${data.total} 个歌单:`, data.results);
    // data.results 是一个数组，包含匹配的歌单
    // 每个歌单包含：
    // - id, name, description, musicCount, createdAt, updatedAt
    // - firstMusicId: 第一首音乐的 ID
    // - firstMusicCover: 第一首音乐的封面 URL
  } else {
    console.error('搜索歌单失败:', data.message);
  }
  return data;
}
```

### 搜索歌手

```javascript
async function searchArtists(query) {
  const response = await fetch('https://music.cnmsb.xin/api/artists/search', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      query: query
    })
  });

  const data = await response.json();
  if (data.success) {
    const artist = data.artist;
    console.log(`歌手: ${artist.name}`);
    console.log(`音乐数量: ${artist.musicCount}`);
    console.log(`音乐列表:`, artist.musicList);
    // artist.musicList 是一个数组，包含该歌手的所有音乐
    // 每首音乐包含：
    // - id, title, artist, album, duration
    // - coverPath, filePath, fileFormat, language
  } else {
    console.error('搜索歌手失败:', data.message);
  }
  return data;
}
```

### 获取歌单详情

```javascript
async function getPlaylistDetail(playlistId) {
  // 无需登录即可访问
  const response = await fetch(`https://music.cnmsb.xin/api/playlist/${playlistId}`, {
    method: 'GET'
  });

  const data = await response.json();
  if (data.success) {
    console.log('歌单详情:', data.playlist);
    // data.playlist 包含歌单的基本信息
    // - id: 歌单 ID
    // - userId: 创建者 ID
    // - name: 歌单名称
    // - description: 歌单描述
    // - musicCount: 音乐数量
    // - createdAt: 创建时间
    // - updatedAt: 更新时间
  } else {
    console.error('获取歌单详情失败:', data.message);
  }
  return data;
}
```

### 创建歌单

```javascript
async function createPlaylist(name, description) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/create', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      name: name,
      description: description
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单创建成功:', data.playlist);
  } else {
    console.error('歌单创建失败:', data.message);
  }
  return data;
}
```

### 获取歌单列表

```javascript
async function getPlaylists() {
  const token = localStorage.getItem('userToken');

  const response = await fetch('https://music.cnmsb.xin/api/user/playlists', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });

  const data = await response.json();
  if (data.success) {
    console.log('获取到我的歌单列表:', data.playlists);
    // data.playlists 只包含当前用户创建的歌单
    // 不会混合其他用户的歌单
  } else {
    console.error('获取歌单列表失败:', data.message);
  }
  return data;
}
```

### 更新歌单

```javascript
async function updatePlaylist(playlistId, name, description) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/update', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      id: playlistId,
      name: name,
      description: description
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单更新成功:', data.playlist);
  } else if (data.message === '无权限修改此歌单') {
    alert('您没有权限修改此歌单');
  } else {
    console.error('歌单更新失败:', data.message);
  }
  return data;
}
```

### 删除歌单

```javascript
async function deletePlaylist(playlistId) {
  const token = localStorage.getItem('userToken');

  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/delete', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      id: playlistId
    })
  });

  const data = await response.json();
  if (data.success) {
    console.log('歌单删除成功');
    // 可以在这里刷新歌单列表
  } else if (data.message === '无权限删除此歌单') {
    alert('您没有权限删除此歌单');
  } else {
    console.error('歌单删除失败:', data.message);
  }
  return data;
}
```

### 获取歌单音乐列表

```javascript
async function getPlaylistMusic(playlistId) {
  // 无需登录即可访问
  const response = await fetch(`https://music.cnmsb.xin/api/user/playlist/music/${playlistId}`, {
    method: 'GET'
  });

  const data = await response.json();
  if (data.success) {
    console.log(`获取到 ${data.total} 首音乐:`, data.musicList);
    // data.musicList 是一个数组，包含歌单中的所有音乐
  } else {
    console.error('获取歌单音乐列表失败:', data.message);
  }
  return data;
}
```

### 添加音乐到歌单

```javascript
async function addMusicToPlaylist(playlistId, musicId) {
  const token = localStorage.getItem('userToken');

  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/music/add', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      playlistId: playlistId,
      musicId: musicId
    })
  });

  const data = await response.json();
  if (data.success) {
    console.log('音乐添加到歌单成功');
    // 可以在这里刷新歌单内容
  } else if (data.message === '无权限修改此歌单') {
    alert('您没有权限修改此歌单');
  } else if (data.message.includes('音乐已存在于歌单中')) {
    alert('音乐已存在于歌单中');
  } else {
    console.error('音乐添加到歌单失败:', data.message);
  }
  return data;
}
```

### 从歌单中移除音乐

```javascript
async function removeMusicFromPlaylist(playlistId, musicId) {
  const token = localStorage.getItem('userToken');

  const response = await fetch('https://music.cnmsb.xin/api/user/playlist/music/remove', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      playlistId: playlistId,
      musicId: musicId
    })
  });

  const data = await response.json();
  if (data.success) {
    console.log('音乐从歌单中移除成功');
    // 可以在这里刷新歌单内容
  } else if (data.message === '无权限修改此歌单') {
    alert('您没有权限修改此歌单');
  } else if (data.message.includes('音乐不存在于歌单中')) {
    alert('音乐不存在于歌单中');
  } else {
    console.error('音乐从歌单中移除失败:', data.message);
  }
  return data;
}
```

### 修改用户密码

```javascript
async function changePassword(oldPassword, newPassword) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/password/change', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      oldPassword: oldPassword,
      newPassword: newPassword
    })
  });
  
  const data = await response.json();
  if (data.success) {
    alert('密码修改成功！');
  } else {
    alert('密码修改失败：' + data.error);
  }
  return data;
}
```

### 获取收藏歌单列表

```javascript
async function getFavoritePlaylists() {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/favorite-playlists', {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('收藏歌单列表:', data.playlists);
    // data.playlists 包含用户收藏的所有歌单
    // 每个歌单包含：
    // - id, name, description, musicCount
    // - createdAt, updatedAt, favoriteTime (Unix时间戳)
    // - creator: 创建者信息 {id, username}
  } else {
    console.error('获取收藏歌单列表失败');
  }
  return data;
}
```

### 收藏歌单

```javascript
async function favoritePlaylist(playlistId) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch('https://music.cnmsb.xin/api/user/favorite-playlists', {
    method: 'POST',
    headers: {
      'Authorization': token,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      playlistId: playlistId
    })
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('收藏歌单成功');
    // 可以在这里刷新收藏列表
  } else if (data.message.includes('已收藏')) {
    alert('您已经收藏过这个歌单了');
  } else {
    console.error('收藏歌单失败:', data.message);
  }
  return data;
}
```

### 取消收藏歌单

```javascript
async function unfavoritePlaylist(playlistId) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch(`https://music.cnmsb.xin/api/user/favorite-playlists/${playlistId}`, {
    method: 'DELETE',
    headers: {
      'Authorization': token
    }
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('取消收藏歌单成功');
    // 可以在这里刷新收藏列表
  } else {
    console.error('取消收藏歌单失败:', data.message);
  }
  return data;
}
```

### 获取收藏歌单内音乐

```javascript
async function getFavoritePlaylistMusic(playlistId) {
  const token = localStorage.getItem('userToken');
  
  const response = await fetch(`https://music.cnmsb.xin/api/user/favorite-playlists/${playlistId}`, {
    method: 'GET',
    headers: {
      'Authorization': token
    }
  });
  
  const data = await response.json();
  if (data.success) {
    console.log('歌单音乐列表:', data.music);
    // data.music 包含歌单内的所有音乐
    // 每首音乐包含：
    // - id, title, artist, album, duration, filename, position
  } else if (data.message === '用户未收藏该歌单') {
    alert('您未收藏该歌单，无法查看音乐');
  } else {
    console.error('获取歌单音乐失败:', data.message);
  }
  return data;
}
```

---

## 注意事项

1. **CORS:** 所有 API 都支持跨域请求
2. **Token 管理:** Token 有效期为 30 天，过期后需要重新登录
3. **错误处理:** 所有 API 都返回统一的 JSON 格式，包含 `success` 和 `message` 字段
4. **速率限制:** 建议客户端实现适当的请求速率限制，避免频繁请求
5. **头像上传:**
   - 支持的图片格式：jpg, jpeg, png, gif, webp, bmp
   - 最大文件大小：50MiB
   - 只允许图片类型文件上传，会严格验证 MIME 类型
   - 头像文件保存在 `avatars/` 目录下
6. **密码修改:**
   - 新密码长度不能少于 6 位
   - 新密码不能与原密码相同
   - 需要提供正确的原密码才能修改
   - 使用 Argon2 算法加密密码
7. **歌单管理:**
   - 每个歌单都有唯一的 ID 和创建者（user_id）
   - 任何用户（包括未登录用户）都可以搜索歌单、查看歌单详情和歌单内容
   - 任何登录用户可以查看自己创建的歌单列表（不会混合其他用户的歌单）
   - 只有歌单的创建者才能更新和删除歌单（通过 token 验证）
   - 删除歌单会级联删除歌单中的所有音乐关联
   - 歌单名称长度限制：255 个字符
   - 歌单描述长度限制：500 个字符
   - `musicCount` 字段会自动更新，无需手动维护
   - 歌单按创建时间倒序排列（最新的在前面）
   - 响应中包含 `userId` 字段，可以识别歌单的创建者
   - 歌单不包含封面字段，客户端应根据歌单中的音乐列表自动选择封面（如使用第一首音乐的封面）
   - 新增 API：`POST /api/playlist/{id}` - 获取歌单详情（无需登录）
   - 新增 API：`POST /api/playlists/search` - 搜索歌单（无需登录）
   - 新增 API：`POST /api/artists/search` - 搜索歌手（无需登录）
   - 获取歌单音乐列表 API 已移除登录要求，任何用户都可以访问
   - 获取歌单列表 API 只返回当前用户的歌单，不会混合其他用户的歌单
   - 搜索歌单 API 会返回歌单的第一首音乐封面 URL，方便客户端展示
   - 搜索歌单 API 使用 POST 方式，参数在请求体中传递
   - 搜索歌手 API 会返回匹配到的第一个歌手及其所有音乐列表
   - 搜索歌手 API 返回的音乐列表包含完整的音乐信息
   - 获取歌单列表 API 只返回当前用户的歌单，不会混合其他用户的歌单
   - 搜索歌单 API 会返回歌单的第一首音乐封面 URL，方便客户端展示
   - 搜索歌单 API 使用 POST 方式，参数在请求体中传递

8. **收藏歌单:**
   - 用户可以收藏其他用户创建的歌单
   - 收藏歌单需要登录，通过 Authorization header 验证
   - 同一用户不能重复收藏同一个歌单
   - 只有收藏过该歌单的用户才能查看歌单内的音乐（权限控制）
   - 取消收藏歌单后，无法再查看该歌单内的音乐
   - 收藏歌单列表按收藏时间倒序排列（最新收藏的在前面）
   - 收藏歌单列表包含歌单的创建者信息
   - 新增 API：`GET /api/user/favorite-playlists` - 获取收藏歌单列表（需要登录）
   - 新增 API：`POST /api/user/favorite-playlists` - 收藏歌单（需要登录）
   - 新增 API：`DELETE /api/user/favorite-playlists/{id}` - 取消收藏歌单（需要登录）
   - 新增 API：`GET /api/user/favorite-playlists/{id}` - 获取收藏歌单内音乐（需要登录）

---

## 歌手相关 API

### 1. 搜索歌手

**端点:** `POST /api/artists/search`

**无需登录**

**请求头:**
```
Content-Type: application/json
```

**请求体:**
```json
{
  "query": "string"  // 搜索关键词
}
```

**响应示例（成功）:**
```json
{
  "success": true,
  "message": "搜索成功",
  "artist": {
    "name": "周杰伦",
    "musicCount": 50,
    "musicList": [
      {
        "id": 1,
        "title": "七里香",
        "artist": "周杰伦",
        "album": "七里香",
        "duration": 298,
        "coverPath": "/path/to/cover1.jpg",
        "filePath": "/path/to/music1.mp3",
        "fileFormat": "mp3",
        "language": "中文"
      },
      {
        "id": 2,
        "title": "晴天",
        "artist": "周杰伦",
        "album": "叶惠美",
        "duration": 269,
        "coverPath": "/path/to/cover2.jpg",
        "filePath": "/path/to/music2.mp3",
        "fileFormat": "mp3",
        "language": "中文"
      }
    ]
  }
}
```

**响应示例（未找到歌手）:**
```json
{
  "success": true,
  "message": "搜索成功",
  "artist": {
    "name": "",
    "musicCount": 0,
    "musicList": []
  }
}
```

**说明:**
- 此 API **无需登录**即可访问
- 搜索关键词会匹配歌手名称（模糊匹配）
- 返回匹配到的**第一个**歌手及其所有音乐
- 返回结果包含：
  - 歌手名称（name）
  - 该歌手的音乐数量（musicCount）
  - 该歌手的所有音乐列表（musicList），包含完整的音乐信息
- 如果没有找到匹配的歌手，返回空的歌手信息和空的音乐列表

**注意事项:**
- 搜索关键词不能为空
- 搜索是模糊匹配，使用 `LIKE %keyword%`
- 使用 POST 方式，参数在请求体中传递
- 只返回匹配到的第一个歌手（音乐数量最多的歌手）
- 返回的音乐列表包含完整的音乐信息，包括封面、文件路径等

**使用场景:**
- 用户搜索歌手以查看该歌手的所有音乐
- 展示歌手的音乐作品集
- 音乐分类浏览

---

## 联系方式

如有问题或建议，请联系：support@cnmsb.xin