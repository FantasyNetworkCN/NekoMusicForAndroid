#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <regex>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "DesktopLyricRenderer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 歌词行结构体
struct LyricLine {
    float time;        // 时间（秒）
    std::string text;  // 歌词文本
    std::string translation; // 翻译文本
    
    LyricLine(float t, const std::string& txt) 
        : time(t), text(txt), translation("") {}
    
    LyricLine(float t, const std::string& txt, const std::string& trans) 
        : time(t), text(txt), translation(trans) {}
};

// 桌面歌词渲染器类
class DesktopLyricRenderer {
private:
    std::vector<LyricLine> lyrics;
    int currentMusicId;
    bool isInitialized;
    
public:
    DesktopLyricRenderer() : currentMusicId(-1), isInitialized(false) {
        LOGI("DesktopLyricRenderer created");
    }
    
    ~DesktopLyricRenderer() {
        LOGI("DesktopLyricRenderer destroyed");
    }
    
    // 初始化渲染器（为VR HUD优化）
    void initializeForVR() {
        isInitialized = true;
        LOGI("DesktopLyricRenderer initialized for VR HUD");
    }
    
    // 解析LRC歌词
    void parseLyrics(const std::string& lrcText, int musicId) {
        lyrics.clear();
        currentMusicId = musicId;
        
        std::istringstream stream(lrcText);
        std::string line;
        std::vector<std::string> lines;
        
        // 按行分割
        while (std::getline(stream, line)) {
            lines.push_back(line);
        }
        
        // 解析每一行
        for (size_t i = 0; i < lines.size(); i++) {
            const std::string& currentLine = lines[i];
            
            // 解析时间戳 [mm:ss.xx]
            std::regex timeRegex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\]");
            std::smatch match;
            
            if (std::regex_search(currentLine, match, timeRegex)) {
                int minutes = std::stoi(match[1].str());
                int seconds = std::stoi(match[2].str());
                int centiseconds = std::stoi(match[3].str());
                float time = minutes * 60.0f + seconds + centiseconds / 100.0f;
                
                // 提取歌词文本
                std::string text = currentLine.substr(match.position() + match.length());
                
                // 去除前后空白
                text.erase(0, text.find_first_not_of(" \t\r\n"));
                text.erase(text.find_last_not_of(" \t\r\n") + 1);
                
                // 检查下一行是否有翻译
                std::string translation;
                bool hasTranslation = false;
                if (i + 1 < lines.size()) {
                    const std::string& nextLine = lines[i + 1];
                    // 翻译行通常以 { } 包裹，且不包含时间戳
                    if (!nextLine.empty() && nextLine[0] == '{' && nextLine.back() == '}') {
                        // 检查是否包含时间戳，如果包含则不是翻译行
                        if (!std::regex_search(nextLine, timeRegex)) {
                            hasTranslation = true;
                            // 提取花括号内的内容
                            std::string content = nextLine.substr(1, nextLine.length() - 2);
                            // 去掉转义字符和引号
                            content.erase(std::remove(content.begin(), content.end(), '\\'), content.end());
                            content.erase(std::remove(content.begin(), content.end(), '"'), content.end());
                            content.erase(std::remove(content.begin(), content.end(), '\''), content.end());
                            // 去除前后空白
                            content.erase(0, content.find_first_not_of(" \t\r\n"));
                            content.erase(content.find_last_not_of(" \t\r\n") + 1);
                            translation = content;
                        }
                    }
                }
                
                lyrics.emplace_back(time, text, translation);
                
                // 如果找到翻译行，跳过它
                if (hasTranslation) {
                    i++;
                }
            }
        }
        
        LOGI("Parsed %zu lyrics for musicId: %d", lyrics.size(), musicId);
    }
    
    // 获取当前时间对应的歌词行
    LyricLine* getCurrentLyric(float currentTime) {
        if (lyrics.empty()) {
            return nullptr;
        }
        
        // 查找最后一个时间小于等于当前时间的歌词行
        for (auto it = lyrics.rbegin(); it != lyrics.rend(); ++it) {
            if (it->time <= currentTime) {
                return &(*it);
            }
        }
        
        return nullptr;
    }
    
    // 获取当前音乐ID
    int getCurrentMusicId() const {
        return currentMusicId;
    }
    
    // 获取歌词数量
    size_t getLyricCount() const {
        return lyrics.size();
    }
    
    // 为VR HUD优化：获取当前歌词的渲染数据
    // 返回格式：JSON字符串，包含歌词文本、翻译、位置信息等
    std::string getVRHUDData(float currentTime) {
        LyricLine* current = getCurrentLyric(currentTime);
        
        if (current == nullptr) {
            return "{\"text\":\"暂无歌词\",\"translation\":\"\",\"hasLyric\":false}";
        }
        
        std::stringstream ss;
        ss << "{";
        ss << "\"text\":\"" << escapeJson(current->text) << "\",";
        ss << "\"translation\":\"" << escapeJson(current->translation) << "\",";
        ss << "\"time\":" << current->time << ",";
        ss << "\"currentTime\":" << currentTime << ",";
        ss << "\"hasLyric\":true";
        ss << "}";
        
        return ss.str();
    }
    
    // 获取前后歌词（用于VR HUD滚动效果）
    std::string getVRHUDContext(float currentTime, int contextLines) {
        if (lyrics.empty()) {
            return "[]";
        }
        
        // 找到当前歌词的索引
        int currentIndex = -1;
        for (size_t i = 0; i < lyrics.size(); i++) {
            if (lyrics[i].time <= currentTime) {
                currentIndex = static_cast<int>(i);
            } else {
                break;
            }
        }
        
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        
        // 构建前后歌词的JSON数组
        std::stringstream ss;
        ss << "[";
        
        int startIdx = std::max(0, currentIndex - contextLines);
        int endIdx = std::min(static_cast<int>(lyrics.size()) - 1, currentIndex + contextLines);
        
        for (int i = startIdx; i <= endIdx; i++) {
            if (i > startIdx) {
                ss << ",";
            }
            
            bool isCurrent = (i == currentIndex);
            ss << "{";
            ss << "\"text\":\"" << escapeJson(lyrics[i].text) << "\",";
            ss << "\"translation\":\"" << escapeJson(lyrics[i].translation) << "\",";
            ss << "\"time\":" << lyrics[i].time << ",";
            ss << "\"isCurrent\":" << (isCurrent ? "true" : "false");
            ss << "}";
        }
        
        ss << "]";
        return ss.str();
    }
    
private:
    // 转义JSON字符串
    std::string escapeJson(const std::string& input) {
        std::string result;
        result.reserve(input.length() * 2);
        
        for (char c : input) {
            switch (c) {
                case '"': result += "\\\""; break;
                case '\\': result += "\\\\"; break;
                case '\b': result += "\\b"; break;
                case '\f': result += "\\f"; break;
                case '\n': result += "\\n"; break;
                case '\r': result += "\\r"; break;
                case '\t': result += "\\t"; break;
                default:
                    if (c < ' ') {
                        char buf[7];
                        snprintf(buf, sizeof(buf), "\\u%04X", static_cast<unsigned char>(c));
                        result += buf;
                    } else {
                        result += c;
                    }
            }
        }
        
        return result;
    }
};

// 全局渲染器实例
static DesktopLyricRenderer* g_renderer = nullptr;

// 获取或创建渲染器实例
DesktopLyricRenderer* getRenderer() {
    if (g_renderer == nullptr) {
        g_renderer = new DesktopLyricRenderer();
    }
    return g_renderer;
}

// JNI方法：初始化渲染器
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeInitialize(JNIEnv* env, jclass clazz) {
    DesktopLyricRenderer* renderer = getRenderer();
    renderer->initializeForVR();
}

// JNI方法：解析歌词
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeParseLyrics(JNIEnv* env, jclass clazz, 
                                                                jstring lrcText, jint musicId) {
    if (lrcText == nullptr) {
        LOGE("lrcText is null");
        return;
    }
    
    const char* lrcStr = env->GetStringUTFChars(lrcText, nullptr);
    if (lrcStr == nullptr) {
        LOGE("Failed to get lrcText string");
        return;
    }
    
    std::string lrcContent(lrcStr);
    env->ReleaseStringUTFChars(lrcText, lrcStr);
    
    DesktopLyricRenderer* renderer = getRenderer();
    renderer->parseLyrics(lrcContent, musicId);
}

// JNI方法：获取当前歌词
extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetCurrentLyric(JNIEnv* env, jclass clazz, 
                                                                    jfloat currentTime) {
    DesktopLyricRenderer* renderer = getRenderer();
    LyricLine* current = renderer->getCurrentLyric(currentTime);
    
    if (current == nullptr) {
        return env->NewStringUTF("{\"text\":\"暂无歌词\",\"translation\":\"\",\"hasLyric\":false}");
    }
    
    std::string jsonData = renderer->getVRHUDData(currentTime);
    return env->NewStringUTF(jsonData.c_str());
}

// JNI方法：获取前后歌词上下文
extern "C" JNIEXPORT jstring JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetLyricContext(JNIEnv* env, jclass clazz, 
                                                                    jfloat currentTime, jint contextLines) {
    DesktopLyricRenderer* renderer = getRenderer();
    std::string contextData = renderer->getVRHUDContext(currentTime, contextLines);
    return env->NewStringUTF(contextData.c_str());
}

// JNI方法：获取当前音乐ID
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetCurrentMusicId(JNIEnv* env, jclass clazz) {
    DesktopLyricRenderer* renderer = getRenderer();
    return renderer->getCurrentMusicId();
}

// JNI方法：获取歌词数量
extern "C" JNIEXPORT jint JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeGetLyricCount(JNIEnv* env, jclass clazz) {
    DesktopLyricRenderer* renderer = getRenderer();
    return static_cast<jint>(renderer->getLyricCount());
}

// JNI方法：清理资源
extern "C" JNIEXPORT void JNICALL
Java_com_neko_music_util_DesktopLyricRenderer_nativeCleanup(JNIEnv* env, jclass clazz) {
    if (g_renderer != nullptr) {
        delete g_renderer;
        g_renderer = nullptr;
        LOGI("DesktopLyricRenderer cleaned up");
    }
}